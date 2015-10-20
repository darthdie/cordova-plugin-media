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
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.widget.RemoteViews;

public class MediaWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // To prevent any ANR timeouts, we perform the update in a service

        context.startService(new Intent(context, UpdateService.class));
    }

    public static class UpdateService extends Service {
        @Override
        public void onStart(Intent intent, int startId) {
            // Build the widget update for today
            RemoteViews updateViews = buildUpdate(this);

            // Push update for this widget to the home screen
            ComponentName thisWidget = new ComponentName(this, MovieSearchWidget.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);

            manager.updateAppWidget(thisWidget, updateViews);
        }

        public RemoteViews buildUpdate(Context context) {
            /*Movie movie = movieSeeker.findLatest();

            String imdbUrl = IMDB_BASE_URL + movie.imdbId;

            // Build an update that holds the updated widget contents
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            updateViews.setTextViewText(R.id.app_name, getString(R.string.app_name));

            updateViews.setTextViewText(R.id.movie_name, movie.name);

            Intent intent = new Intent();

            intent.setAction("android.intent.action.VIEW");

            intent.addCategory("android.intent.category.BROWSABLE");

            intent.setData(Uri.parse(imdbUrl));

            PendingIntent pendingIntent =

            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            updateViews.setOnClickPendingIntent(R.id.movie_name, pendingIntent);*/

            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            updateViews.setTextViewText(R.id.app_name, getString(R.string.app_name));

            updateViews.setTextViewText(R.id.movie_name, "HAI");

            return updateViews;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;// We don't need to bind to this service
        }
    }
}
