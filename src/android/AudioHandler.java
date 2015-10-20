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

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import android.support.v4.content.LocalBroadcastManager;

import android.os.Bundle;

import java.util.HashMap;

/**
 * This class called by CordovaActivity to play and record audio.
 * The file can be local or over a network using http.
 *
 * Audio formats supported (tested):
 * 	.mp3, .wav
 *
 * Local audio files must reside in one of two places:
 * 		android_asset: 		file name must start with /android_asset/sound.mp3
 * 		sdcard:				file name is just sound.mp3
 */
public class AudioHandler extends CordovaPlugin {

    public static String TAG = "AudioHandler";
    HashMap<String, AudioPlayer> players;	// Audio player object
    ArrayList<AudioPlayer> pausedForPhone;     // Audio players that were paused when phone call came in
    private int origVolumeStream = -1;
    private CallbackContext messageChannel;
    private BroadcastReceiver musicReceiver;

    private class FocusListener implements AudioManager.OnAudioFocusChangeListener {
        AudioManager audioManager;

        public FocusListener (Context ctx) {
            this.audioManager = (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
            int result = this.audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
            }
        }

        @Override
        public void onAudioFocusChange (int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                for (AudioPlayer audio : players.values()) {
                    if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                        audio.pausePlaying();
                    }
                }
            }
            else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                for (AudioPlayer audio : players.values()) {
                    audio.duckVolume();
                }
            }
            else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                for (AudioPlayer audio : players.values()) {
                    audio.unduckVolume();
                }
            }
        }

        public void abandonFocus() {
            if (this.audioManager != null) {
                this.audioManager.abandonAudioFocus(this);
                this.audioManager = null;
            }
        }

        public void onDestroy() {
            abandonFocus();
        }
    }

    private FocusListener focusListener;

    private BroadcastReceiver myReceiver = null;

    /**
     * Constructor.
     */
    public AudioHandler() {
        this.players = new HashMap<String, AudioPlayer>();
        this.pausedForPhone = new ArrayList<AudioPlayer>();
        this.musicReceiver = null;
        this.focusListener = null;
    }

    /**
     * Executes the request and returns PluginResult.
     * @param action 		The action to execute.
     * @param args 			JSONArry of arguments for the plugin.
     * @param callbackContext		The callback context used when calling back into JavaScript.
     * @return 				A PluginResult object with a status and message.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        CordovaResourceApi resourceApi = webView.getResourceApi();
        PluginResult.Status status = PluginResult.Status.OK;
        String result = "";

        if (action.equals("startRecordingAudio")) {
            String target = args.getString(1);
            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }
            this.startRecordingAudio(args.getString(0), FileHelper.stripFileProtocol(fileUriStr));
        }
        else if (action.equals("stopRecordingAudio")) {
            this.stopRecordingAudio(args.getString(0));
        }
        else if (action.equals("startPlayingAudio")) {
            String target = args.getString(1);
            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }
            this.startPlayingAudio(args.getString(0), FileHelper.stripFileProtocol(fileUriStr));
        }
        else if (action.equals("seekToAudio")) {
            this.seekToAudio(args.getString(0), args.getInt(1));
        }
        else if (action.equals("pausePlayingAudio")) {
            this.pausePlayingAudio(args.getString(0));
        }
        else if (action.equals("stopPlayingAudio")) {
            this.stopPlayingAudio(args.getString(0));
        } else if (action.equals("setVolume")) {
           try {
               this.setVolume(args.getString(0), Float.parseFloat(args.getString(1)));
           } catch (NumberFormatException nfe) {
               //no-op
           }
        } else if (action.equals("getCurrentPositionAudio")) {
            float f = this.getCurrentPositionAudio(args.getString(0));
            callbackContext.sendPluginResult(new PluginResult(status, f));
            return true;
        }
        else if (action.equals("getDurationAudio")) {
            float f = this.getDurationAudio(args.getString(0), args.getString(1));
            callbackContext.sendPluginResult(new PluginResult(status, f));
            return true;
        }
        else if (action.equals("create")) {
            String id = args.getString(0);
            //String src = FileHelper.stripFileProtocol(args.getString(1));
            CreateOptions opts = new CreateOptions(cordova.getActivity().getApplicationContext(), args.optJSONObject(1));
            getOrCreatePlayer(id, opts);//src);
        }
        else if (action.equals("release")) {
            boolean b = this.release(args.getString(0));
            callbackContext.sendPluginResult(new PluginResult(status, b));
            return true;
        }
        else if (action.equals("messageChannel")) {
            messageChannel = callbackContext;
            return true;
        }
        else { // Unrecognized action.
            return false;
        }

        callbackContext.sendPluginResult(new PluginResult(status, result));

        return true;
    }

    /**
     * Stop all audio players and recorders.
     */
    public void onDestroy() {
        removeMusicReceiver();

        if (!players.isEmpty()) {
            onLastPlayerReleased();
        }

        for (AudioPlayer audio : this.players.values()) {
            audio.destroy();
        }

        this.players.clear();
    }

    private void removeMusicReceiver() {
        if (this.musicReceiver != null) {
            try {
                this.cordova.getActivity().unregisterReceiver(this.musicReceiver);
                this.musicReceiver = null;
            } catch (Exception e) { }
        }

        if(this.myReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(cordova.getActivity().getApplicationContext()).unregisterReceiver(this.myReceiver);
                this.myReceiver = null;
            } catch(Exception e) {}
        }

        if (this.focusListener != null) {
            this.focusListener.abandonFocus();
            this.focusListener = null;
        }
    }

    /**
     * Stop all audio players and recorders on navigate.
     */
    @Override
    public void onReset() {
        onDestroy();
    }

    /**
     * Called when a message is sent to plugin.
     *
     * @param id            The message id
     * @param data          The message data
     * @return              Object to stop propagation or null
     */
    public Object onMessage(String id, Object data) {

        // If phone message
        if (id.equals("telephone")) {

            // If phone ringing, then pause playing
            if ("ringing".equals(data) || "offhook".equals(data)) {

                // Get all audio players and pause them
                for (AudioPlayer audio : this.players.values()) {
                    if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                        this.pausedForPhone.add(audio);
                        audio.pausePlaying();
                    }
                }

            }

            // If phone idle, then resume playing those players we paused
            else if ("idle".equals(data)) {
                for (AudioPlayer audio : this.pausedForPhone) {
                    audio.startPlaying(null);
                }
                this.pausedForPhone.clear();
            }
        }
        return null;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    private AudioPlayer getOrCreatePlayer(String id, CreateOptions options) {
        AudioPlayer ret = players.get(id);
        if (ret == null) {
            if (players.isEmpty()) {
                onFirstPlayerCreated();
            }

            ret = new AudioPlayer(this, id, options);
            players.put(id, ret);
        }

        return ret;
    }

    /**
     * Release the audio player instance to save memory.
     * @param id				The id of the audio player
     */
    private boolean release(String id) {
        AudioPlayer audio = players.remove(id);
        if (audio == null) {
            return false;
        }
        if (players.isEmpty()) {
            onLastPlayerReleased();
        }
        audio.destroy();
        return true;
    }

    /**
     * Start recording and save the specified file.
     * @param id				The id of the audio player
     * @param file				The name of the file
     */
    public void startRecordingAudio(String id, String file) {
        //AudioPlayer audio = getOrCreatePlayer(id, file);
        //audio.startRecording(file);
    }

    /**
     * Stop recording and save to the file specified when recording started.
     * @param id				The id of the audio player
     */
    public void stopRecordingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.stopRecording();
        }
    }

    /**
     * Start or resume playing audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     */
    public void startPlayingAudio(String id, String file) {
        AudioPlayer audio = getOrCreatePlayer(id, null);
        audio.startPlaying(file);
    }

    /**
     * Seek to a location.
     * @param id				The id of the audio player
     * @param milliseconds		int: number of milliseconds to skip 1000 = 1 second
     */
    public void seekToAudio(String id, int milliseconds) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.seekToPlaying(milliseconds);
        }
    }

    /**
     * Pause playing.
     * @param id				The id of the audio player
     */
    public void pausePlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.pausePlaying();
        }
    }

    /**
     * Stop playing the audio file.
     * @param id				The id of the audio player
     */
    public void stopPlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.stopPlaying();
        }
    }

    /**
     * Get current position of playback.
     * @param id				The id of the audio player
     * @return 					position in msec
     */
    public float getCurrentPositionAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            return (audio.getCurrentPosition() / 1000.0f);
        }
        return -1;
    }

    /**
     * Get the duration of the audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     * @return					The duration in msec.
     */
    public float getDurationAudio(String id, String file) {
        AudioPlayer audio = getOrCreatePlayer(id, null);
        return audio.getDuration(file);
    }

    /**
     * Set the audio device to be used for playback.
     *
     * @param output			1=earpiece, 2=speaker
     */
    @SuppressWarnings("deprecation")
    public void setAudioOutputDevice(int output) {
        AudioManager audiMgr = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (output == 2) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
        }
        else if (output == 1) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
        }
        else {
            System.out.println("AudioHandler.setAudioOutputDevice() Error: Unknown output device.");
        }
    }

    /**
     * Get the audio device to be used for playback.
     *
     * @return					1=earpiece, 2=speaker
     */
    @SuppressWarnings("deprecation")
    public int getAudioOutputDevice() {
        AudioManager audiMgr = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (audiMgr.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_EARPIECE) {
            return 1;
        }
        else if (audiMgr.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_SPEAKER) {
            return 2;
        }
        else {
            return -1;
        }
    }

    /**
     * Set the volume for an audio device
     *
     * @param id				The id of the audio player
     * @param volume            Volume to adjust to 0.0f - 1.0f
     */
    public void setVolume(String id, float volume) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.setVolume(volume);
        } else {
            System.out.println("AudioHandler.setVolume() Error: Unknown Audio Player " + id);
        }
    }

    private void onFirstPlayerCreated() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        if (this.musicReceiver == null) {
            this.musicReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    for (AudioPlayer audio : players.values()) {
                        if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                            audio.pausePlaying();
                        }
                    }
                }
            };

            cordova.getActivity().registerReceiver(this.musicReceiver, intentFilter);
        }

        if(myReceiver == null) {
            Log.d("MediaNotification", "Initiing");
            myReceiver = new BroadcastReceiver() {
                @Override
                 public void onReceive(final Context context, final Intent intent) {
                     final String action = intent.getAction();

                     Bundle bundle  = intent.getExtras();
                     String id = bundle.getString("id");

                     AudioPlayer audio = players.get(id);
                     if(audio == null) {
                         return;
                     }

                     Log.d("MediaNotification", "Received intent with action " + action + " - for id: " + id);

                     if((MediaNotification.ACTION_PAUSE + ".real").equals(action)) {
                         if(audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                             audio.pausePlaying();
                         }
                         else {
                             audio.startPlaying(audio.getOptions().getSourcePath());
                         }
                     }
                     else if((MediaNotification.ACTION_REWIND + ".real").equals(action)) {
                         int pos = (int)(audio.getCurrentPosition() - 10000);

                         audio.seekToPlaying(pos);
                     }
                     else if((MediaNotification.ACTION_FORWARD + ".real").equals(action)) {
                         int pos = (int)(audio.getCurrentPosition() + 30000);
                         audio.seekToPlaying(pos);
                     }
                 }
            };

            IntentFilter intentFilter2 = new IntentFilter();
            intentFilter2.addAction(MediaNotification.ACTION_PLAY + ".real");
            intentFilter2.addAction(MediaNotification.ACTION_PAUSE + ".real");
            intentFilter2.addAction(MediaNotification.ACTION_REWIND + ".real");
            intentFilter2.addAction(MediaNotification.ACTION_FORWARD + ".real");
            //cordova.getActivity().registerReceiver(this.myReceiver, intentFilter2);
            LocalBroadcastManager.getInstance(cordova.getActivity().getApplicationContext()).registerReceiver(this.myReceiver, intentFilter2);
        }

        if (this.focusListener == null) {
            /*AudioManager audioManager = (AudioManager)cordova.getActivity().getBaseContext().getSystemService(Context.AUDIO_SERVICE);
            this.focusListener = new OnAudioFocusChangeListener() {
                {
                    int result = audioManager.requestAudioFocus(this,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);

                    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
                        //sendFocusEvent(AudioManager.AUDIOFOCUS_LOSS);
                    }
                }

                public void onAudioFocusChange (int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        for (AudioPlayer audio : players.values()) {
                            if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                                audio.pausePlaying();
                            }
                        }
                    }
                }

                public void abandonFocus() {
                    if (audioManager != null) {
                        audioManager.abandonAudioFocus(this);
                        audioManager = null;
                    }
                }

                public void onDestroy() {
                    abandonFocus();
                }
            };*/

            /*private class FocusListener implements AudioManager.OnAudioFocusChangeListener {
                AudioManager audioManager;

                public FocusListener (Context ctx) {
                    this.audioManager = (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
                    int result = this.audioManager.requestAudioFocus(this,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);

                    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        sendFocusEvent(AudioManager.AUDIOFOCUS_LOSS);
                    }
                }

                @Override
                public void onAudioFocusChange (int focusChange) {
                    sendFocusEvent(focusChange);
                }

                public void abandonFocus() {
                    if (this.audioManager != null) {
                        this.audioManager.abandonAudioFocus(this);
                        this.audioManager = null;
                    }
                }

                public void onDestroy() {
                    abandonFocus();
                }
            }*/

            Context ctx = cordova.getActivity().getBaseContext();
            this.focusListener = this.new FocusListener(ctx);
        }

        origVolumeStream = cordova.getActivity().getVolumeControlStream();
        cordova.getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void onLastPlayerReleased() {
        removeMusicReceiver();

        if (origVolumeStream != -1) {
            cordova.getActivity().setVolumeControlStream(origVolumeStream);
            origVolumeStream = -1;
        }
    }

    void sendEventMessage(String action, JSONObject actionData) {
        JSONObject message = new JSONObject();
        try {
            message.put("action", action);
            if (actionData != null) {
                message.put(action, actionData);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create event message", e);
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, message);
        pluginResult.setKeepCallback(true);
        if (messageChannel != null) {
            messageChannel.sendPluginResult(pluginResult);
        }
    }
}
