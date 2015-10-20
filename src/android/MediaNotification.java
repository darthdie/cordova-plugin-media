/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package org.apache.cordova.media;

import android.app.PendingIntent;
import android.media.session.MediaSession;
import android.media.MediaMetadata;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;

import android.app.Notification;
import android.app.NotificationManager;
import android.media.session.PlaybackState;

import android.view.KeyEvent;

import android.content.BroadcastReceiver;

import android.util.Log;

import java.util.Random;

import android.support.v4.content.LocalBroadcastManager;

public class MediaNotification extends BroadcastReceiver {
    private static final String TAG = "MediaNotification";

    public static final String ACTION_TOGGLE_PLAYBACK = "org.apache.cordova.media.TOGGLE_PLAYBACK";
    public static final String ACTION_PLAY = "org.apache.cordova.media.PLAY";
    public static final String ACTION_PAUSE = "org.apache.cordova.media.PAUSE";
    public static final String ACTION_REWIND = "org.apache.cordova.media.REWIND";
    public static final String ACTION_FORWARD = "org.apache.cordova.media.FORWARD";

    final private AudioPlayer player;
    private Context context;
    private NotificationManager mNotificationManager;
    private MediaSession mSession;
    private int mState;
    final private CreateOptions options;
    private Notification.Builder mNotificationBuilder;
    private Notification.Action mPlayPauseAction;

    public MediaNotification() {
        this.player = null;
        this.options = null;
    }

    public MediaNotification(AudioPlayer player) {
        this.player = player;
        this.options = this.player.getOptions();
        this.context = this.options.getContext();
        this.mNotificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void show() {
        Log.d(TAG, "Showing notification");
        Bitmap artwork = options.getIconBitmap();

        this.mSession = new MediaSession(this.context, "Satchel Media Session");
        this.mSession.setMetadata(new MediaMetadata.Builder()
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, this.options.getPodcastName())
            .putString(MediaMetadata.METADATA_KEY_TITLE, this.options.getEpisodeName())
            .build());

        this.mSession.setActive(true);
        this.mSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);

        // Attach a new Callback to receive MediaSession updates
        this.mSession.setCallback(new MediaSession.Callback() {
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                if(mSession == null || !Intent.ACTION_MEDIA_BUTTON.equals(mediaButtonIntent.getAction())) {
                    return false;
                }

                KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (ke == null || ke.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }

                //PlaybackState state = mPlaybackState;
                //long validActions = state == null ? 0 : state.getActions();

                if(ke.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    player.startPlaying(options.getSourcePath());
                    return true;
                }

                if(ke.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    player.pausePlaying();
                    return true;
                }

                return false;
            }
        });

        /*PlaybackState state = new PlaybackState.Builder()
        .setActions(
                PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE |
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        .setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0, SystemClock.elapsedRealtime())
        .build();

        this.mSession.setPlaybackState(state);*/

        this.mState = PlaybackState.STATE_BUFFERING;
        this.updateState();

        this.updatePlayPauseAction();

        mNotificationBuilder = new Notification.Builder(this.context)
             .setShowWhen(false)
             .setStyle(new Notification.MediaStyle()
                 .setMediaSession(this.mSession.getSessionToken())
                 .setShowActionsInCompactView(0, 1, 2))
             .setColor(Color.parseColor("#303030"))
             .setSmallIcon(this.options.getSmallIcon())
             .setLargeIcon(artwork)
             .setVisibility(Notification.VISIBILITY_PUBLIC)
             .setContentText(this.options.getEpisodeName())
             .setContentTitle(this.options.getPodcastName())
             .setProgress(100, 33, false)
             .setOngoing(false)
             .addAction(this.options.getMediaBackIcon(), "prev", retrievePlaybackAction(ACTION_REWIND))
             //.addAction(android.R.drawable.ic_media_play, "play", retrievePlaybackAction(ACTION_PLAY))
             .addAction(mPlayPauseAction)
             .addAction(this.options.getMediaForwardIcon(), "next", retrievePlaybackAction(ACTION_FORWARD));

         mNotificationManager.notify(1, mNotificationBuilder.build());
    }

    private void updatePlayPauseAction() {
        Log.d(TAG, "updatePlayPauseAction");
        String playPauseLabel = "";
        int playPauseIcon;
        if (this.mState == PlaybackState.STATE_PLAYING) {
            playPauseLabel = "Pause";
            playPauseIcon = this.options.getMediaPauseIcon();
        } else {
            playPauseLabel = "Play";
            playPauseIcon = this.options.getMediaPlayIcon();
        }

        if (mPlayPauseAction == null) {
            mPlayPauseAction = new Notification.Action(playPauseIcon, playPauseLabel, retrievePlaybackAction(ACTION_PAUSE));
        } else {
            mPlayPauseAction.icon = playPauseIcon;
            mPlayPauseAction.title = playPauseLabel;
            mPlayPauseAction.actionIntent = retrievePlaybackAction(ACTION_PAUSE);
        }
    }

    private PendingIntent retrievePlaybackAction(String action) {
        Intent intent = new Intent(this.context, MediaNotification.class);
        intent.setAction(action);
        intent.putExtra("id", this.player.getId());

        int requestCode = new Random().nextInt();
        return PendingIntent.getBroadcast(this.context, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    public void updateState() {
        if(mNotificationBuilder == null) {
            return;
        }

        AudioPlayer.STATE state = AudioPlayer.STATE.values()[this.player.getState()];
        Log.d(TAG, "Updating state to: " + state);

        if(state == AudioPlayer.STATE.MEDIA_STARTING) {
            this.mState = PlaybackState.STATE_PAUSED;
        }
        else if(state == AudioPlayer.STATE.MEDIA_RUNNING) {
            this.mState = PlaybackState.STATE_PLAYING;
        }
        else if(state == AudioPlayer.STATE.MEDIA_STOPPED) {
            this.mState = PlaybackState.STATE_STOPPED;
        }
        else if(state == AudioPlayer.STATE.MEDIA_PAUSED) {
            this.mState = PlaybackState.STATE_PAUSED;
        }

        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;

        if (mState == PlaybackState.STATE_PLAYING) {
            position = this.player.getCurrentPosition();
            mNotificationBuilder.setOngoing(true);
        }
        else {
            mNotificationBuilder.setOngoing(false);
        }

        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());
        stateBuilder.setState(mState, position, 1.0f);

        this.mSession.setPlaybackState(stateBuilder.build());

        updatePlayPauseAction();

        mNotificationManager.notify(1, mNotificationBuilder.build());
    }

    private long getAvailableActions() {
        long actions = PlaybackState.ACTION_PLAY |
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackState.ACTION_PLAY_FROM_SEARCH |
                PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                PlaybackState.ACTION_SKIP_TO_NEXT;

        if (mState == PlaybackState.STATE_PLAYING) {
            actions |= PlaybackState.ACTION_PAUSE;
        }

        return actions;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        Log.d("MediaNotification", "Received intent " + action);

        if (ACTION_PAUSE.equals(action) || ACTION_FORWARD.equals(action) || ACTION_REWIND.equals(action)) {
            intent.setAction(intent.getAction() + ".real");
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    public void stop() {
        this.mSession.release();
        this.mSession = null;
    }
}
