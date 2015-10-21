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

package org.apache.cordova.media.widget;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.widget.RemoteViews;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.media.AudioHandler;

import android.util.Log;

public class MediaWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d("MediaNotification", "On update - widget");
        RemoteViews updateViews = buildUpdate(context);

        // Push update for this widget to the home screen
        ComponentName thisWidget = new ComponentName(context, MediaWidget.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);

        manager.updateAppWidget(thisWidget, updateViews);
    }

    public RemoteViews buildUpdate(Context context) {
        int layoutId = context.getResources().getIdentifier("media_widget_layout", "layout", context.getPackageName());
        RemoteViews updateViews = new RemoteViews(context.getPackageName(), layoutId);

        int movieNameId = context.getResources().getIdentifier("movie_name", "id", context.getPackageName());
        updateViews.setTextViewText(movieNameId, "HAI");

        return updateViews;
    }
}
