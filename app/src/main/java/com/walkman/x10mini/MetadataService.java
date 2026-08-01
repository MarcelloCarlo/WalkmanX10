package com.walkman.x10mini;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;

public class MetadataService extends IntentService {
    private static final int NOTIFY_ID = 100;
    public static final String ACTION_DONE = "com.walkman.x10mini.METADATA_DONE";
    private NotificationManager mNotifyManager;

    public MetadataService() {
        super("MetadataService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.ALBUM_ID
                },
                MediaStore.Audio.Media.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.Media.TITLE + " ASC");

        if (c == null) return;

        int total = c.getCount();
        int current = 0;
        int updated = 0;

        while (c.moveToNext()) {
            current++;
            long id = c.getLong(0);
            String title = c.getString(1);
            String artist = c.getString(2);
            String filePath = c.getString(3);
            long albumId = c.getLong(4);

            updateNotification("Updating " + current + " of " + total + "...");

            if (title == null || title.length() == 0) continue;

            MetadataUtils.LookupResult result =
                    MetadataUtils.lookupMusicBrainz(title, artist);

            if (result.found) {
                ContentValues values = new ContentValues();
                if (result.title != null && result.title.length() > 0) {
                    values.put(MediaStore.Audio.Media.TITLE, result.title);
                }
                if (result.artist != null && result.artist.length() > 0) {
                    values.put(MediaStore.Audio.Media.ARTIST, result.artist);
                }
                if (result.album != null && result.album.length() > 0) {
                    values.put(MediaStore.Audio.Media.ALBUM, result.album);
                }
                if (result.year != null && result.year.length() > 0) {
                    try {
                        values.put(MediaStore.Audio.Media.YEAR,
                                Integer.parseInt(result.year));
                    } catch (NumberFormatException e) {
                    }
                }

                if (values.size() > 0) {
                    getContentResolver().update(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            values,
                            MediaStore.Audio.Media._ID + "=?",
                            new String[]{String.valueOf(id)});
                }

                if (filePath != null && filePath.length() > 0) {
                    MetadataUtils.writeId3v1Tag(filePath,
                            result.title != null ? result.title : title,
                            result.artist != null ? result.artist : artist,
                            result.album, result.year);
                    sendBroadcast(new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(new File(filePath))));
                }

                if (result.releaseId != null && result.releaseId.length() > 0
                        && albumId >= 0) {
                    MetadataUtils.downloadAlbumArt(this, result.releaseId, albumId);
                }

                updated++;
            }

            try {
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                break;
            }
        }
        c.close();

        updateNotification("Done! Updated " + updated + " of " + total + " tracks.");
        sendBroadcast(new Intent(ACTION_DONE));
    }

    private void updateNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, WalkmanActivity.class), 0);
        Notification n = new Notification(
                R.drawable.music_statusbar_icon,
                text,
                System.currentTimeMillis());
        try {
            java.lang.reflect.Method m = Notification.class.getMethod(
                    "setLatestEventInfo",
                    android.content.Context.class, CharSequence.class,
                    CharSequence.class, PendingIntent.class);
            m.invoke(n, this, "Walkman", text, pi);
        } catch (Exception e) {
            n.contentIntent = pi;
        }
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        mNotifyManager.notify(NOTIFY_ID, n);
    }
}
