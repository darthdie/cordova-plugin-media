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

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaRecorder;
import android.media.session.PlaybackState;
import android.os.Environment;
import android.util.Log;

import android.app.Notification;
import android.app.NotificationManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import android.app.PendingIntent;
import android.media.session.MediaSession;
import android.media.MediaMetadata;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;

import android.view.KeyEvent;

/**
 * This class implements the audio playback and recording capabilities used by Cordova.
 * It is called by the AudioHandler Cordova class.
 * Only one file can be played or recorded per class instance.
 *
 * Local audio files must reside in one of two places:
 *      android_asset:      file name must start with /android_asset/sound.mp3
 *      sdcard:             file name is just sound.mp3
 */
public class AudioPlayer implements OnCompletionListener, OnPreparedListener, OnErrorListener {

    // AudioPlayer modes
    public enum MODE { NONE, PLAY, RECORD };

    // AudioPlayer states
    public enum STATE { MEDIA_NONE,
                        MEDIA_STARTING,
                        MEDIA_RUNNING,
                        MEDIA_PAUSED,
                        MEDIA_STOPPED,
                        MEDIA_LOADING
                      };

    private static final String LOG_TAG = "AudioPlayer";

    private static final String ACTION_TOGGLE_PLAYBACK = "org.apache.cordova.media.TOGGLE_PLAYBACK";
    private static final String ACTION_PLAY = "org.apache.cordova.media.PLAY";
    private static final String ACTION_PAUSE = "org.apache.cordova.media.PAUSE";
    private static final String ACTION_REWIND = "org.apache.cordova.media.REWIND";
    private static final String ACTION_FORWARD = "org.apache.cordova.media.FORWARD";

    // AudioPlayer message ids
    private static int MEDIA_STATE = 1;
    private static int MEDIA_DURATION = 2;
    private static int MEDIA_POSITION = 3;
    private static int MEDIA_ERROR = 9;

    // Media error codes
    private static int MEDIA_ERR_NONE_ACTIVE    = 0;
    private static int MEDIA_ERR_ABORTED        = 1;
//    private static int MEDIA_ERR_NETWORK        = 2;
//    private static int MEDIA_ERR_DECODE         = 3;
//    private static int MEDIA_ERR_NONE_SUPPORTED = 4;

    private AudioHandler handler;           // The AudioHandler object
    private String id;                      // The id of this player (used to identify Media object in JavaScript)
    private MODE mode = MODE.NONE;          // Playback or Recording mode
    private STATE state = STATE.MEDIA_NONE; // State of recording or playback

    private String audioFile = null;        // File name to play or record to
    private float duration = -1;            // Duration of audio

    private MediaRecorder recorder = null;  // Audio recording object
    private String tempFile = null;         // Temporary recording file name

    private MediaPlayer player = null;      // Audio player object
    private Notification notification = null;
    private MediaSession mSession  = null;

    private boolean prepareOnly = true;     // playback after file prepare flag
    private int seekOnPrepared = 0;     // seek to this location once media is prepared

    private float volume;
    private float origVolume;

    private CreateOptions options;
    private int mState = PlaybackState.STATE_NONE;
    //private PlaybackState mPlaybackState;

    public class MediaControlReceiver extends BroadcastReceiver {
        /*public static final String ACTION_PLAY = "de.appplant.action_play";
        public static final String ACTION_PAUSE = "de.appplant.action_pause";
        public static final String ACTION_REWIND = "de.appplant.action_rewind";
        public static final String ACTION_FAST_FORWARD = "de.appplant.action_fast_forward";
        public static final String ACTION_NEXT = "de.appplant.action_next";
        public static final String ACTION_PREVIOUS = "de.appplant.action_previous";
        public static final String ACTION_STOP = "de.appplant.action_stop";*/

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle  = intent.getExtras();
            Options options;

            try {
                String data = bundle.getString("NOTIFICATION_OPTIONS");
                JSONObject dict = new JSONObject(data);

                options = new Options(context).parse(dict);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            if (options == null) {
                return;
            }

            Builder builder = new Builder(options);
            Notification notification = buildNotification(builder);

            final String action = intent.getAction();

            if(action.equals(ACTION_TOGGLE_PLAY)) {
                pausePlaying();
                //LocalNotification.fireEvent("mediapause", notification);
            }
            else if(action.equals(ACTION_PLAY)) {
                startPlaying(audioFile);
                //LocalNotification.fireEvent("mediaplay", notification);
            }
            else if(action.equals(ACTION_REWIND)) {
                //LocalNotification.fireEvent("mediarewind", notification);
            }
            else if(action.equals(ACTION_FAST_FORWARD)) {
                //LocalNotification.fireEvent("mediafastforward", notification);
            }
        }

        public Notification buildNotification (Builder builder) {
            return builder
                    .setTriggerReceiver(TriggerReceiver.class)
                    .setClickActivity(ClickActivity.class)
                    .setClearReceiver(ClearReceiver.class)
                    .build();
        }
    }

    /**
     * Constructor.
     *
     * @param handler           The audio handler object
     * @param id                The id of this audio player
     */
    public AudioPlayer(AudioHandler handler, String id, CreateOptions options) {
        this.handler = handler;
        this.id = id;
        this.options = options;
        this.audioFile = options.getSourcePath();
        this.recorder = new MediaRecorder();
        this.volume = 1.0f;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            this.tempFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/tmprecording.3gp";
        } else {
            this.tempFile = "/data/data/" + handler.cordova.getActivity().getPackageName() + "/cache/tmprecording.3gp";
        }

    }

    /**
     * Destroy player and stop audio playing or recording.
     */
    public void destroy() {
        // Stop any play or record
        if (this.player != null) {
            if ((this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) {
                this.player.stop();
                this.setState(STATE.MEDIA_STOPPED);
            }

            this.player.release();
            this.player = null;

            this.mSession.release();
            this.mSession = null;
        }
        if (this.recorder != null) {
            this.stopRecording();
            this.recorder.release();
            this.recorder = null;
        }
    }

    /**
     * Start recording the specified file.
     *
     * @param file              The name of the file
     */
    public void startRecording(String file) {
        switch (this.mode) {
        case PLAY:
            Log.d(LOG_TAG, "AudioPlayer Error: Can't record in play mode.");
            sendErrorStatus(MEDIA_ERR_ABORTED);
            break;
        case NONE:
            this.audioFile = file;
            this.recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            this.recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT); // THREE_GPP);
            this.recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT); //AMR_NB);
            this.recorder.setOutputFile(this.tempFile);
            try {
                this.recorder.prepare();
                this.recorder.start();
                this.setState(STATE.MEDIA_RUNNING);
                return;
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            sendErrorStatus(MEDIA_ERR_ABORTED);
            break;
        case RECORD:
            Log.d(LOG_TAG, "AudioPlayer Error: Already recording.");
            sendErrorStatus(MEDIA_ERR_ABORTED);
        }
    }

    /**
     * Save temporary recorded file to specified name
     *
     * @param file
     */
    public void moveFile(String file) {
        /* this is a hack to save the file as the specified name */
        File f = new File(this.tempFile);

        if (!file.startsWith("/")) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                file = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + file;
            } else {
                file = "/data/data/" + handler.cordova.getActivity().getPackageName() + "/cache/" + file;
            }
        }

        String logMsg = "renaming " + this.tempFile + " to " + file;
        Log.d(LOG_TAG, logMsg);
        if (!f.renameTo(new File(file))) Log.e(LOG_TAG, "FAILED " + logMsg);
    }

    /**
     * Stop recording and save to the file specified when recording started.
     */
    public void stopRecording() {
        if (this.recorder != null) {
            try{
                if (this.state == STATE.MEDIA_RUNNING) {
                    this.recorder.stop();
                    this.setState(STATE.MEDIA_STOPPED);
                }
                this.recorder.reset();
                this.moveFile(this.audioFile);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updatePlaybackState() {
        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;

        if (this.player != null && this.player.isPlaying()) {
            position = this.player.getCurrentPosition();
        }

        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());
        stateBuilder.setState(mState, position, 1.0f);

        //this.mPlaybackState = stateBuilder.build();
        this.mSession.setPlaybackState(stateBuilder.build());
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

    //==========================================================================
    // Playback
    //==========================================================================

    /**
     * Start or resume playing audio file.
     *
     * @param file              The name of the audio file.
     */
    public void startPlaying(String file) {
        if (this.readyPlayer(file) && this.player != null) {
            this.player.start();
            this.setState(STATE.MEDIA_RUNNING);
            this.seekOnPrepared = 0; //insures this is always reset

            this.mState = PlaybackState.STATE_PLAYING;
            this.updatePlaybackState();
        } else {
            this.prepareOnly = false;
        }
    }

    /**
     * Seek or jump to a new time in the track.
     */
    public void seekToPlaying(int milliseconds) {
        if (this.readyPlayer(this.audioFile)) {
            this.player.seekTo(milliseconds);
            Log.d(LOG_TAG, "Send a onStatus update for the new seek");
            sendStatusChange(MEDIA_POSITION, null, (milliseconds / 1000.0f));
        }
        else {
            this.seekOnPrepared = milliseconds;
        }
    }

    /**
     * Pause playing.
     */
    public void pausePlaying() {
        // If playing, then pause
        if (this.state == STATE.MEDIA_RUNNING && this.player != null) {
            this.player.pause();
            this.setState(STATE.MEDIA_PAUSED);

            this.mState = PlaybackState.STATE_PAUSED;
            this.updatePlaybackState();
        }
        else {
            Log.d(LOG_TAG, "AudioPlayer Error: pausePlaying() called during invalid state: " + this.state.ordinal());
            sendErrorStatus(MEDIA_ERR_NONE_ACTIVE);
        }
    }

    /**
     * Stop playing the audio file.
     */
    public void stopPlaying() {
        if ((this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) {
            this.player.pause();
            this.player.seekTo(0);
            Log.d(LOG_TAG, "stopPlaying is calling stopped");
            this.setState(STATE.MEDIA_STOPPED);

            this.mState = PlaybackState.STATE_STOPPED;
            this.updatePlaybackState();
        }
        else {
            Log.d(LOG_TAG, "AudioPlayer Error: stopPlaying() called during invalid state: " + this.state.ordinal());
            sendErrorStatus(MEDIA_ERR_NONE_ACTIVE);
        }
    }

    /**
     * Callback to be invoked when playback of a media source has completed.
     *
     * @param player           The MediaPlayer that reached the end of the file
     */
    public void onCompletion(MediaPlayer player) {
        Log.d(LOG_TAG, "on completion is calling stopped");
        this.setState(STATE.MEDIA_STOPPED);

        this.mState = PlaybackState.STATE_STOPPED;
        this.updatePlaybackState();
    }

    /**
     * Get current position of playback.
     *
     * @return                  position in msec or -1 if not playing
     */
    public long getCurrentPosition() {
        if ((this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) {
            int curPos = this.player.getCurrentPosition();
            sendStatusChange(MEDIA_POSITION, null, (curPos / 1000.0f));
            return curPos;
        }
        else {
            return -1;
        }
    }

    /**
     * Determine if playback file is streaming or local.
     * It is streaming if file name starts with "http://"
     *
     * @param file              The file name
     * @return                  T=streaming, F=local
     */
    public boolean isStreaming(String file) {
        if (file.contains("http://") || file.contains("https://")) {
            return true;
        }
        else {
            return false;
        }
    }

    public void duckVolume() {
        this.origVolume = this.volume;

        this.setVolume(Math.max(0, Math.min(1, this.volume * 0.5f)));
    }

    public void unduckVolume() {
        if(this.origVolume > -1) {
            this.setVolume(this.origVolume);
            this.origVolume = -1;
        }
    }

    /**
      * Get the duration of the audio file.
      *
      * @param file             The name of the audio file.
      * @return                 The duration in msec.
      *                             -1=can't be determined
      *                             -2=not allowed
      */
    public float getDuration(String file) {

        // Can't get duration of recording
        if (this.recorder != null) {
            return (-2); // not allowed
        }

        // If audio file already loaded and started, then return duration
        if (this.player != null) {
            return this.duration;
        }

        // If no player yet, then create one
        else {
            this.prepareOnly = true;
            this.startPlaying(file);

            // This will only return value for local, since streaming
            // file hasn't been read yet.
            return this.duration;
        }
    }

    /**
     * Callback to be invoked when the media source is ready for playback.
     *
     * @param player           The MediaPlayer that is ready for playback
     */
    public void onPrepared(MediaPlayer player) {
        // Listen for playback completion
        this.player.setOnCompletionListener(this);
        // seek to any location received while not prepared
        this.seekToPlaying(this.seekOnPrepared);
        // If start playing after prepared
        if (!this.prepareOnly) {
            this.player.start();
            this.setState(STATE.MEDIA_RUNNING);
            this.seekOnPrepared = 0; //reset only when played

            this.mState = PlaybackState.STATE_PLAYING;
        } else {
            this.setState(STATE.MEDIA_STARTING);
            this.mState = PlaybackState.STATE_PAUSED;
        }

        this.updatePlaybackState();

        // Save off duration
        this.duration = getDurationInSeconds();
        // reset prepare only flag
        this.prepareOnly = true;

        // Send status notification to JavaScript
        sendStatusChange(MEDIA_DURATION, null, this.duration);
    }

    /**
     * By default Android returns the length of audio in mills but we want seconds
     *
     * @return length of clip in seconds
     */
    private float getDurationInSeconds() {
        return (this.player.getDuration() / 1000.0f);
    }

    /**
     * Callback to be invoked when there has been an error during an asynchronous operation
     *  (other errors will throw exceptions at method call time).
     *
     * @param player           the MediaPlayer the error pertains to
     * @param arg1              the type of error that has occurred: (MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_SERVER_DIED)
     * @param arg2              an extra code, specific to the error.
     */
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        Log.d(LOG_TAG, "AudioPlayer.onError(" + arg1 + ", " + arg2 + ")");

        // TODO: Not sure if this needs to be sent?
        this.player.stop();
        this.player.release();
        this.mSession.release();

        // Send error notification to JavaScript
        sendErrorStatus(arg1);
        return false;
    }

    /**
     * Set the state and send it to JavaScript.
     *
     * @param state
     */
    private void setState(STATE state) {
        if (this.state != state) {
            sendStatusChange(MEDIA_STATE, null, (float)state.ordinal());
        }
        this.state = state;
    }

    /**
     * Set the mode and send it to JavaScript.
     *
     * @param mode
     */
    private void setMode(MODE mode) {
        if (this.mode != mode) {
            //mode is not part of the expected behavior, so no notification
            //this.handler.webView.sendJavascript("cordova.require('cordova-plugin-media.Media').onStatus('" + this.id + "', " + MEDIA_STATE + ", " + mode + ");");
        }
        this.mode = mode;
    }

    /**
     * Get the audio state.
     *
     * @return int
     */
    public int getState() {
        return this.state.ordinal();
    }

    /**
     * Set the volume for audio player
     *
     * @param volume
     */
    public void setVolume(float volume) {
        this.volume = volume;
        this.player.setVolume(volume, volume);
    }

    /**
     * attempts to put the player in play mode
     * @return true if in playmode, false otherwise
     */
    private boolean playMode() {
        switch(this.mode) {
        case NONE:
            this.setMode(MODE.PLAY);
            break;
        case PLAY:
            break;
        case RECORD:
            Log.d(LOG_TAG, "AudioPlayer Error: Can't play in record mode.");
            sendErrorStatus(MEDIA_ERR_ABORTED);
            return false; //player is not ready
        }
        return true;
    }

    /**
     * attempts to initialize the media player for playback
     * @param file the file to play
     * @return false if player not ready, reports if in wrong mode or state
     */
    private boolean readyPlayer(String file) {
        if (playMode()) {
            switch (this.state) {
                case MEDIA_NONE:
                    if (this.player == null) {
                        this.player = new MediaPlayer();

                        c

                        Bitmap artwork = this.options.getIconBitmap();

                        this.mSession = new MediaSession(ctx, "Satchel Media Session");
                        this.mSession.setMetadata(new MediaMetadata.Builder()
                            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, this.options.getPodcastName())
                            //.putString(MediaMetadata.METADATA_KEY_ALBUM, "Dark Side of the Moon")
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
                                    startPlaying(audioFile);
                                    return true;
                                }

                                if(ke.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                                    pausePlaying();
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
                        this.updatePlaybackState();

                        this.notification = new Notification.Builder(ctx)
                             .setShowWhen(false)
                             .setStyle(new Notification.MediaStyle()
                                 .setMediaSession(this.mSession.getSessionToken())
                                 .setShowActionsInCompactView(0, 1, 2))
                             .setColor(Color.parseColor("#303030"))
                             .setSmallIcon(this.options.getSmallIcon())
                             .setLargeIcon(artwork)
                             .setContentText(this.options.getEpisodeName())
                             .setContentTitle(this.options.getPodcastName())
                             .addAction(android.R.drawable.ic_media_rew, "prev", retreivePlaybackAction(4))
                             .addAction(android.R.drawable.ic_media_play, "play", retreivePlaybackAction(1))
                             .addAction(android.R.drawable.ic_media_ff, "next", retreivePlaybackAction(3))
                             .build();

                         ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, this.notification);
                    }
                    try {
                        this.loadAudioFile(file);
                    } catch (Exception e) {
                        sendErrorStatus(MEDIA_ERR_ABORTED);
                    }
                    return false;
                case MEDIA_LOADING:
                    //cordova js is not aware of MEDIA_LOADING, so we send MEDIA_STARTING instead
                    Log.d(LOG_TAG, "AudioPlayer Loading: startPlaying() called during media preparation: " + STATE.MEDIA_STARTING.ordinal());
                    this.prepareOnly = false;
                    return false;
                case MEDIA_STARTING:
                case MEDIA_RUNNING:
                case MEDIA_PAUSED:
                    return true;
                case MEDIA_STOPPED:
                    //if we are readying the same file
                    if (this.audioFile.compareTo(file) == 0) {
                        //reset the audio file
                        player.seekTo(0);
                        player.pause();
                        return true;
                    } else {
                        //reset the player
                        this.player.reset();
                        try {
                            this.loadAudioFile(file);
                        } catch (Exception e) {
                            sendErrorStatus(MEDIA_ERR_ABORTED);
                        }
                        //if we had to prepare the file, we won't be in the correct state for playback
                        return false;
                    }
                default:
                    Log.d(LOG_TAG, "AudioPlayer Error: startPlaying() called during invalid state: " + this.state);
                    sendErrorStatus(MEDIA_ERR_ABORTED);
            }
        }
        return false;
    }

    private PendingIntent retreivePlaybackAction(int which) {
        Intent action;
        PendingIntent pendingIntent;
        PendingIntent.getService
        //final ComponentName serviceName = new ComponentName(this, MediaControlReceiver.class);
        switch (which) {
            case 1:
                // Play
                action = new Intent(ACTION_PLAY);
                //action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(ctx, 1, action, 0);
                return pendingIntent;
            case 2:
                //Pause
                action = new Intent(ACTION_PAUSE);
                //action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(ctx, 1, action, 0);
                return pendingIntent;
            case 3:
                // Skip tracks
                action = new Intent(ACTION_NEXT);
                //action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(ctx, 3, action, 0);
                return pendingIntent;
            case 4:
                // Previous tracks
                action = new Intent(ACTION_PREV);
                //action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(ctx, 4, action, 0);
                return pendingIntent;
            default:
                break;
        }
        return null;
    }

    /**
     * load audio file
     * @throws IOException
     * @throws IllegalStateException
     * @throws SecurityException
     * @throws IllegalArgumentException
     */
    private void loadAudioFile(String file) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
        if (this.isStreaming(file)) {
            this.player.setDataSource(file);
            this.player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            //if it's a streaming file, play mode is implied
            this.setMode(MODE.PLAY);
            this.setState(STATE.MEDIA_STARTING);
            this.player.setOnPreparedListener(this);
            this.player.prepareAsync();
        }
        else {
            if (file.startsWith("/android_asset/")) {
                String f = file.substring(15);
                android.content.res.AssetFileDescriptor fd = this.handler.cordova.getActivity().getAssets().openFd(f);
                this.player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            }
            else {
                File fp = new File(file);
                if (fp.exists()) {
                    FileInputStream fileInputStream = new FileInputStream(file);
                    this.player.setDataSource(fileInputStream.getFD());
                    fileInputStream.close();
                }
                else {
                    this.player.setDataSource(Environment.getExternalStorageDirectory().getPath() + "/" + file);
                }
            }
                this.setState(STATE.MEDIA_STARTING);
                this.player.setOnPreparedListener(this);
                this.player.prepare();

                // Get duration
                this.duration = getDurationInSeconds();
            }
    }

    private void sendErrorStatus(int errorCode) {
        sendStatusChange(MEDIA_ERROR, errorCode, null);
    }

    private void sendStatusChange(int messageType, Integer additionalCode, Float value) {

        if (additionalCode != null && value != null) {
            throw new IllegalArgumentException("Only one of additionalCode or value can be specified, not both");
        }

        JSONObject statusDetails = new JSONObject();
        try {
            statusDetails.put("id", this.id);
            statusDetails.put("msgType", messageType);
            if (additionalCode != null) {
                JSONObject code = new JSONObject();
                code.put("code", additionalCode.intValue());
                statusDetails.put("value", code);
            }
            else if (value != null) {
                statusDetails.put("value", value.floatValue());
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to create status details", e);
        }

        this.handler.sendEventMessage("status", statusDetails);
    }
}
