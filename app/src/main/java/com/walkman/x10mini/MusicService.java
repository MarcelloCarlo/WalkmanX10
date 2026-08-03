package com.walkman.x10mini;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.util.Log;
import android.widget.RemoteViews;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class MusicService extends Service implements
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener {

    private static final String TAG = "WalkmanService";
    public static final String ACTION_PLAY = "com.walkman.x10mini.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.walkman.x10mini.ACTION_PAUSE";
    public static final String ACTION_NEXT = "com.walkman.x10mini.ACTION_NEXT";
    public static final String ACTION_PREV = "com.walkman.x10mini.ACTION_PREV";
    public static final String ACTION_STOP = "com.walkman.x10mini.ACTION_STOP";
    public static final String ACTION_TOGGLE = "com.walkman.x10mini.ACTION_TOGGLE";
    public static final String META_CHANGED = "com.walkman.x10mini.META_CHANGED";
    public static final String PLAYSTATE_CHANGED = "com.walkman.x10mini.PLAYSTATE_CHANGED";
    public static final String QUEUE_CHANGED = "com.walkman.x10mini.QUEUE_CHANGED";
    public static final String WIDGET_UPDATE = "com.walkman.x10mini.WIDGET_UPDATE";
    public static final String WIDGET_PLAY_PAUSE = "com.walkman.x10mini.WIDGET_PLAY_PAUSE";
    public static final String WIDGET_NEXT = "com.walkman.x10mini.WIDGET_NEXT";
    public static final String WIDGET_PREV = "com.walkman.x10mini.WIDGET_PREV";
    private static final int NOTIFICATION_ID = 1;

    public static class JellyfinTrack {
        public String jellyfinId;
        public String title;
        public String artist;
        public String album;
        public String albumId;
        public String streamUrl;
        public String imageUrl;
        public long durationMs;
    }

    private MediaPlayer mPlayer;
    private MediaPlayer mNextPlayer;
    private boolean mNextPrepared = false;
    private int mNextIndex = -1;
    private int mJellyfinSeekOffset = 0;
    private ArrayList<Long> mPlayQueue = new ArrayList<Long>();
    private int mCurrentIndex = -1;
    private boolean mIsPlaying = false;
    private boolean mIsPrepared = false;
    private String mCurrentTitle = "";
    private String mCurrentArtist = "";
    private String mCurrentAlbum = "";
    private long mCurrentAlbumId = -1;
    private String mCurrentFilePath = "";
    private long mCurrentId = -1;
    private int mRepeatMode = 0; // 0=off, 1=all, 2=one
    private boolean mShuffle = false;
    private boolean mPausedByCall = false;
    private int mPendingSeek = 0;
    private int mSavedDuration = 0;
    private HashMap<Long, JellyfinTrack> mJellyfinTracks = new HashMap<Long, JellyfinTrack>();
    private long mNextJellyfinId = -1;
    private boolean mIsJellyfinQueue = false;
    private final IBinder mBinder = new MusicBinder();
    private PowerManager.WakeLock mWakeLock;
    private Handler mHandler = new Handler();
    private OnPlaybackChangeListener mListener;

    private Runnable mPositionSaver = new Runnable() {
        public void run() {
            if (mIsPrepared && mIsPlaying) {
                saveState();
                mHandler.postDelayed(this, 5000);
            }
        }
    };

    private Runnable mGaplessChecker = new Runnable() {
        public void run() {
            if (!mIsPrepared || !mIsPlaying || mRepeatMode == 2) return;
            int remaining = mPlayer.getDuration() - mPlayer.getCurrentPosition();
            if (remaining < 5000 && remaining > 0 && mNextPlayer == null) {
                prepareNextTrack();
            }
            if (mIsPrepared && mIsPlaying) {
                mHandler.postDelayed(this, 1000);
            }
        }
    };

    public interface OnPlaybackChangeListener {
        void onTrackChanged();
        void onPlayStateChanged(boolean playing);
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private PhoneStateListener mPhoneListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String number) {
            if (state == TelephonyManager.CALL_STATE_RINGING ||
                state == TelephonyManager.CALL_STATE_OFFHOOK) {
                if (mIsPlaying) {
                    pause();
                    mPausedByCall = true;
                }
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                if (mPausedByCall) {
                    play();
                    mPausedByCall = false;
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(this);
        mPlayer.setOnErrorListener(this);
        mPlayer.setOnPreparedListener(this);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.setReferenceCounted(false);

        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tm != null) {
            tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        SharedPreferences prefs = getSharedPreferences("walkman", MODE_PRIVATE);
        long savedId = prefs.getLong("last_track_id", -1);
        mIsJellyfinQueue = prefs.getBoolean("is_jellyfin_queue", false);
        if (savedId != -1) {
            mCurrentId = savedId;
            mPendingSeek = prefs.getInt("last_position", 0);
            mSavedDuration = prefs.getInt("last_duration", 0);
            String queueStr = prefs.getString("last_queue", "");
            if (queueStr.length() > 0) {
                String[] ids = queueStr.split(",");
                for (int i = 0; i < ids.length; i++) {
                    try {
                        mPlayQueue.add(Long.parseLong(ids[i]));
                    } catch (NumberFormatException e) {
                    }
                }
                mCurrentIndex = prefs.getInt("last_queue_index", 0);
                if (mCurrentIndex >= mPlayQueue.size()) mCurrentIndex = 0;
            }
            if (mIsJellyfinQueue) {
                restoreJellyfinTracks(prefs);
                JellyfinTrack jt = mJellyfinTracks.get(mCurrentId);
                if (jt != null) {
                    mCurrentTitle = jt.title != null ? jt.title : "";
                    mCurrentArtist = jt.artist != null ? jt.artist : "";
                    mCurrentAlbum = jt.album != null ? jt.album : "";
                    mCurrentAlbumId = mCurrentId;
                    mCurrentFilePath = "";
                }
            } else if (savedId >= 0) {
                loadTrackInfo(savedId);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                play();
            } else if (ACTION_PAUSE.equals(action)) {
                pause();
            } else if (ACTION_NEXT.equals(action)) {
                next();
            } else if (ACTION_PREV.equals(action)) {
                prev();
            } else if (ACTION_STOP.equals(action)) {
                stop();
                stopSelf();
            } else if (ACTION_TOGGLE.equals(action)) {
                if (mIsPlaying) pause(); else play();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        saveState();
        stop();
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tm != null) {
            tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
        }
        super.onDestroy();
    }

    public void setOnPlaybackChangeListener(OnPlaybackChangeListener l) {
        mListener = l;
    }

    public void setQueue(ArrayList<Long> ids, int startIndex) {
        mPlayQueue.clear();
        mPlayQueue.addAll(ids);
        mJellyfinTracks.clear();
        mIsJellyfinQueue = false;
        mCurrentIndex = startIndex;
        sendBroadcast(new Intent(QUEUE_CHANGED));
        openAndPlay(mCurrentIndex);
    }

    public void setJellyfinQueue(ArrayList<JellyfinTrack> tracks, int startIndex) {
        mPlayQueue.clear();
        mJellyfinTracks.clear();
        mNextJellyfinId = -1;
        mIsJellyfinQueue = true;
        for (int i = 0; i < tracks.size(); i++) {
            long syntheticId = mNextJellyfinId--;
            mJellyfinTracks.put(syntheticId, tracks.get(i));
            mPlayQueue.add(syntheticId);
        }
        mCurrentIndex = startIndex;
        sendBroadcast(new Intent(QUEUE_CHANGED));
        openAndPlay(mCurrentIndex);
    }

    public void addToQueue(long id) {
        if (mIsJellyfinQueue) {
            clearJellyfinQueue();
        }
        mPlayQueue.add(id);
        sendBroadcast(new Intent(QUEUE_CHANGED));
    }

    public void addJellyfinToQueue(JellyfinTrack track) {
        if (!mIsJellyfinQueue) {
            clearLocalQueue();
        }
        long syntheticId = mNextJellyfinId--;
        mJellyfinTracks.put(syntheticId, track);
        mPlayQueue.add(syntheticId);
        sendBroadcast(new Intent(QUEUE_CHANGED));
    }

    public void addToQueueNext(long id) {
        if (mIsJellyfinQueue) {
            clearJellyfinQueue();
        }
        int insertPos = mCurrentIndex + 1;
        if (insertPos > mPlayQueue.size()) insertPos = mPlayQueue.size();
        mPlayQueue.add(insertPos, id);
        sendBroadcast(new Intent(QUEUE_CHANGED));
    }

    public void addJellyfinToQueueNext(JellyfinTrack track) {
        if (!mIsJellyfinQueue) {
            clearLocalQueue();
        }
        long syntheticId = mNextJellyfinId--;
        mJellyfinTracks.put(syntheticId, track);
        int insertPos = mCurrentIndex + 1;
        if (insertPos > mPlayQueue.size()) insertPos = mPlayQueue.size();
        mPlayQueue.add(insertPos, syntheticId);
        sendBroadcast(new Intent(QUEUE_CHANGED));
    }

    private void clearJellyfinQueue() {
        releaseNextPlayer();
        mJellyfinTracks.clear();
        mPlayQueue.clear();
        mNextJellyfinId = -1;
        mIsJellyfinQueue = false;
        mCurrentIndex = -1;
    }

    private void clearLocalQueue() {
        releaseNextPlayer();
        mPlayQueue.clear();
        mIsJellyfinQueue = true;
        mCurrentIndex = -1;
    }

    private void openAndPlay(int index) {
        if (index < 0 || index >= mPlayQueue.size()) return;
        releaseNextPlayer();
        mJellyfinSeekOffset = 0;
        mCurrentIndex = index;
        mCurrentId = mPlayQueue.get(index);

        if (mCurrentId < 0 && mJellyfinTracks.containsKey(mCurrentId)) {
            JellyfinTrack jt = mJellyfinTracks.get(mCurrentId);
            mCurrentTitle = jt.title != null ? jt.title : "";
            mCurrentArtist = jt.artist != null ? jt.artist : "";
            mCurrentAlbum = jt.album != null ? jt.album : "";
            mCurrentAlbumId = mCurrentId;
            mCurrentFilePath = "";
            mSavedDuration = (int) jt.durationMs;
            saveState();
            notifyMetaChanged();
            updateWidget();
            try {
                mPlayer.reset();
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setDataSource(jt.streamUrl);
                mPlayer.prepareAsync();
                mIsPrepared = false;
            } catch (IOException e) {
                Log.e(TAG, "Error opening Jellyfin stream", e);
            }
        } else {
            Uri uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCurrentId);
            loadTrackInfo(mCurrentId);
            saveState();
            try {
                mPlayer.reset();
                mPlayer.setDataSource(this, uri);
                mPlayer.prepareAsync();
                mIsPrepared = false;
            } catch (IOException e) {
                Log.e(TAG, "Error opening track", e);
            }
        }
    }

    private void loadTrackInfo(long id) {
        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.DATA
                },
                MediaStore.Audio.Media._ID + "=?",
                new String[]{String.valueOf(id)},
                null);
        if (c != null) {
            if (c.moveToFirst()) {
                mCurrentTitle = c.getString(0);
                mCurrentArtist = c.getString(1);
                mCurrentAlbum = c.getString(2);
                mCurrentAlbumId = c.getLong(3);
                mCurrentFilePath = c.getString(4);
            }
            c.close();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp == mNextPlayer) {
            mNextPrepared = true;
            return;
        }
        mIsPrepared = true;
        int playerDur = mp.getDuration();
        if (playerDur > 0) {
            mSavedDuration = playerDur;
        }
        if (mPendingSeek > 0) {
            mp.seekTo(mPendingSeek);
            mPendingSeek = 0;
        }
        mp.start();
        mIsPlaying = true;
        mWakeLock.acquire();
        if (mCurrentId >= 0) {
            recordPlay(mCurrentId);
        }
        showNotification();
        mHandler.removeCallbacks(mPositionSaver);
        mHandler.postDelayed(mPositionSaver, 5000);
        mHandler.removeCallbacks(mGaplessChecker);
        mHandler.postDelayed(mGaplessChecker, 1000);
        notifyMetaChanged();
        notifyPlayStateChanged();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mRepeatMode == 2) {
            openAndPlay(mCurrentIndex);
            return;
        }
        if (mNextPlayer != null && mNextPrepared && mNextIndex >= 0) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = mNextPlayer;
            mNextPlayer = null;
            mNextPrepared = false;
            mJellyfinSeekOffset = 0;
            mCurrentIndex = mNextIndex;
            mNextIndex = -1;
            mCurrentId = mPlayQueue.get(mCurrentIndex);
            if (mCurrentId < 0 && mJellyfinTracks.containsKey(mCurrentId)) {
                JellyfinTrack jt = mJellyfinTracks.get(mCurrentId);
                mCurrentTitle = jt.title != null ? jt.title : "";
                mCurrentArtist = jt.artist != null ? jt.artist : "";
                mCurrentAlbum = jt.album != null ? jt.album : "";
                mCurrentAlbumId = mCurrentId;
                mCurrentFilePath = "";
                mSavedDuration = (int) jt.durationMs;
            } else {
                loadTrackInfo(mCurrentId);
            }
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.setOnPreparedListener(this);
            mIsPrepared = true;
            int playerDur = mPlayer.getDuration();
            if (playerDur > 0) mSavedDuration = playerDur;
            mPlayer.start();
            mIsPlaying = true;
            if (mCurrentId >= 0) recordPlay(mCurrentId);
            saveState();
            showNotification();
            updateWidget();
            mHandler.removeCallbacks(mPositionSaver);
            mHandler.postDelayed(mPositionSaver, 5000);
            mHandler.removeCallbacks(mGaplessChecker);
            mHandler.postDelayed(mGaplessChecker, 1000);
            notifyMetaChanged();
            notifyPlayStateChanged();
        } else {
            releaseNextPlayer();
            next();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: " + what + "/" + extra);
        if (mp == mNextPlayer) {
            releaseNextPlayer();
            return true;
        }
        mIsPlaying = false;
        mIsPrepared = false;
        notifyPlayStateChanged();
        return true;
    }

    private void prepareNextTrack() {
        if (mPlayQueue.isEmpty() || mNextPlayer != null) return;
        int nextIdx;
        if (mShuffle) {
            nextIdx = (int)(Math.random() * mPlayQueue.size());
        } else {
            nextIdx = mCurrentIndex + 1;
            if (nextIdx >= mPlayQueue.size()) {
                if (mRepeatMode == 1) {
                    nextIdx = 0;
                } else {
                    return;
                }
            }
        }
        long nextId = mPlayQueue.get(nextIdx);
        try {
            mNextPlayer = new MediaPlayer();
            mNextPlayer.setOnPreparedListener(this);
            mNextPlayer.setOnErrorListener(this);
            mNextPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if (nextId < 0 && mJellyfinTracks.containsKey(nextId)) {
                JellyfinTrack jt = mJellyfinTracks.get(nextId);
                mNextPlayer.setDataSource(jt.streamUrl);
            } else {
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, nextId);
                mNextPlayer.setDataSource(this, uri);
            }
            mNextIndex = nextIdx;
            mNextPrepared = false;
            mNextPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error preparing next track", e);
            releaseNextPlayer();
        }
    }

    private void releaseNextPlayer() {
        mHandler.removeCallbacks(mGaplessChecker);
        if (mNextPlayer != null) {
            try {
                mNextPlayer.reset();
                mNextPlayer.release();
            } catch (Exception e) {
            }
            mNextPlayer = null;
        }
        mNextPrepared = false;
        mNextIndex = -1;
    }

    public void play() {
        if (mIsPrepared) {
            mPlayer.start();
            mIsPlaying = true;
            mWakeLock.acquire();
            showNotification();
            mHandler.removeCallbacks(mPositionSaver);
            mHandler.postDelayed(mPositionSaver, 5000);
            notifyPlayStateChanged();
        } else if (mCurrentIndex >= 0 && mCurrentIndex < mPlayQueue.size()) {
            openAndPlay(mCurrentIndex);
        }
    }

    public void pause() {
        if (mIsPrepared && mIsPlaying) {
            mHandler.removeCallbacks(mPositionSaver);
            mPlayer.pause();
            mIsPlaying = false;
            if (mWakeLock.isHeld()) mWakeLock.release();
            saveState();
            notifyPlayStateChanged();
        }
    }

    public void stop() {
        if (mPlayer != null) {
            releaseNextPlayer();
            mHandler.removeCallbacks(mPositionSaver);
            saveState();
            mPlayer.reset();
            mIsPlaying = false;
            mIsPrepared = false;
            if (mWakeLock.isHeld()) mWakeLock.release();
            stopForeground(true);
            notifyPlayStateChanged();
        }
    }

    public void next() {
        if (mPlayQueue.isEmpty()) return;
        int nextIndex;
        if (mShuffle) {
            nextIndex = (int)(Math.random() * mPlayQueue.size());
        } else {
            nextIndex = mCurrentIndex + 1;
            if (nextIndex >= mPlayQueue.size()) {
                if (mRepeatMode == 1) {
                    nextIndex = 0;
                } else {
                    stop();
                    return;
                }
            }
        }
        openAndPlay(nextIndex);
    }

    public void prev() {
        if (mPlayQueue.isEmpty()) return;
        if (mIsPrepared && getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }
        int prevIndex = mCurrentIndex - 1;
        if (prevIndex < 0) {
            prevIndex = mRepeatMode == 1 ? mPlayQueue.size() - 1 : 0;
        }
        openAndPlay(prevIndex);
    }

    private int mSeekSessionCounter = 0;

    public void seekTo(int ms) {
        if (!mIsPrepared) return;
        if (mCurrentId < 0 && mJellyfinTracks.containsKey(mCurrentId)) {
            JellyfinTrack jt = mJellyfinTracks.get(mCurrentId);
            String baseUrl = jt.streamUrl;
            int stIdx = baseUrl.indexOf("&StartTimeTicks=");
            if (stIdx >= 0) baseUrl = baseUrl.substring(0, stIdx);
            stIdx = baseUrl.indexOf("&PlaySessionId=");
            if (stIdx >= 0) baseUrl = baseUrl.substring(0, stIdx);
            long ticks = (long) ms * 10000L;
            String sessionId = "seek" + System.currentTimeMillis() + "_" + (mSeekSessionCounter++);
            String seekUrl = baseUrl + "&StartTimeTicks=" + ticks
                    + "&PlaySessionId=" + sessionId;
            mJellyfinSeekOffset = ms;
            releaseNextPlayer();
            try {
                mPlayer.reset();
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setDataSource(seekUrl);
                mIsPrepared = false;
                mPlayer.prepareAsync();
            } catch (IOException e) {
                Log.e(TAG, "Error seeking Jellyfin stream", e);
            }
        } else {
            mPlayer.seekTo(ms);
        }
    }

    public int getCurrentPosition() {
        if (mIsPrepared) return mPlayer.getCurrentPosition() + mJellyfinSeekOffset;
        if (mPendingSeek > 0) return mPendingSeek;
        return mJellyfinSeekOffset;
    }

    public int getDuration() {
        if (mSavedDuration > 0) return mSavedDuration;
        if (mIsPrepared) {
            int dur = mPlayer.getDuration();
            if (dur > 0) return dur;
        }
        return 0;
    }

    public boolean isPlaying() { return mIsPlaying; }
    public String getTitle() { return mCurrentTitle; }
    public String getArtist() { return mCurrentArtist; }
    public String getAlbum() { return mCurrentAlbum; }
    public long getAlbumId() { return mCurrentAlbumId; }
    public String getFilePath() { return mCurrentFilePath; }
    public long getCurrentTrackId() { return mCurrentId; }
    public boolean isJellyfinTrack() { return mCurrentId < 0 && mJellyfinTracks.containsKey(mCurrentId); }
    public JellyfinTrack getJellyfinTrack() { return mJellyfinTracks.get(mCurrentId); }

    public void refreshTrackInfo() {
        if (mCurrentId >= 0) {
            loadTrackInfo(mCurrentId);
            notifyMetaChanged();
        }
    }
    public int getQueuePosition() { return mCurrentIndex; }
    public int getQueueSize() { return mPlayQueue.size(); }

    public ArrayList<String[]> getQueueInfo() {
        ArrayList<String[]> info = new ArrayList<String[]>();
        for (int i = 0; i < mPlayQueue.size(); i++) {
            long id = mPlayQueue.get(i);
            String title = null;
            String artist = null;
            if (id < 0 && mJellyfinTracks.containsKey(id)) {
                JellyfinTrack jt = mJellyfinTracks.get(id);
                title = jt.title;
                artist = jt.artist;
            } else if (id >= 0) {
                Cursor c = getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST},
                        MediaStore.Audio.Media._ID + "=?",
                        new String[]{String.valueOf(id)}, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        title = c.getString(0);
                        artist = c.getString(1);
                    }
                    c.close();
                }
            }
            info.add(new String[]{
                    title != null ? title : "Unknown",
                    artist != null ? artist : "Unknown"
            });
        }
        return info;
    }

    public void playQueueIndex(int index) {
        if (index >= 0 && index < mPlayQueue.size()) {
            openAndPlay(index);
        }
    }

    public int getRepeatMode() { return mRepeatMode; }
    public void setRepeatMode(int mode) { mRepeatMode = mode; }
    public boolean getShuffle() { return mShuffle; }
    public void setShuffle(boolean shuffle) { mShuffle = shuffle; }

    private void recordPlay(long trackId) {
        SharedPreferences sp = getSharedPreferences("play_history", MODE_PRIVATE);
        String data = sp.getString("history", "");
        HashMap<Long, long[]> map = new HashMap<Long, long[]>();
        if (data.length() > 0) {
            String[] entries = data.split(",");
            for (int i = 0; i < entries.length; i++) {
                String[] parts = entries[i].split(":");
                if (parts.length == 3) {
                    try {
                        long id = Long.parseLong(parts[0]);
                        long ts = Long.parseLong(parts[1]);
                        long count = Long.parseLong(parts[2]);
                        map.put(id, new long[]{ts, count});
                    } catch (NumberFormatException e) {}
                }
            }
        }
        long now = System.currentTimeMillis();
        long[] existing = map.get(trackId);
        if (existing != null) {
            map.put(trackId, new long[]{now, existing[1] + 1});
        } else {
            map.put(trackId, new long[]{now, 1});
        }
        if (map.size() > 200) {
            long oldestTs = Long.MAX_VALUE;
            Long oldestId = null;
            for (java.util.Map.Entry<Long, long[]> e : map.entrySet()) {
                if (e.getValue()[0] < oldestTs) {
                    oldestTs = e.getValue()[0];
                    oldestId = e.getKey();
                }
            }
            if (oldestId != null) map.remove(oldestId);
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Long, long[]> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue()[0]).append(':').append(e.getValue()[1]);
        }
        sp.edit().putString("history", sb.toString()).commit();
    }

    public ArrayList<Long> getRecentlyPlayed(int limit) {
        SharedPreferences sp = getSharedPreferences("play_history", MODE_PRIVATE);
        String data = sp.getString("history", "");
        ArrayList<long[]> entries = new ArrayList<long[]>();
        if (data.length() > 0) {
            String[] parts = data.split(",");
            for (int i = 0; i < parts.length; i++) {
                String[] f = parts[i].split(":");
                if (f.length == 3) {
                    try {
                        entries.add(new long[]{Long.parseLong(f[0]), Long.parseLong(f[1])});
                    } catch (NumberFormatException e) {}
                }
            }
        }
        Collections.sort(entries, new java.util.Comparator<long[]>() {
            public int compare(long[] a, long[] b) {
                return b[1] > a[1] ? 1 : (b[1] < a[1] ? -1 : 0);
            }
        });
        ArrayList<Long> result = new ArrayList<Long>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            result.add(entries.get(i)[0]);
        }
        return result;
    }

    public ArrayList<Long> getFrequentlyPlayed(int limit) {
        SharedPreferences sp = getSharedPreferences("play_history", MODE_PRIVATE);
        String data = sp.getString("history", "");
        ArrayList<long[]> entries = new ArrayList<long[]>();
        if (data.length() > 0) {
            String[] parts = data.split(",");
            for (int i = 0; i < parts.length; i++) {
                String[] f = parts[i].split(":");
                if (f.length == 3) {
                    try {
                        entries.add(new long[]{Long.parseLong(f[0]), Long.parseLong(f[2])});
                    } catch (NumberFormatException e) {}
                }
            }
        }
        Collections.sort(entries, new java.util.Comparator<long[]>() {
            public int compare(long[] a, long[] b) {
                return b[1] > a[1] ? 1 : (b[1] < a[1] ? -1 : 0);
            }
        });
        ArrayList<Long> result = new ArrayList<Long>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            result.add(entries.get(i)[0]);
        }
        return result;
    }

    private void saveState() {
        SharedPreferences.Editor ed = getSharedPreferences("walkman", MODE_PRIVATE).edit();
        ed.putLong("last_track_id", mCurrentId);
        if (mIsPrepared) {
            ed.putInt("last_position", mPlayer.getCurrentPosition());
            ed.putInt("last_duration", mPlayer.getDuration());
        } else if (mPendingSeek > 0) {
            ed.putInt("last_position", mPendingSeek);
        }
        ed.putBoolean("is_jellyfin_queue", mIsJellyfinQueue);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mPlayQueue.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(mPlayQueue.get(i));
        }
        ed.putString("last_queue", sb.toString());
        ed.putInt("last_queue_index", mCurrentIndex);
        if (mIsJellyfinQueue) {
            StringBuilder jfData = new StringBuilder();
            for (int i = 0; i < mPlayQueue.size(); i++) {
                long id = mPlayQueue.get(i);
                JellyfinTrack jt = mJellyfinTracks.get(id);
                if (jt == null) continue;
                if (jfData.length() > 0) jfData.append('\n');
                jfData.append(id).append('\t')
                        .append(safe(jt.jellyfinId)).append('\t')
                        .append(safe(jt.title)).append('\t')
                        .append(safe(jt.artist)).append('\t')
                        .append(safe(jt.album)).append('\t')
                        .append(safe(jt.albumId)).append('\t')
                        .append(safe(jt.streamUrl)).append('\t')
                        .append(safe(jt.imageUrl)).append('\t')
                        .append(jt.durationMs);
            }
            ed.putString("jellyfin_tracks", jfData.toString());
        } else {
            ed.remove("jellyfin_tracks");
        }
        ed.commit();
    }

    private String safe(String s) { return s != null ? s : ""; }

    private void restoreJellyfinTracks(SharedPreferences prefs) {
        String data = prefs.getString("jellyfin_tracks", "");
        if (data.length() == 0) return;
        String[] lines = data.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String[] parts = lines[i].split("\t");
            if (parts.length < 9) continue;
            try {
                long id = Long.parseLong(parts[0]);
                JellyfinTrack jt = new JellyfinTrack();
                jt.jellyfinId = parts[1];
                jt.title = parts[2];
                jt.artist = parts[3];
                jt.album = parts[4];
                jt.albumId = parts[5];
                jt.streamUrl = parts[6];
                jt.imageUrl = parts[7];
                jt.durationMs = Long.parseLong(parts[8]);
                mJellyfinTracks.put(id, jt);
                if (id <= mNextJellyfinId) {
                    mNextJellyfinId = id - 1;
                }
            } catch (NumberFormatException e) {
            }
        }
    }

    private void showNotification() {
        Intent ni = new Intent(this, NowPlayingActivity.class);
        ni.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, ni, 0);

        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_player);
        rv.setTextViewText(R.id.notif_title, mCurrentTitle);
        rv.setTextViewText(R.id.notif_artist, mCurrentArtist);
        rv.setImageViewResource(R.id.notif_play_pause,
                mIsPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        Bitmap art = loadNotificationArt(mCurrentAlbumId);
        if (art != null) {
            rv.setImageViewBitmap(R.id.notif_album_art, art);
        } else {
            rv.setImageViewResource(R.id.notif_album_art, R.drawable.musicplayer_default_album);
        }

        PendingIntent prevPi = PendingIntent.getBroadcast(this, 1,
                new Intent(WIDGET_PREV), 0);
        PendingIntent togglePi = PendingIntent.getBroadcast(this, 2,
                new Intent(WIDGET_PLAY_PAUSE), 0);
        PendingIntent nextPi = PendingIntent.getBroadcast(this, 3,
                new Intent(WIDGET_NEXT), 0);

        rv.setOnClickPendingIntent(R.id.notif_prev, prevPi);
        rv.setOnClickPendingIntent(R.id.notif_play_pause, togglePi);
        rv.setOnClickPendingIntent(R.id.notif_next, nextPi);

        Notification n = new Notification(
                R.drawable.music_statusbar_icon,
                mCurrentTitle,
                System.currentTimeMillis());
        n.contentView = rv;
        n.contentIntent = contentIntent;
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        startForeground(NOTIFICATION_ID, n);

        updateWidget();
    }

    private Bitmap loadNotificationArt(long albumId) {
        if (albumId < 0) {
            JellyfinTrack jt = mJellyfinTracks.get(albumId);
            if (jt != null && jt.jellyfinId != null) {
                File artFile = new File(Environment.getExternalStorageDirectory(),
                        ".walkman_art/jf_" + jt.jellyfinId + ".jpg");
                if (artFile.exists()) {
                    return BitmapFactory.decodeFile(artFile.getAbsolutePath());
                }
                String artAlbumId = jt.albumId != null ? jt.albumId : jt.jellyfinId;
                File albumArtFile = new File(Environment.getExternalStorageDirectory(),
                        ".walkman_art/jf_" + artAlbumId + ".jpg");
                if (albumArtFile.exists()) {
                    return BitmapFactory.decodeFile(albumArtFile.getAbsolutePath());
                }
            }
            return null;
        }
        try {
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId);
            InputStream in = getContentResolver().openInputStream(artUri);
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

    private void updateWidget() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        ComponentName widget = new ComponentName(this, NowPlayingWidget.class);
        int[] ids = mgr.getAppWidgetIds(widget);
        if (ids == null || ids.length == 0) return;

        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.widget_now_playing);

        String title = mCurrentTitle;
        String artist = mCurrentArtist;
        rv.setTextViewText(R.id.widget_track_name,
                title != null && title.length() > 0 ? title : "Walkman");
        rv.setTextViewText(R.id.widget_artist_name,
                artist != null && artist.length() > 0 ? artist : "Not playing");
        rv.setImageViewResource(R.id.widget_play_pause,
                mIsPlaying ? R.drawable.notification_pause : R.drawable.notification_play);

        Bitmap art = null;
        if (mCurrentAlbumId < 0 && mJellyfinTracks.containsKey(mCurrentId)) {
            JellyfinTrack jt = mJellyfinTracks.get(mCurrentId);
            if (jt != null) {
                String artId = jt.albumId != null ? jt.albumId : jt.jellyfinId;
                File artFile = new File(Environment.getExternalStorageDirectory(),
                        ".walkman_art/jf_" + artId + ".jpg");
                if (artFile.exists()) {
                    art = BitmapFactory.decodeFile(artFile.getAbsolutePath());
                }
            }
        } else if (mCurrentAlbumId >= 0) {
            art = loadNotificationArt(mCurrentAlbumId);
        }
        if (art != null) {
            rv.setImageViewBitmap(R.id.widget_album_art, art);
        } else {
            rv.setImageViewResource(R.id.widget_album_art, R.drawable.musicplayer_default_album);
        }

        Intent openApp = new Intent(this, NowPlayingActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openApp, 0);
        rv.setOnClickPendingIntent(R.id.widget_track_area, openPi);
        rv.setOnClickPendingIntent(R.id.widget_album_art, openPi);

        PendingIntent prevPi = PendingIntent.getBroadcast(this, 1,
                new Intent(WIDGET_PREV), 0);
        PendingIntent playPi = PendingIntent.getBroadcast(this, 2,
                new Intent(WIDGET_PLAY_PAUSE), 0);
        PendingIntent nextPi = PendingIntent.getBroadcast(this, 3,
                new Intent(WIDGET_NEXT), 0);

        rv.setOnClickPendingIntent(R.id.widget_prev, prevPi);
        rv.setOnClickPendingIntent(R.id.widget_play_pause, playPi);
        rv.setOnClickPendingIntent(R.id.widget_next, nextPi);

        mgr.updateAppWidget(ids, rv);
    }

    private void notifyMetaChanged() {
        sendBroadcast(new Intent(META_CHANGED));
        if (mListener != null) mListener.onTrackChanged();
    }

    private void notifyPlayStateChanged() {
        sendBroadcast(new Intent(PLAYSTATE_CHANGED));
        if (mListener != null) mListener.onPlayStateChanged(mIsPlaying);
        updateWidget();
    }
}
