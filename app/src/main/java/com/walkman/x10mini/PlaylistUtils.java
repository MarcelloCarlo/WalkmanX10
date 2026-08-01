package com.walkman.x10mini;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class PlaylistUtils {

    public static long getOrCreateFavourites(Context ctx) {
        Cursor c = ctx.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Playlists._ID},
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[]{"Favourites"},
                null);
        if (c != null) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                c.close();
                return id;
            }
            c.close();
        }
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, "Favourites");
        Uri uri = ctx.getContentResolver().insert(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, cv);
        if (uri != null) {
            return ContentUris.parseId(uri);
        }
        return -1;
    }

    public static boolean isInPlaylist(Context ctx, long playlistId, long trackId) {
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        Cursor c = ctx.getContentResolver().query(uri,
                new String[]{MediaStore.Audio.Playlists.Members._ID},
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=?",
                new String[]{String.valueOf(trackId)},
                null);
        if (c != null) {
            boolean found = c.getCount() > 0;
            c.close();
            return found;
        }
        return false;
    }

    public static void addToPlaylist(Context ctx, long playlistId, long trackId) {
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        int order = 0;
        Cursor c = ctx.getContentResolver().query(uri,
                new String[]{"MAX(" + MediaStore.Audio.Playlists.Members.PLAY_ORDER + ")"},
                null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                order = c.getInt(0) + 1;
            }
            c.close();
        }
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, trackId);
        cv.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, order);
        ctx.getContentResolver().insert(uri, cv);
    }

    public static void removeFromPlaylist(Context ctx, long playlistId, long trackId) {
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        ctx.getContentResolver().delete(uri,
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=?",
                new String[]{String.valueOf(trackId)});
    }

    public static long createPlaylist(Context ctx, String name) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Playlists.NAME, name);
        Uri uri = ctx.getContentResolver().insert(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, cv);
        if (uri != null) {
            return ContentUris.parseId(uri);
        }
        return -1;
    }
}
