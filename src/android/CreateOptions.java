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

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

public class CreateOptions {
    private JSONObject options = new JSONObject();
    private final Context context;
    private final AssetUtil assets;

    public CreateOptions (Context context, JSONObject options) {
        this.context = context;
        this.assets  = AssetUtil.getInstance(context);
        this.options = options;

        parseAssets();
    }

    private void parseAssets() {
        if (options.has("iconUri")) {
            return;
        }

        Uri iconUri = assets.parse(options.optString("icon", "icon"));

        try {
            options.put("iconUri", iconUri.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public Context getContext() {
        return context;
    }

    public String getSourcePath() {
        return options.optString("source", "");
    }

    public String getPodcastName() {
        return options.optString("podcastName", "");
    }

    public String getEpisodeName() {
        return options.optString("episodeName", "");
    }

    public Bitmap getIconBitmap() {
        String icon = options.optString("icon", "icon");
        Bitmap bmp;

        try{
            Uri uri = Uri.parse(options.optString("iconUri"));
            bmp = assets.getIconFromUri(uri);
        } catch (Exception e){
            bmp = assets.getIconFromDrawable(icon);
        }

        return bmp;
    }

    public int getSmallIcon () {
        String icon = options.optString("smallIcon", "");

        int resId = assets.getResIdForDrawable(icon);

        if (resId == 0) {
            resId = android.R.drawable.screen_background_dark;
        }

        return resId;
    }
}
