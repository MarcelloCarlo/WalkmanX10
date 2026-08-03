package com.walkman.x10mini;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.Environment;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

public class NowPlayingActivity extends Activity implements View.OnClickListener {
    private static final int MENU_REPEAT = 1;
    private static final int MENU_SHUFFLE = 2;
    private static final int MENU_EDIT_INFO = 3;
    private static final int MENU_LYRICS = 4;
    private static final int MENU_FAVOURITE = 5;
    private static final int MENU_ADD_PLAYLIST = 6;
    private static final int MENU_DOWNLOAD = 7;
    private static final int MENU_GO_ALBUM = 8;
    private static final int MENU_QUEUE = 9;

    private MusicService mService;
    private boolean mBound = false;
    private boolean mNeedsRefresh = false;
    private ImageView mAlbumArt;
    private TextView mTitle;
    private TextView mArtist;
    private TextView mAlbumText;
    private TextView mPosition;
    private TextView mDuration;
    private SeekBar mSeekBar;
    private ImageButton mBtnPrev;
    private ImageButton mBtnPlay;
    private ImageButton mBtnNext;
    private ImageView mShuffleBtn;
    private ImageView mRepeatBtn;
    private ScrollView mLyricsScroll;
    private LinearLayout mLyricsContainer;
    private boolean mLyricsLoaded = false;
    private boolean mLyricsVisible = false;
    private String mCurrentLyrics = null;
    private ArrayList<LrcParser.LrcLine> mLrcLines = null;
    private int mCurrentLrcIndex = -1;
    private Handler mHandler = new Handler();
    private boolean mUserSeeking = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((MusicService.MusicBinder) binder).getService();
            mBound = true;
            if (mNeedsRefresh) {
                mService.refreshTrackInfo();
                mNeedsRefresh = false;
            }
            updateUI();
            startProgressUpdates();
        }
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            updateUI();
        }
    };

    private Runnable mProgressRunner = new Runnable() {
        public void run() {
            if (mBound && !mUserSeeking) {
                int pos = mService.getCurrentPosition();
                int dur = mService.getDuration();
                mSeekBar.setMax(dur);
                mSeekBar.setProgress(pos);
                mPosition.setText(formatTime(pos));
                mDuration.setText(formatTime(dur));
                if (mLyricsVisible && mLrcLines != null) {
                    updateSyncedLyrics(pos);
                }
            }
            mHandler.postDelayed(this, 500);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        bindViews();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_player);
        bindViews();
        if (mBound) {
            mTitle.setText(mService.getTitle());
            mArtist.setText(mService.getArtist());
            if (mAlbumText != null) mAlbumText.setText(mService.getAlbum());
            updatePlayButton();
            updateShuffleRepeat();
            loadAlbumArt(mService.getAlbumId());
            if (mLyricsVisible && mLrcLines != null) {
                showSyncedLyrics();
            } else if (mLyricsVisible && mCurrentLyrics != null) {
                showPlainLyrics(mCurrentLyrics);
            } else if (mLyricsVisible) {
                showPlainLyrics("Loading lyrics...");
                fetchAndShowLyrics();
            }
        }
    }

    private void bindViews() {
        mAlbumArt = (ImageView) findViewById(R.id.album_art);
        mTitle = (TextView) findViewById(R.id.player_title);
        mArtist = (TextView) findViewById(R.id.player_artist);
        mAlbumText = (TextView) findViewById(R.id.player_album);
        mPosition = (TextView) findViewById(R.id.player_position);
        mDuration = (TextView) findViewById(R.id.player_duration);
        mSeekBar = (SeekBar) findViewById(R.id.player_seekbar);
        mBtnPrev = (ImageButton) findViewById(R.id.btn_prev);
        mBtnPlay = (ImageButton) findViewById(R.id.btn_play);
        mBtnNext = (ImageButton) findViewById(R.id.btn_next);
        mShuffleBtn = (ImageView) findViewById(R.id.btn_shuffle);
        mRepeatBtn = (ImageView) findViewById(R.id.btn_repeat);
        mLyricsScroll = (ScrollView) findViewById(R.id.lyrics_scroll);
        mLyricsContainer = (LinearLayout) findViewById(R.id.lyrics_container);

        mBtnPrev.setOnClickListener(this);
        mBtnPlay.setOnClickListener(this);
        mBtnNext.setOnClickListener(this);

        if (mShuffleBtn != null) {
            mShuffleBtn.setOnClickListener(this);
        }
        if (mRepeatBtn != null) {
            mRepeatBtn.setOnClickListener(this);
        }

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    mPosition.setText(formatTime(progress));
                }
            }
            public void onStartTrackingTouch(SeekBar sb) {
                mUserSeeking = true;
            }
            public void onStopTrackingTouch(SeekBar sb) {
                mUserSeeking = false;
                if (mBound) {
                    mService.seekTo(sb.getProgress());
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), mConnection, Context.BIND_AUTO_CREATE);
        IntentFilter f = new IntentFilter();
        f.addAction(MusicService.META_CHANGED);
        f.addAction(MusicService.PLAYSTATE_CHANGED);
        registerReceiver(mReceiver, f);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mProgressRunner);
        unregisterReceiver(mReceiver);
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onClick(View v) {
        if (!mBound) return;
        int id = v.getId();
        if (id == R.id.btn_prev) {
            mService.prev();
        } else if (id == R.id.btn_play) {
            if (mService.isPlaying()) {
                mService.pause();
            } else {
                mService.play();
            }
            updatePlayButton();
        } else if (id == R.id.btn_next) {
            mService.next();
        } else if (id == R.id.btn_shuffle) {
            mService.setShuffle(!mService.getShuffle());
            updateShuffleRepeat();
        } else if (id == R.id.btn_repeat) {
            int mode = (mService.getRepeatMode() + 1) % 3;
            mService.setRepeatMode(mode);
            updateShuffleRepeat();
        }
    }

    private void updateUI() {
        if (!mBound) return;
        mTitle.setText(mService.getTitle());
        mArtist.setText(mService.getArtist());
        if (mAlbumText != null) {
            mAlbumText.setText(mService.getAlbum());
        }
        updatePlayButton();
        updateShuffleRepeat();
        loadAlbumArt(mService.getAlbumId());
        mLyricsLoaded = false;
        mCurrentLyrics = null;
        mLrcLines = null;
        mCurrentLrcIndex = -1;
        if (mLyricsVisible) {
            showPlainLyrics("Loading lyrics...");
            fetchAndShowLyrics();
        }
    }

    private void updatePlayButton() {
        if (!mBound) return;
        if (mService.isPlaying()) {
            mBtnPlay.setImageResource(R.drawable.btn_pause_selector);
        } else {
            mBtnPlay.setImageResource(R.drawable.btn_play_selector);
        }
    }

    private void updateShuffleRepeat() {
        if (!mBound) return;
        if (mShuffleBtn != null) {
            mShuffleBtn.setAlpha(mService.getShuffle() ? 255 : 80);
        }
        if (mRepeatBtn != null) {
            int mode = mService.getRepeatMode();
            if (mode == 2) {
                mRepeatBtn.setImageResource(R.drawable.music_playview_repeat_one);
            } else {
                mRepeatBtn.setImageResource(R.drawable.music_playview_repeat_all);
            }
            mRepeatBtn.setAlpha(mode > 0 ? 255 : 80);
        }
    }

    private void loadAlbumArt(long albumId) {
        if (albumId < 0 && mBound && mService.isJellyfinTrack()) {
            MusicService.JellyfinTrack jt = mService.getJellyfinTrack();
            if (jt != null) {
                String artId = jt.albumId != null ? jt.albumId : jt.jellyfinId;
                File jfArt = new File(Environment.getExternalStorageDirectory(),
                        ".walkman_art/jf_" + artId + ".jpg");
                if (jfArt.exists()) {
                    Bitmap bm = BitmapFactory.decodeFile(jfArt.getAbsolutePath());
                    if (bm != null) {
                        mAlbumArt.setImageBitmap(bm);
                        return;
                    }
                }
            }
            mAlbumArt.setImageResource(R.drawable.musicplayer_default_album);
            return;
        }
        if (albumId < 0) {
            mAlbumArt.setImageResource(R.drawable.musicplayer_default_album);
            return;
        }
        try {
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId);
            InputStream in = getContentResolver().openInputStream(artUri);
            if (in != null) {
                Bitmap bm = BitmapFactory.decodeStream(in);
                in.close();
                if (bm != null) {
                    mAlbumArt.setImageBitmap(bm);
                    return;
                }
            }
        } catch (Exception e) {
        }
        File artFile = new File(Environment.getExternalStorageDirectory(),
                ".walkman_art/" + albumId + ".jpg");
        if (artFile.exists()) {
            Bitmap bm = BitmapFactory.decodeFile(artFile.getAbsolutePath());
            if (bm != null) {
                mAlbumArt.setImageBitmap(bm);
                return;
            }
        }
        try {
            android.database.Cursor ac = getContentResolver().query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Albums.ALBUM_ART},
                    MediaStore.Audio.Albums._ID + "=?",
                    new String[]{String.valueOf(albumId)},
                    null);
            if (ac != null) {
                if (ac.moveToFirst()) {
                    String artPath = ac.getString(0);
                    if (artPath != null) {
                        Bitmap bm = BitmapFactory.decodeFile(artPath);
                        if (bm != null) {
                            ac.close();
                            mAlbumArt.setImageBitmap(bm);
                            return;
                        }
                    }
                }
                ac.close();
            }
        } catch (Exception e) {
        }
        mAlbumArt.setImageResource(R.drawable.musicplayer_default_album);
    }

    private void startProgressUpdates() {
        mHandler.removeCallbacks(mProgressRunner);
        mHandler.post(mProgressRunner);
    }

    private String formatTime(int ms) {
        int secs = ms / 1000;
        int mins = secs / 60;
        secs = secs % 60;
        return String.format("%d:%02d", mins, secs);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SHUFFLE, 0, "Shuffle")
                .setIcon(R.drawable.music_menu_shuffle);
        menu.add(0, MENU_REPEAT, 1, "Repeat")
                .setIcon(R.drawable.music_menu_repeat);
        menu.add(0, MENU_EDIT_INFO, 2, "Edit Info")
                .setIcon(R.drawable.music_menu_edit_music_info);
        menu.add(0, MENU_LYRICS, 3, "Lyrics")
                .setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_FAVOURITE, 4, "Favourite")
                .setIcon(R.drawable.music_playview_star);
        menu.add(0, MENU_ADD_PLAYLIST, 5, "Add to Playlist")
                .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, MENU_DOWNLOAD, 4, "Download")
                .setIcon(android.R.drawable.stat_sys_download);
        menu.add(0, MENU_GO_ALBUM, 5, "Go to Album")
                .setIcon(android.R.drawable.ic_menu_recent_history);
        menu.add(0, MENU_QUEUE, 6, "View Queue")
                .setIcon(R.drawable.music_playview_playqueue);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mBound) return false;
        switch (item.getItemId()) {
            case MENU_SHUFFLE:
                mService.setShuffle(!mService.getShuffle());
                updateShuffleRepeat();
                return true;
            case MENU_REPEAT:
                mService.setRepeatMode((mService.getRepeatMode() + 1) % 3);
                updateShuffleRepeat();
                return true;
            case MENU_EDIT_INFO: {
                Intent ei = new Intent(this, EditInfoActivity.class);
                ei.putExtra("track_id", mService.getCurrentTrackId());
                startActivityForResult(ei, 1);
                return true;
            }
            case MENU_LYRICS:
                toggleLyrics();
                return true;
            case MENU_FAVOURITE:
                toggleFavourite();
                return true;
            case MENU_ADD_PLAYLIST:
                showAddToPlaylistDialog();
                return true;
            case MENU_DOWNLOAD:
                downloadCurrentTrack();
                return true;
            case MENU_GO_ALBUM: {
                if (mService.isJellyfinTrack()) {
                    MusicService.JellyfinTrack jt = mService.getJellyfinTrack();
                    if (jt != null && jt.albumId != null) {
                        Intent ji = new Intent(this, JellyfinActivity.class);
                        ji.putExtra("open_album_id", jt.albumId);
                        ji.putExtra("open_album_name", jt.album);
                        startActivity(ji);
                    }
                }
                return true;
            }
            case MENU_QUEUE:
                showQueueDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isJellyfin = mBound && mService.isJellyfinTrack();

        MenuItem fav = menu.findItem(MENU_FAVOURITE);
        MenuItem addPl = menu.findItem(MENU_ADD_PLAYLIST);
        MenuItem dl = menu.findItem(MENU_DOWNLOAD);
        MenuItem goAlbum = menu.findItem(MENU_GO_ALBUM);

        if (fav != null) fav.setVisible(!isJellyfin);
        if (addPl != null) addPl.setVisible(!isJellyfin);
        if (dl != null) dl.setVisible(isJellyfin);
        if (goAlbum != null) goAlbum.setVisible(isJellyfin);

        if (fav != null && !isJellyfin && mBound) {
            long trackId = mService.getCurrentTrackId();
            if (trackId > 0) {
                long favId = PlaylistUtils.getOrCreateFavourites(this);
                boolean isFav = favId >= 0 && PlaylistUtils.isInPlaylist(this, favId, trackId);
                fav.setIcon(isFav ? R.drawable.music_playview_star_on : R.drawable.music_playview_star);
                fav.setTitle(isFav ? "Unfavourite" : "Favourite");
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void toggleLyrics() {
        if (mLyricsVisible) {
            hideLyrics();
            return;
        }
        if (mLyricsLoaded) {
            if (mLrcLines != null) {
                showSyncedLyrics();
            } else if (mCurrentLyrics != null) {
                showPlainLyrics(mCurrentLyrics);
            } else {
                showPlainLyrics("No lyrics found");
            }
        } else {
            showPlainLyrics("Loading lyrics...");
            fetchAndShowLyrics();
        }
    }

    private void showPlainLyrics(String text) {
        mLyricsContainer.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        tv.setTextSize(16);
        tv.setLineSpacing(4, 1);
        mLyricsContainer.addView(tv);
        mLyricsScroll.setVisibility(View.VISIBLE);
        mAlbumArt.setVisibility(View.GONE);
        mLyricsScroll.scrollTo(0, 0);
        mLyricsVisible = true;
    }

    private void showSyncedLyrics() {
        mLyricsContainer.removeAllViews();
        mCurrentLrcIndex = -1;
        int pad = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < mLrcLines.size(); i++) {
            TextView tv = new TextView(this);
            tv.setText(mLrcLines.get(i).text);
            tv.setTextColor(getResources().getColor(R.color.text_tertiary));
            tv.setTextSize(16);
            tv.setPadding(0, pad, 0, pad);
            mLyricsContainer.addView(tv);
        }
        mLyricsScroll.setVisibility(View.VISIBLE);
        mAlbumArt.setVisibility(View.GONE);
        mLyricsScroll.scrollTo(0, 0);
        mLyricsVisible = true;
        if (mBound) {
            updateSyncedLyrics(mService.getCurrentPosition());
        }
    }

    private void updateSyncedLyrics(int positionMs) {
        if (mLrcLines == null || mLyricsContainer.getChildCount() == 0) return;
        int idx = LrcParser.findLineIndex(mLrcLines, positionMs);
        if (idx == mCurrentLrcIndex) return;

        if (mCurrentLrcIndex >= 0 && mCurrentLrcIndex < mLyricsContainer.getChildCount()) {
            TextView old = (TextView) mLyricsContainer.getChildAt(mCurrentLrcIndex);
            old.setTextColor(getResources().getColor(R.color.text_tertiary));
            old.setTypeface(null, Typeface.NORMAL);
        }

        mCurrentLrcIndex = idx;
        if (idx >= 0 && idx < mLyricsContainer.getChildCount()) {
            TextView cur = (TextView) mLyricsContainer.getChildAt(idx);
            cur.setTextColor(getResources().getColor(R.color.walkman_blue));
            cur.setTypeface(null, Typeface.BOLD);

            final int scrollY = cur.getTop() - mLyricsScroll.getHeight() / 3;
            mLyricsScroll.smoothScrollTo(0, Math.max(0, scrollY));
        }
    }

    private void hideLyrics() {
        mLyricsScroll.setVisibility(View.GONE);
        mAlbumArt.setVisibility(View.VISIBLE);
        mLyricsVisible = false;
    }

    private void fetchAndShowLyrics() {
        if (!mBound) return;
        final boolean isJellyfin = mService.isJellyfinTrack();
        final String title = mService.getTitle();
        final String artist = mService.getArtist();
        final String filePath = mService.getFilePath();
        final String jellyfinId = isJellyfin && mService.getJellyfinTrack() != null
                ? mService.getJellyfinTrack().jellyfinId : null;
        new Thread(new Runnable() {
            public void run() {
                if (isJellyfin && jellyfinId != null) {
                    JellyfinClient client = new JellyfinClient();
                    client.loadFromPrefs(NowPlayingActivity.this);
                    if (client.isConfigured()) {
                        JellyfinClient.LyricResult result = client.getLyrics(jellyfinId);
                        if (result != null && !result.lines.isEmpty()) {
                            if (result.synced) {
                                final ArrayList<LrcParser.LrcLine> parsed =
                                        new ArrayList<LrcParser.LrcLine>();
                                for (int i = 0; i < result.lines.size(); i++) {
                                    JellyfinClient.LyricLine ll = result.lines.get(i);
                                    parsed.add(new LrcParser.LrcLine(ll.startMs, ll.text));
                                }
                                mHandler.post(new Runnable() {
                                    public void run() {
                                        mLyricsLoaded = true;
                                        mLrcLines = parsed;
                                        mCurrentLyrics = null;
                                        if (mLyricsVisible) showSyncedLyrics();
                                    }
                                });
                                return;
                            } else {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < result.lines.size(); i++) {
                                    if (i > 0) sb.append('\n');
                                    sb.append(result.lines.get(i).text);
                                }
                                final String plain = sb.toString();
                                mHandler.post(new Runnable() {
                                    public void run() {
                                        mLyricsLoaded = true;
                                        mLrcLines = null;
                                        mCurrentLyrics = plain;
                                        if (mLyricsVisible) showPlainLyrics(plain);
                                    }
                                });
                                return;
                            }
                        }
                    }
                }

                if (!isJellyfin) {
                    String lrcContent = MetadataUtils.loadLrcFile(filePath);
                    if (lrcContent != null) {
                        final ArrayList<LrcParser.LrcLine> parsed = LrcParser.parse(lrcContent);
                        if (parsed.size() > 0) {
                            mHandler.post(new Runnable() {
                                public void run() {
                                    mLyricsLoaded = true;
                                    mLrcLines = parsed;
                                    mCurrentLyrics = null;
                                    if (mLyricsVisible) showSyncedLyrics();
                                }
                            });
                            return;
                        }
                    }
                }

                final String lyrics = MetadataUtils.fetchLyrics(title, artist);
                mHandler.post(new Runnable() {
                    public void run() {
                        mLyricsLoaded = true;
                        mLrcLines = null;
                        if (lyrics != null) {
                            mCurrentLyrics = lyrics;
                            if (mLyricsVisible) {
                                showPlainLyrics(lyrics);
                            }
                        } else {
                            mCurrentLyrics = null;
                            if (mLyricsVisible) {
                                showPlainLyrics("No lyrics found");
                            }
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            mNeedsRefresh = true;
            if (mBound) {
                mService.refreshTrackInfo();
                mNeedsRefresh = false;
                updateUI();
            }
        }
    }

    private void toggleFavourite() {
        if (!mBound) return;
        long trackId = mService.getCurrentTrackId();
        if (trackId <= 0) {
            Toast.makeText(this, "Cannot favourite this track", Toast.LENGTH_SHORT).show();
            return;
        }
        long favId = PlaylistUtils.getOrCreateFavourites(this);
        if (favId < 0) return;

        if (PlaylistUtils.isInPlaylist(this, favId, trackId)) {
            PlaylistUtils.removeFromPlaylist(this, favId, trackId);
            Toast.makeText(this, "Removed from Favourites", Toast.LENGTH_SHORT).show();
        } else {
            PlaylistUtils.addToPlaylist(this, favId, trackId);
            Toast.makeText(this, "Added to Favourites", Toast.LENGTH_SHORT).show();
        }
    }

    private void showQueueDialog() {
        if (!mBound) return;
        ArrayList<String[]> queue = mService.getQueueInfo();
        if (queue.isEmpty()) {
            Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        final int currentPos = mService.getQueuePosition();
        String[] items = new String[queue.size()];
        for (int i = 0; i < queue.size(); i++) {
            String prefix = (i == currentPos) ? "▶ " : "";
            items[i] = prefix + queue.get(i)[0] + " – " + queue.get(i)[1];
        }
        new AlertDialog.Builder(this)
                .setTitle("Queue (" + queue.size() + " tracks)")
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (mBound) {
                            mService.playQueueIndex(which);
                        }
                    }
                })
                .show();
    }

    private void downloadCurrentTrack() {
        if (!mBound || !mService.isJellyfinTrack()) return;
        final MusicService.JellyfinTrack jt = mService.getJellyfinTrack();
        if (jt == null || jt.streamUrl == null) {
            Toast.makeText(this, "Cannot download this track", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                String artist = jt.artist != null ? jt.artist : "Unknown";
                String album = jt.album != null ? jt.album : "Unknown";
                String name = jt.title != null ? jt.title : "track";
                artist = artist.replaceAll("[/\\\\:*?\"<>|]", "_");
                album = album.replaceAll("[/\\\\:*?\"<>|]", "_");
                name = name.replaceAll("[/\\\\:*?\"<>|]", "_");

                File dir = new File(Environment.getExternalStorageDirectory(),
                        "Music/Jellyfin/" + artist + "/" + album);
                final File outFile = new File(dir, name + ".mp3");
                dir.mkdirs();

                boolean ok = false;
                try {
                    java.net.HttpURLConnection conn =
                            (java.net.HttpURLConnection) new java.net.URL(jt.streamUrl).openConnection();
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);
                    if (conn.getResponseCode() == 200) {
                        InputStream in = conn.getInputStream();
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                        fos.close();
                        in.close();
                        ok = true;
                    }
                    conn.disconnect();
                } catch (Exception e) {
                }

                final boolean success = ok;
                mHandler.post(new Runnable() {
                    public void run() {
                        if (success) {
                            sendBroadcast(new Intent(
                                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    Uri.fromFile(outFile)));
                            Toast.makeText(NowPlayingActivity.this,
                                    "Downloaded", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(NowPlayingActivity.this,
                                    "Download failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void showAddToPlaylistDialog() {
        if (!mBound) return;
        final long trackId = mService.getCurrentTrackId();
        if (trackId <= 0) {
            Toast.makeText(this, "Cannot add this track to a playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        final ArrayList<String> names = new ArrayList<String>();
        final ArrayList<Long> ids = new ArrayList<Long>();

        long favId = PlaylistUtils.getOrCreateFavourites(this);
        if (favId >= 0) {
            names.add("Favourites");
            ids.add(favId);
        }

        Cursor c = getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Playlists._ID,
                        MediaStore.Audio.Playlists.NAME
                },
                null, null,
                MediaStore.Audio.Playlists.NAME + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                String name = c.getString(1);
                if ("Favourites".equals(name)) continue;
                ids.add(c.getLong(0));
                names.add(name);
            }
            c.close();
        }

        names.add("New playlist...");
        ids.add(-1L);

        String[] items = names.toArray(new String[names.size()]);
        new AlertDialog.Builder(this)
                .setTitle("Add to Playlist")
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        long plId = ids.get(which);
                        if (plId == -1) {
                            showCreatePlaylistAndAdd(trackId);
                        } else {
                            PlaylistUtils.addToPlaylist(NowPlayingActivity.this, plId, trackId);
                            Toast.makeText(NowPlayingActivity.this,
                                    "Added to " + names.get(which), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void showCreatePlaylistAndAdd(final long trackId) {
        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("New Playlist")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) return;
                        long plId = PlaylistUtils.createPlaylist(NowPlayingActivity.this, name);
                        if (plId >= 0) {
                            PlaylistUtils.addToPlaylist(NowPlayingActivity.this, plId, trackId);
                            Toast.makeText(NowPlayingActivity.this,
                                    "Added to " + name, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
