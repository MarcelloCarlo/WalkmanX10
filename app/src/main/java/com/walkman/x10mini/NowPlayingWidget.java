package com.walkman.x10mini;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.widget.RemoteViews;
import java.io.File;
import java.io.InputStream;

public class NowPlayingWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int i = 0; i < ids.length; i++) {
            mgr.updateAppWidget(ids[i], buildViews(context, null, null, false));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (MusicService.WIDGET_UPDATE.equals(action)) {
            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");
            long albumId = intent.getLongExtra("albumId", -1);
            boolean isPlaying = intent.getBooleanExtra("isPlaying", false);

            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName widget = new ComponentName(context, NowPlayingWidget.class);
            int[] ids = mgr.getAppWidgetIds(widget);
            if (ids == null || ids.length == 0) return;

            String jellyfinArtId = intent.getStringExtra("jellyfinArtId");
            RemoteViews rv = buildViews(context, title, artist, isPlaying);

            Bitmap art = null;
            if (albumId < 0 && jellyfinArtId != null) {
                art = loadJellyfinArt(jellyfinArtId);
            } else {
                art = loadArt(context, albumId);
            }
            if (art != null) {
                rv.setImageViewBitmap(R.id.widget_album_art, art);
            } else {
                rv.setImageViewResource(R.id.widget_album_art, R.drawable.musicplayer_default_album);
            }

            mgr.updateAppWidget(ids, rv);
        } else if (MusicService.WIDGET_PLAY_PAUSE.equals(action)) {
            context.startService(
                    new Intent(context, MusicService.class).setAction(MusicService.ACTION_TOGGLE));
        } else if (MusicService.WIDGET_NEXT.equals(action)) {
            context.startService(
                    new Intent(context, MusicService.class).setAction(MusicService.ACTION_NEXT));
        } else if (MusicService.WIDGET_PREV.equals(action)) {
            context.startService(
                    new Intent(context, MusicService.class).setAction(MusicService.ACTION_PREV));
        }
    }

    private RemoteViews buildViews(Context context, String title, String artist, boolean isPlaying) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_now_playing);

        rv.setTextViewText(R.id.widget_track_name,
                title != null && title.length() > 0 ? title : "Walkman");
        rv.setTextViewText(R.id.widget_artist_name,
                artist != null && artist.length() > 0 ? artist : "Not playing");
        rv.setImageViewResource(R.id.widget_play_pause,
                isPlaying ? R.drawable.notification_pause : R.drawable.notification_play);

        Intent openApp = new Intent(context, NowPlayingActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(context, 0, openApp, 0);
        rv.setOnClickPendingIntent(R.id.widget_track_area, openPi);
        rv.setOnClickPendingIntent(R.id.widget_album_art, openPi);

        PendingIntent prevPi = PendingIntent.getBroadcast(context, 1,
                new Intent(MusicService.WIDGET_PREV), 0);
        PendingIntent playPi = PendingIntent.getBroadcast(context, 2,
                new Intent(MusicService.WIDGET_PLAY_PAUSE), 0);
        PendingIntent nextPi = PendingIntent.getBroadcast(context, 3,
                new Intent(MusicService.WIDGET_NEXT), 0);

        rv.setOnClickPendingIntent(R.id.widget_prev, prevPi);
        rv.setOnClickPendingIntent(R.id.widget_play_pause, playPi);
        rv.setOnClickPendingIntent(R.id.widget_next, nextPi);

        return rv;
    }

    private Bitmap loadJellyfinArt(String artId) {
        File artFile = new File(Environment.getExternalStorageDirectory(),
                ".walkman_art/jf_" + artId + ".jpg");
        if (artFile.exists()) {
            return BitmapFactory.decodeFile(artFile.getAbsolutePath());
        }
        return null;
    }

    private Bitmap loadArt(Context context, long albumId) {
        if (albumId < 0) return null;
        try {
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId);
            InputStream in = context.getContentResolver().openInputStream(artUri);
            if (in != null) {
                Bitmap bm = BitmapFactory.decodeStream(in);
                in.close();
                if (bm != null) return bm;
            }
        } catch (Exception e) {
        }
        File artFile = new File(Environment.getExternalStorageDirectory(),
                ".walkman_art/" + albumId + ".jpg");
        if (artFile.exists()) {
            return BitmapFactory.decodeFile(artFile.getAbsolutePath());
        }
        return null;
    }
}
