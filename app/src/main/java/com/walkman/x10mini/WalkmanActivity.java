package com.walkman.x10mini;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;
import android.content.ContentValues;
import android.content.SharedPreferences;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class WalkmanActivity extends ListActivity {
    private static final int MENU_NOW_PLAYING = 1;
    private static final int MENU_SHUFFLE_ALL = 2;
    private static final int MENU_DOWNLOAD_ALL = 3;
    private static final int MENU_SEARCH = 4;
    private static final int MENU_VIEW_SONGS = 10;
    private static final int MENU_VIEW_ALBUMS = 11;
    private static final int MENU_VIEW_ARTISTS = 12;
    private static final int MENU_VIEW_PLAYLISTS = 13;
    private static final int MENU_VIEW_SEARCH = 14;
    private static final int MENU_JELLYFIN = 15;

    private static final int RESULT_TYPE_HEADER = 0;
    private static final int RESULT_TYPE_TRACK = 1;
    private static final int RESULT_TYPE_ALBUM = 2;
    private static final int RESULT_TYPE_ARTIST = 3;

    private static final int CTX_PLAY = 20;
    private static final int CTX_EDIT_INFO = 21;
    private static final int CTX_ADD_PLAYLIST = 22;
    private static final int CTX_SET_RINGTONE = 23;
    private static final int CTX_DELETE = 24;
    private static final int CTX_PLAY_NEXT = 25;
    private static final int CTX_ADD_QUEUE = 26;
    private static final int CTX_PLAY_NEXT_ALBUM = 27;
    private static final int CTX_ADD_QUEUE_ALBUM = 28;
    private static final int CTX_PLAY_NEXT_ARTIST = 29;
    private static final int CTX_ADD_QUEUE_ARTIST = 30;

    private static final int PL_TYPE_CREATE = 0;
    private static final int PL_TYPE_FAVOURITES = 1;
    private static final int PL_TYPE_RECENT = 2;
    private static final int PL_TYPE_FREQUENT = 3;
    private static final int PL_TYPE_ADDED = 4;
    private static final int PL_TYPE_USER = 5;

    private MusicService mService;
    private boolean mBound = false;
    private boolean mNeedsRefresh = false;
    private int mCurrentView = MENU_VIEW_SONGS;
    private Cursor mCursor;
    private TextView mNowPlayingBar;
    private LinearLayout mNowPlayingContainer;
    private View mNowPlayingBorder;
    private ImageView mNowPlayingIcon;
    private ImageView mNowPlayingArt;
    private TextView mTabArtists;
    private TextView mTabAlbums;
    private TextView mTabTracks;
    private LinearLayout mSearchBar;
    private EditText mSearchEdit;
    private int mPreSearchView = MENU_VIEW_SONGS;
    private ArrayList<SearchResult> mSearchResults;
    private Handler mSearchHandler = new Handler();
    private Runnable mSearchRunnable;
    private boolean mSearchVisible = false;
    private boolean mIsPlaylistTracks = false;
    private ArrayList<Long> mSmartPlaylistIds = null;
    private ArrayList<PlaylistItem> mPlaylistItems;

    private static final int SORT_NAME = 0;
    private static final int SORT_YEAR = 1;
    private static final int SORT_ARTIST = 2;
    private int mAlbumSortField = SORT_NAME;
    private boolean mAlbumSortAsc = true;
    private boolean mAlbumGridView = false;
    private View mFilterBar;
    private TextView mSortLabel;
    private TextView mSortDirection;
    private TextView mViewToggle;
    private GridView mGridView;
    private View mAlbumHeader;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((MusicService.MusicBinder) binder).getService();
            mBound = true;
            if (mNeedsRefresh) {
                mService.refreshTrackInfo();
                mNeedsRefresh = false;
            }
            updateNowPlayingBar();
        }
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            updateNowPlayingBar();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNowPlayingBar = (TextView) findViewById(R.id.now_playing_bar);
        mNowPlayingContainer = (LinearLayout) findViewById(R.id.now_playing_container);
        mNowPlayingBorder = findViewById(R.id.now_playing_border);
        mNowPlayingIcon = (ImageView) findViewById(R.id.now_playing_icon);
        mNowPlayingArt = (ImageView) findViewById(R.id.now_playing_art);
        mTabArtists = (TextView) findViewById(R.id.tab_artists);
        mTabAlbums = (TextView) findViewById(R.id.tab_albums);
        mTabTracks = (TextView) findViewById(R.id.tab_tracks);

        if (mNowPlayingContainer != null) {
            mNowPlayingContainer.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(WalkmanActivity.this, NowPlayingActivity.class));
                }
            });
        }

        if (mNowPlayingIcon != null) {
            mNowPlayingIcon.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!mBound) return;
                    if (mService.isPlaying()) {
                        mService.pause();
                    } else {
                        mService.play();
                    }
                    updateNowPlayingBar();
                }
            });
        }

        if (mTabArtists != null) {
            mTabArtists.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    loadArtistsList();
                    updateTabs();
                }
            });
        }
        if (mTabAlbums != null) {
            mTabAlbums.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    loadAlbumsList();
                    updateTabs();
                }
            });
        }
        if (mTabTracks != null) {
            mTabTracks.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    loadSongsList();
                    updateTabs();
                }
            });
        }

        mSearchBar = (LinearLayout) findViewById(R.id.search_bar);
        mSearchEdit = (EditText) findViewById(R.id.search_edit);
        TextView searchClear = (TextView) findViewById(R.id.search_clear);

        if (searchClear != null) {
            searchClear.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    hideSearchBar();
                }
            });
        }

        if (mSearchEdit != null) {
            mSearchEdit.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(android.text.Editable s) {
                    final String query = s.toString().trim();
                    if (mSearchRunnable != null) {
                        mSearchHandler.removeCallbacks(mSearchRunnable);
                    }
                    if (query.length() < 2) {
                        if (query.length() == 0) {
                            mSearchResults = new ArrayList<SearchResult>();
                            setListAdapter(new SearchAdapter(mSearchResults));
                        }
                        return;
                    }
                    mSearchRunnable = new Runnable() {
                        public void run() {
                            performSearch(query);
                        }
                    };
                    mSearchHandler.postDelayed(mSearchRunnable, 300);
                }
            });
        }

        mFilterBar = findViewById(R.id.filter_bar);
        mSortLabel = (TextView) findViewById(R.id.sort_label);
        mSortDirection = (TextView) findViewById(R.id.sort_direction);
        mViewToggle = (TextView) findViewById(R.id.view_toggle);
        mGridView = (GridView) findViewById(R.id.album_grid);

        SharedPreferences ap = getSharedPreferences("album_prefs", MODE_PRIVATE);
        mAlbumSortField = ap.getInt("sort_field", SORT_NAME);
        mAlbumSortAsc = ap.getBoolean("sort_asc", true);
        mAlbumGridView = ap.getBoolean("grid_view", true);

        if (mSortLabel != null) {
            mSortLabel.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mAlbumSortField = (mAlbumSortField + 1) % 3;
                    saveAlbumPrefs();
                    loadAlbumsList();
                }
            });
        }
        if (mSortDirection != null) {
            mSortDirection.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mAlbumSortAsc = !mAlbumSortAsc;
                    saveAlbumPrefs();
                    loadAlbumsList();
                }
            });
        }
        if (mViewToggle != null) {
            mViewToggle.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mAlbumGridView = !mAlbumGridView;
                    saveAlbumPrefs();
                    loadAlbumsList();
                }
            });
        }
        if (mGridView != null) {
            mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    loadAlbumTracks(id);
                }
            });
        }

        registerForContextMenu(getListView());
        if (mGridView != null) {
            registerForContextMenu(mGridView);
        }
        getListView().setFastScrollEnabled(true);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            handleViewIntent(intent);
        } else {
            loadSongsList();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent si = new Intent(this, MusicService.class);
        startService(si);
        bindService(si, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter f = new IntentFilter();
        f.addAction(MusicService.META_CHANGED);
        f.addAction(MusicService.PLAYSTATE_CHANGED);
        registerReceiver(mReceiver, f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCurrentView == MENU_VIEW_ALBUMS && mAlbumGridView) {
            findViewById(android.R.id.empty).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        if (mSearchRunnable != null) {
            mSearchHandler.removeCallbacks(mSearchRunnable);
        }
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
        super.onDestroy();
    }

    private void handleViewIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null && mBound) {
            ArrayList<Long> queue = new ArrayList<Long>();
            long id = ContentUris.parseId(data);
            if (id > 0) {
                queue.add(id);
                mService.setQueue(queue, 0);
                startActivity(new Intent(this, NowPlayingActivity.class));
            }
        }
    }

    private void loadSongsList() {
        mCurrentView = MENU_VIEW_SONGS;
        mIsPlaylistTracks = false;
        mSmartPlaylistIds = null;
        hideFilterBar();
        removeAlbumHeader();
        setTitle("Walkman - Songs");
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        mCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST
                },
                MediaStore.Audio.Media.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.Media.TITLE + " ASC");

        if (mCursor != null) {
            startManagingCursor(mCursor);
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    R.layout.list_item_track,
                    mCursor,
                    new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST},
                    new int[]{R.id.track_title, R.id.track_artist});
            setListAdapter(adapter);
        }
    }

    private void loadAlbumsList() {
        mCurrentView = MENU_VIEW_ALBUMS;
        mIsPlaylistTracks = false;
        mSmartPlaylistIds = null;
        removeAlbumHeader();
        setTitle("Walkman - Albums");
        if (mGridView != null) mGridView.setAdapter(null);
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        showFilterBar();

        String sortCol;
        switch (mAlbumSortField) {
            case SORT_YEAR: sortCol = MediaStore.Audio.Albums.FIRST_YEAR; break;
            case SORT_ARTIST: sortCol = MediaStore.Audio.Albums.ARTIST; break;
            default: sortCol = MediaStore.Audio.Albums.ALBUM; break;
        }
        String orderBy = sortCol + (mAlbumSortAsc ? " ASC" : " DESC");

        mCursor = getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Albums._ID,
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.ARTIST
                },
                null, null, orderBy);

        if (mCursor != null) {
            if (mAlbumGridView) {
                getListView().setVisibility(View.GONE);
                findViewById(android.R.id.empty).setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
                mGridView.setAdapter(new AlbumGridAdapter(mCursor));
            } else {
                startManagingCursor(mCursor);
                mGridView.setVisibility(View.GONE);
                getListView().setVisibility(View.VISIBLE);
                SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                        R.layout.list_item_album,
                        mCursor,
                        new String[]{MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST},
                        new int[]{R.id.track_title, R.id.track_artist}) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        ImageView art = (ImageView) v.findViewById(R.id.album_art);
                        if (art != null) {
                            Cursor c = (Cursor) getItem(position);
                            long albumId = c.getLong(0);
                            loadAlbumArtInto(art, albumId);
                        }
                        return v;
                    }
                };
                setListAdapter(adapter);
            }
        }
    }

    private void loadAlbumArtInto(ImageView iv, long albumId) {
        try {
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId);
            InputStream in = getContentResolver().openInputStream(artUri);
            if (in != null) {
                Bitmap bm = BitmapFactory.decodeStream(in);
                in.close();
                if (bm != null) {
                    iv.setImageBitmap(bm);
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
                iv.setImageBitmap(bm);
                return;
            }
        }
        iv.setImageResource(R.drawable.musicplayer_default_album);
    }

    private void loadArtistsList() {
        mCurrentView = MENU_VIEW_ARTISTS;
        hideFilterBar();
        removeAlbumHeader();
        setTitle("Walkman - Artists");
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        mCursor = getContentResolver().query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Artists._ID,
                        MediaStore.Audio.Artists.ARTIST,
                        MediaStore.Audio.Artists.NUMBER_OF_TRACKS
                },
                null, null,
                MediaStore.Audio.Artists.ARTIST + " ASC");

        if (mCursor != null) {
            startManagingCursor(mCursor);
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    R.layout.list_item_track,
                    mCursor,
                    new String[]{MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.NUMBER_OF_TRACKS},
                    new int[]{R.id.track_title, R.id.track_artist});
            setListAdapter(adapter);
        }
    }

    private void loadPlaylistsList() {
        mCurrentView = MENU_VIEW_PLAYLISTS;
        mIsPlaylistTracks = false;
        mSmartPlaylistIds = null;
        removeAlbumHeader();
        hideFilterBar();
        setTitle("Walkman - Playlists");
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();
        mCursor = null;

        mPlaylistItems = new ArrayList<PlaylistItem>();
        mPlaylistItems.add(new PlaylistItem(PL_TYPE_CREATE, -1,
                "Create playlist", null, R.drawable.music_playlists_add_normal));
        mPlaylistItems.add(new PlaylistItem(PL_TYPE_FAVOURITES, -1,
                "Favourites", null, R.drawable.music_playlists_list_favourites));
        mPlaylistItems.add(new PlaylistItem(PL_TYPE_RECENT, -1,
                "Recently played", null, R.drawable.music_playlists_list_most_played));
        mPlaylistItems.add(new PlaylistItem(PL_TYPE_FREQUENT, -1,
                "Most played", null, R.drawable.music_playlists_list_most_played));
        mPlaylistItems.add(new PlaylistItem(PL_TYPE_ADDED, -1,
                "Recently added", null, R.drawable.music_playlists_list_newly_added));

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
                mPlaylistItems.add(new PlaylistItem(PL_TYPE_USER, c.getLong(0),
                        name, null, R.drawable.music_playlists_list_custom));
            }
            c.close();
        }

        setListAdapter(new PlaylistAdapter(mPlaylistItems));
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (mCurrentView == MENU_VIEW_SEARCH) {
            handleSearchResultClick(position);
            return;
        }
        if (mCurrentView == MENU_VIEW_SONGS) {
            int headerCount = getListView().getHeaderViewsCount();
            playSongsFromPosition(position - headerCount);
        } else if (mCurrentView == MENU_VIEW_ALBUMS) {
            loadAlbumTracks(id);
        } else if (mCurrentView == MENU_VIEW_ARTISTS) {
            loadArtistTracks(id);
        } else if (mCurrentView == MENU_VIEW_PLAYLISTS) {
            handlePlaylistClick(position);
        }
    }

    private void handlePlaylistClick(int position) {
        if (mPlaylistItems == null || position >= mPlaylistItems.size()) return;
        PlaylistItem item = mPlaylistItems.get(position);
        switch (item.type) {
            case PL_TYPE_CREATE:
                showCreatePlaylistDialog();
                break;
            case PL_TYPE_FAVOURITES: {
                long favId = PlaylistUtils.getOrCreateFavourites(this);
                if (favId >= 0) loadPlaylistTracks(favId);
                break;
            }
            case PL_TYPE_RECENT:
                if (!mBound) return;
                loadTracksById(mService.getRecentlyPlayed(50), "Recently played");
                break;
            case PL_TYPE_FREQUENT:
                if (!mBound) return;
                loadTracksById(mService.getFrequentlyPlayed(50), "Most played");
                break;
            case PL_TYPE_ADDED:
                loadRecentlyAdded();
                break;
            case PL_TYPE_USER:
                loadPlaylistTracks(item.id);
                break;
        }
    }

    private void playSongsFromPosition(int position) {
        if (!mBound) return;
        if (mSmartPlaylistIds != null) {
            if (mSmartPlaylistIds.isEmpty()) return;
            mService.setQueue(mSmartPlaylistIds, position);
            startActivity(new Intent(this, NowPlayingActivity.class));
            return;
        }
        if (mCursor == null) return;
        ArrayList<Long> ids = new ArrayList<Long>();
        int col = mIsPlaylistTracks ? 1 : 0;
        mCursor.moveToPosition(-1);
        while (mCursor.moveToNext()) {
            ids.add(mCursor.getLong(col));
        }
        mService.setQueue(ids, position);
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    private void loadAlbumTracks(long albumId) {
        mCurrentView = MENU_VIEW_SONGS;
        mIsPlaylistTracks = false;
        mSmartPlaylistIds = null;
        hideFilterBar();
        removeAlbumHeader();
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        String albumName = null;
        String albumArtist = null;
        int albumYear = 0;
        Cursor ac = getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.ARTIST,
                        MediaStore.Audio.Albums.FIRST_YEAR
                },
                MediaStore.Audio.Albums._ID + "=?",
                new String[]{String.valueOf(albumId)}, null);
        if (ac != null) {
            if (ac.moveToFirst()) {
                albumName = ac.getString(0);
                albumArtist = ac.getString(1);
                albumYear = ac.getInt(2);
            }
            ac.close();
        }

        mAlbumHeader = getLayoutInflater().inflate(R.layout.album_header, null);
        ((TextView) mAlbumHeader.findViewById(R.id.header_album_name))
                .setText(albumName != null ? albumName : "Unknown Album");
        ((TextView) mAlbumHeader.findViewById(R.id.header_album_artist))
                .setText(albumArtist != null ? albumArtist : "Unknown Artist");
        TextView yearView = (TextView) mAlbumHeader.findViewById(R.id.header_album_year);
        if (albumYear > 0) {
            yearView.setText(String.valueOf(albumYear));
        } else {
            yearView.setVisibility(View.GONE);
        }
        loadAlbumArtInto(
                (ImageView) mAlbumHeader.findViewById(R.id.header_album_art), albumId);
        setListAdapter(null);
        getListView().addHeaderView(mAlbumHeader, null, false);

        mCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST
                },
                MediaStore.Audio.Media.ALBUM_ID + "=? AND " +
                        MediaStore.Audio.Media.IS_MUSIC + "=1",
                new String[]{String.valueOf(albumId)},
                MediaStore.Audio.Media.TRACK + " ASC");

        if (mCursor != null) {
            startManagingCursor(mCursor);
            setTitle(albumName != null ? albumName : "Album Tracks");
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    R.layout.list_item_track,
                    mCursor,
                    new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST},
                    new int[]{R.id.track_title, R.id.track_artist});
            setListAdapter(adapter);
        }
    }

    private void removeAlbumHeader() {
        if (mAlbumHeader != null) {
            getListView().removeHeaderView(mAlbumHeader);
            mAlbumHeader = null;
        }
    }

    private void loadArtistTracks(long artistId) {
        mCurrentView = MENU_VIEW_SONGS;
        mIsPlaylistTracks = false;
        mSmartPlaylistIds = null;
        hideFilterBar();
        removeAlbumHeader();
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        mCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST
                },
                MediaStore.Audio.Media.ARTIST_ID + "=? AND " +
                        MediaStore.Audio.Media.IS_MUSIC + "=1",
                new String[]{String.valueOf(artistId)},
                MediaStore.Audio.Media.TITLE + " ASC");

        if (mCursor != null) {
            startManagingCursor(mCursor);
            setTitle("Walkman - Artist Tracks");
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    R.layout.list_item_track,
                    mCursor,
                    new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST},
                    new int[]{R.id.track_title, R.id.track_artist});
            setListAdapter(adapter);
        }
    }

    private void loadPlaylistTracks(long playlistId) {
        mCurrentView = MENU_VIEW_SONGS;
        mIsPlaylistTracks = true;
        mSmartPlaylistIds = null;
        hideFilterBar();
        removeAlbumHeader();
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
        mCursor = getContentResolver().query(uri,
                new String[]{
                        MediaStore.Audio.Playlists.Members._ID,
                        MediaStore.Audio.Playlists.Members.AUDIO_ID,
                        MediaStore.Audio.Playlists.Members.TITLE,
                        MediaStore.Audio.Playlists.Members.ARTIST
                },
                null, null,
                MediaStore.Audio.Playlists.Members.PLAY_ORDER + " ASC");

        if (mCursor != null) {
            startManagingCursor(mCursor);
            setTitle("Walkman - Playlist");
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    R.layout.list_item_track,
                    mCursor,
                    new String[]{MediaStore.Audio.Playlists.Members.TITLE,
                            MediaStore.Audio.Playlists.Members.ARTIST},
                    new int[]{R.id.track_title, R.id.track_artist});
            setListAdapter(adapter);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SEARCH, 0, "Search")
                .setIcon(android.R.drawable.ic_menu_search);
        menu.add(0, MENU_NOW_PLAYING, 1, "Now Playing")
                .setIcon(android.R.drawable.ic_media_play);
        menu.add(0, MENU_SHUFFLE_ALL, 2, "Shuffle All")
                .setIcon(R.drawable.music_menu_shuffle);
        menu.add(0, MENU_JELLYFIN, 3, "Jellyfin")
                .setIcon(android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_DOWNLOAD_ALL, 4, "Download All Info")
                .setIcon(R.drawable.music_menu_get_music_info);
        menu.add(1, MENU_VIEW_PLAYLISTS, 10, "Playlists")
                .setIcon(android.R.drawable.ic_menu_recent_history);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SEARCH:
                if (mSearchVisible) {
                    hideSearchBar();
                } else {
                    showSearchBar();
                }
                return true;
            case MENU_NOW_PLAYING:
                startActivity(new Intent(this, NowPlayingActivity.class));
                return true;
            case MENU_SHUFFLE_ALL:
                shuffleAll();
                return true;
            case MENU_DOWNLOAD_ALL:
                startService(new Intent(this, MetadataService.class));
                android.widget.Toast.makeText(this,
                        "Downloading info for all tracks...",
                        android.widget.Toast.LENGTH_LONG).show();
                return true;
            case MENU_VIEW_PLAYLISTS:
                loadPlaylistsList();
                return true;
            case MENU_JELLYFIN:
                android.content.SharedPreferences jfPrefs =
                        getSharedPreferences("jellyfin", MODE_PRIVATE);
                if (jfPrefs.getString("access_token", null) != null) {
                    startActivity(new Intent(this, JellyfinActivity.class));
                } else {
                    startActivity(new Intent(this, JellyfinSettingsActivity.class));
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                     ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (mCurrentView == MENU_VIEW_SEARCH) return;
        if (mCurrentView == MENU_VIEW_SONGS) {
            menu.setHeaderTitle("Track Options");
            menu.add(0, CTX_PLAY, 0, "Play")
                    .setIcon(android.R.drawable.ic_media_play);
            menu.add(0, CTX_PLAY_NEXT, 1, "Play Next");
            menu.add(0, CTX_ADD_QUEUE, 2, "Add to Queue");
            menu.add(0, CTX_EDIT_INFO, 3, "Edit Info")
                    .setIcon(R.drawable.music_menu_edit_music_info);
            menu.add(0, CTX_ADD_PLAYLIST, 4, "Add to Playlist");
            menu.add(0, CTX_SET_RINGTONE, 5, "Set as Ringtone");
            menu.add(0, CTX_DELETE, 6, "Delete")
                    .setIcon(R.drawable.music_menu_delete_playlist);
        } else if (mCurrentView == MENU_VIEW_ALBUMS) {
            menu.setHeaderTitle("Album Options");
            menu.add(0, CTX_PLAY_NEXT_ALBUM, 0, "Play Next");
            menu.add(0, CTX_ADD_QUEUE_ALBUM, 1, "Add to Queue");
        } else if (mCurrentView == MENU_VIEW_ARTISTS) {
            menu.setHeaderTitle("Artist Options");
            menu.add(0, CTX_PLAY_NEXT_ARTIST, 0, "Play Next");
            menu.add(0, CTX_ADD_QUEUE_ARTIST, 1, "Add to Queue");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null) return false;

        final long trackId = info.id;

        switch (item.getItemId()) {
            case CTX_PLAY: {
                if (mCursor != null) {
                    int hc = getListView().getHeaderViewsCount();
                    int pos = info.position - hc;
                    mCursor.moveToPosition(pos);
                    playSongsFromPosition(pos);
                }
                return true;
            }
            case CTX_PLAY_NEXT: {
                if (mBound) {
                    mService.addToQueueNext(trackId);
                    Toast.makeText(this, "Playing next", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            case CTX_ADD_QUEUE: {
                if (mBound) {
                    mService.addToQueue(trackId);
                    Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            case CTX_EDIT_INFO: {
                Intent ei = new Intent(this, EditInfoActivity.class);
                ei.putExtra("track_id", trackId);
                startActivityForResult(ei, 1);
                return true;
            }
            case CTX_ADD_PLAYLIST: {
                showAddToPlaylistDialog(trackId);
                return true;
            }
            case CTX_SET_RINGTONE: {
                setAsRingtone(trackId);
                return true;
            }
            case CTX_DELETE: {
                confirmDelete(trackId);
                return true;
            }
            case CTX_PLAY_NEXT_ALBUM: {
                queueLocalAlbum(trackId, true);
                return true;
            }
            case CTX_ADD_QUEUE_ALBUM: {
                queueLocalAlbum(trackId, false);
                return true;
            }
            case CTX_PLAY_NEXT_ARTIST: {
                queueLocalArtist(trackId, true);
                return true;
            }
            case CTX_ADD_QUEUE_ARTIST: {
                queueLocalArtist(trackId, false);
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            mNeedsRefresh = true;
            if (mBound) {
                mService.refreshTrackInfo();
                mNeedsRefresh = false;
                updateNowPlayingBar();
            }
            if (mCurrentView == MENU_VIEW_SONGS) {
                loadSongsList();
            } else if (mCurrentView == MENU_VIEW_ALBUMS) {
                loadAlbumsList();
            }
        }
    }

    private void queueLocalAlbum(long albumId, boolean playNext) {
        if (!mBound) return;
        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.ALBUM_ID + "=? AND " +
                        MediaStore.Audio.Media.IS_MUSIC + "=1",
                new String[]{String.valueOf(albumId)},
                MediaStore.Audio.Media.TRACK + " ASC");
        if (c != null) {
            ArrayList<Long> ids = new ArrayList<Long>();
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
            c.close();
            if (playNext) {
                for (int i = ids.size() - 1; i >= 0; i--) {
                    mService.addToQueueNext(ids.get(i));
                }
                Toast.makeText(this, "Playing next", Toast.LENGTH_SHORT).show();
            } else {
                for (int i = 0; i < ids.size(); i++) {
                    mService.addToQueue(ids.get(i));
                }
                Toast.makeText(this, "Added " + ids.size() + " tracks to queue",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void queueLocalArtist(long artistId, boolean playNext) {
        if (!mBound) return;
        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.ARTIST_ID + "=? AND " +
                        MediaStore.Audio.Media.IS_MUSIC + "=1",
                new String[]{String.valueOf(artistId)},
                MediaStore.Audio.Media.TITLE + " ASC");
        if (c != null) {
            ArrayList<Long> ids = new ArrayList<Long>();
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
            c.close();
            if (playNext) {
                for (int i = ids.size() - 1; i >= 0; i--) {
                    mService.addToQueueNext(ids.get(i));
                }
                Toast.makeText(this, "Playing next", Toast.LENGTH_SHORT).show();
            } else {
                for (int i = 0; i < ids.size(); i++) {
                    mService.addToQueue(ids.get(i));
                }
                Toast.makeText(this, "Added " + ids.size() + " tracks to queue",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shuffleAll() {
        if (!mBound) return;
        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.IS_MUSIC + "=1",
                null, null);
        if (c != null) {
            ArrayList<Long> ids = new ArrayList<Long>();
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
            c.close();
            java.util.Collections.shuffle(ids);
            if (!ids.isEmpty()) {
                mService.setQueue(ids, 0);
                startActivity(new Intent(this, NowPlayingActivity.class));
            }
        }
    }

    private void setAsRingtone(long id) {
        Uri uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        android.media.RingtoneManager.setActualDefaultRingtoneUri(
                this, android.media.RingtoneManager.TYPE_RINGTONE, uri);
        android.widget.Toast.makeText(this, "Set as ringtone", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(final long id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Track")
                .setMessage("Delete this track from device?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        getContentResolver().delete(
                                ContentUris.withAppendedId(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                null, null);
                        loadSongsList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTabs() {
        if (mTabArtists == null || mTabAlbums == null || mTabTracks == null) return;
        int activeColor = getResources().getColor(R.color.walkman_blue);
        int inactiveColor = getResources().getColor(R.color.text_tertiary);

        boolean isArtists = mCurrentView == MENU_VIEW_ARTISTS;
        boolean isAlbums = mCurrentView == MENU_VIEW_ALBUMS;
        boolean isTracks = !isArtists && !isAlbums;

        mTabArtists.setTextColor(isArtists ? activeColor : inactiveColor);
        mTabArtists.setTypeface(null, isArtists ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        mTabAlbums.setTextColor(isAlbums ? activeColor : inactiveColor);
        mTabAlbums.setTypeface(null, isAlbums ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        mTabTracks.setTextColor(isTracks ? activeColor : inactiveColor);
        mTabTracks.setTypeface(null, isTracks ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void updateNowPlayingBar() {
        if (mBound && mService.isPlaying()) {
            if (mNowPlayingBorder != null) mNowPlayingBorder.setVisibility(View.VISIBLE);
            mNowPlayingContainer.setVisibility(View.VISIBLE);
            mNowPlayingBar.setText(mService.getTitle() + " - " + mService.getArtist());
            if (mNowPlayingIcon != null) {
                mNowPlayingIcon.setImageResource(R.drawable.btn_pause_selector);
            }
            loadNowPlayingArt(mService.getAlbumId());
        } else if (mBound && mService.getTitle() != null && mService.getTitle().length() > 0) {
            if (mNowPlayingBorder != null) mNowPlayingBorder.setVisibility(View.VISIBLE);
            mNowPlayingContainer.setVisibility(View.VISIBLE);
            mNowPlayingBar.setText(mService.getTitle() + " - " + mService.getArtist());
            if (mNowPlayingIcon != null) {
                mNowPlayingIcon.setImageResource(R.drawable.btn_play_selector);
            }
            loadNowPlayingArt(mService.getAlbumId());
        } else {
            if (mNowPlayingBorder != null) mNowPlayingBorder.setVisibility(View.GONE);
            mNowPlayingContainer.setVisibility(View.GONE);
        }
    }

    private void loadNowPlayingArt(long albumId) {
        if (mNowPlayingArt == null) return;
        if (albumId < 0 && mBound && mService.isJellyfinTrack()) {
            MusicService.JellyfinTrack jt = mService.getJellyfinTrack();
            if (jt != null) {
                String artId = jt.albumId != null ? jt.albumId : jt.jellyfinId;
                File jfArt = new File(Environment.getExternalStorageDirectory(),
                        ".walkman_art/jf_" + artId + ".jpg");
                if (jfArt.exists()) {
                    Bitmap bm = BitmapFactory.decodeFile(jfArt.getAbsolutePath());
                    if (bm != null) {
                        mNowPlayingArt.setImageBitmap(bm);
                        return;
                    }
                }
            }
            mNowPlayingArt.setImageResource(R.drawable.musicplayer_default_album);
            return;
        }
        if (albumId < 0) {
            mNowPlayingArt.setImageResource(R.drawable.musicplayer_default_album);
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
                    mNowPlayingArt.setImageBitmap(bm);
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
                mNowPlayingArt.setImageBitmap(bm);
                return;
            }
        }
        try {
            Cursor ac = getContentResolver().query(
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
                            mNowPlayingArt.setImageBitmap(bm);
                            return;
                        }
                    }
                }
                ac.close();
            }
        } catch (Exception e) {
        }
        mNowPlayingArt.setImageResource(R.drawable.musicplayer_default_album);
    }

    @Override
    public void onBackPressed() {
        if (mSearchVisible) {
            hideSearchBar();
        } else if (mIsPlaylistTracks || mSmartPlaylistIds != null) {
            loadPlaylistsList();
        } else if (mCurrentView != MENU_VIEW_SONGS && mCurrentView != MENU_VIEW_ALBUMS
                && mCurrentView != MENU_VIEW_ARTISTS) {
            loadSongsList();
            updateTabs();
        } else {
            super.onBackPressed();
        }
    }

    // --- Search ---

    private void showSearchBar() {
        if (mSearchBar == null) return;
        mPreSearchView = mCurrentView;
        mCurrentView = MENU_VIEW_SEARCH;
        mSearchVisible = true;
        mSearchBar.setVisibility(View.VISIBLE);
        mSearchEdit.setText("");
        mSearchEdit.requestFocus();
        setTitle("Walkman - Search");
        mSearchResults = new ArrayList<SearchResult>();
        setListAdapter(new SearchAdapter(mSearchResults));
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mSearchEdit, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSearchBar() {
        if (mSearchBar == null) return;
        mSearchVisible = false;
        mSearchBar.setVisibility(View.GONE);
        mSearchEdit.setText("");
        mSearchResults = null;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchEdit.getWindowToken(), 0);
        }
        if (mSearchRunnable != null) {
            mSearchHandler.removeCallbacks(mSearchRunnable);
            mSearchRunnable = null;
        }
        switch (mPreSearchView) {
            case MENU_VIEW_ALBUMS:
                loadAlbumsList();
                break;
            case MENU_VIEW_ARTISTS:
                loadArtistsList();
                break;
            case MENU_VIEW_PLAYLISTS:
                loadPlaylistsList();
                break;
            default:
                loadSongsList();
                break;
        }
        updateTabs();
    }

    private void performSearch(String query) {
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
            mCursor = null;
        }

        ArrayList<SearchResult> results = new ArrayList<SearchResult>();
        String likePattern = "%" + query + "%";

        Cursor ac = getContentResolver().query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Artists._ID,
                        MediaStore.Audio.Artists.ARTIST,
                        MediaStore.Audio.Artists.NUMBER_OF_TRACKS
                },
                MediaStore.Audio.Artists.ARTIST + " LIKE ?",
                new String[]{likePattern},
                MediaStore.Audio.Artists.ARTIST + " ASC");
        if (ac != null) {
            if (ac.getCount() > 0) {
                results.add(new SearchResult(RESULT_TYPE_HEADER, 0, "ARTISTS", null));
                while (ac.moveToNext()) {
                    int n = ac.getInt(2);
                    results.add(new SearchResult(RESULT_TYPE_ARTIST, ac.getLong(0),
                            ac.getString(1), n + (n == 1 ? " track" : " tracks")));
                }
            }
            ac.close();
        }

        Cursor alc = getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Albums._ID,
                        MediaStore.Audio.Albums.ALBUM,
                        MediaStore.Audio.Albums.ARTIST
                },
                MediaStore.Audio.Albums.ALBUM + " LIKE ?",
                new String[]{likePattern},
                MediaStore.Audio.Albums.ALBUM + " ASC");
        if (alc != null) {
            if (alc.getCount() > 0) {
                results.add(new SearchResult(RESULT_TYPE_HEADER, 0, "ALBUMS", null));
                while (alc.moveToNext()) {
                    results.add(new SearchResult(RESULT_TYPE_ALBUM, alc.getLong(0),
                            alc.getString(1), alc.getString(2)));
                }
            }
            alc.close();
        }

        Cursor tc = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST
                },
                MediaStore.Audio.Media.IS_MUSIC + "=1 AND (" +
                        MediaStore.Audio.Media.TITLE + " LIKE ? OR " +
                        MediaStore.Audio.Media.ARTIST + " LIKE ?)",
                new String[]{likePattern, likePattern},
                MediaStore.Audio.Media.TITLE + " ASC");
        if (tc != null) {
            if (tc.getCount() > 0) {
                results.add(new SearchResult(RESULT_TYPE_HEADER, 0, "TRACKS", null));
                while (tc.moveToNext()) {
                    results.add(new SearchResult(RESULT_TYPE_TRACK, tc.getLong(0),
                            tc.getString(1), tc.getString(2)));
                }
            }
            tc.close();
        }

        mSearchResults = results;
        setListAdapter(new SearchAdapter(results));
    }

    private void handleSearchResultClick(int position) {
        if (mSearchResults == null || position >= mSearchResults.size()) return;
        SearchResult result = mSearchResults.get(position);

        if (result.type == RESULT_TYPE_TRACK) {
            if (!mBound) return;
            ArrayList<Long> trackIds = new ArrayList<Long>();
            int playIndex = 0;
            int trackCount = 0;
            for (int i = 0; i < mSearchResults.size(); i++) {
                SearchResult sr = mSearchResults.get(i);
                if (sr.type == RESULT_TYPE_TRACK) {
                    if (i == position) playIndex = trackCount;
                    trackIds.add(sr.id);
                    trackCount++;
                }
            }
            if (!trackIds.isEmpty()) {
                mService.setQueue(trackIds, playIndex);
                startActivity(new Intent(this, NowPlayingActivity.class));
            }
        } else if (result.type == RESULT_TYPE_ALBUM) {
            hideSearchBar();
            loadAlbumTracks(result.id);
        } else if (result.type == RESULT_TYPE_ARTIST) {
            hideSearchBar();
            loadArtistTracks(result.id);
        }
    }

    private static class SearchResult {
        int type;
        long id;
        String line1;
        String line2;

        SearchResult(int type, long id, String line1, String line2) {
            this.type = type;
            this.id = id;
            this.line1 = line1;
            this.line2 = line2;
        }
    }

    private class SearchAdapter extends BaseAdapter {
        private ArrayList<SearchResult> mResults;

        SearchAdapter(ArrayList<SearchResult> results) {
            mResults = results;
        }

        public int getCount() { return mResults.size(); }
        public SearchResult getItem(int pos) { return mResults.get(pos); }
        public long getItemId(int pos) { return mResults.get(pos).id; }
        public boolean isEnabled(int pos) { return mResults.get(pos).type != RESULT_TYPE_HEADER; }
        public int getViewTypeCount() { return 2; }
        public int getItemViewType(int pos) {
            return mResults.get(pos).type == RESULT_TYPE_HEADER ? 0 : 1;
        }

        public View getView(int pos, View convertView, ViewGroup parent) {
            SearchResult r = mResults.get(pos);
            if (r.type == RESULT_TYPE_HEADER) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(
                            R.layout.list_item_search_header, parent, false);
                }
                ((TextView) convertView.findViewById(R.id.header_title)).setText(r.line1);
            } else {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(
                            R.layout.list_item_track, parent, false);
                }
                ((TextView) convertView.findViewById(R.id.track_title)).setText(r.line1);
                ((TextView) convertView.findViewById(R.id.track_artist)).setText(
                        r.line2 != null ? r.line2 : "");
            }
            return convertView;
        }
    }

    // --- Playlist classes and methods ---

    private static class PlaylistItem {
        int type;
        long id;
        String name;
        String subtitle;
        int iconRes;

        PlaylistItem(int type, long id, String name, String subtitle, int iconRes) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
        }
    }

    private class PlaylistAdapter extends BaseAdapter {
        private ArrayList<PlaylistItem> mItems;

        PlaylistAdapter(ArrayList<PlaylistItem> items) {
            mItems = items;
        }

        public int getCount() { return mItems.size(); }
        public PlaylistItem getItem(int pos) { return mItems.get(pos); }
        public long getItemId(int pos) { return mItems.get(pos).id; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(
                        R.layout.list_item_playlist, parent, false);
            }
            PlaylistItem item = mItems.get(pos);
            ImageView icon = (ImageView) convertView.findViewById(R.id.playlist_icon);
            icon.setImageResource(item.iconRes);
            if (item.type != PL_TYPE_CREATE) {
                icon.setColorFilter(0xFFCCCCCC, android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                icon.setColorFilter(null);
            }
            ((TextView) convertView.findViewById(R.id.playlist_title))
                    .setText(item.name);
            TextView sub = (TextView) convertView.findViewById(R.id.playlist_subtitle);
            if (item.subtitle != null && item.subtitle.length() > 0) {
                sub.setText(item.subtitle);
                sub.setVisibility(View.VISIBLE);
            } else {
                sub.setVisibility(View.GONE);
            }
            return convertView;
        }
    }

    private void loadTracksById(ArrayList<Long> trackIds, String title) {
        mCurrentView = MENU_VIEW_SONGS;
        mIsPlaylistTracks = false;
        removeAlbumHeader();
        hideFilterBar();
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();
        mCursor = null;

        if (trackIds == null || trackIds.isEmpty()) {
            mSmartPlaylistIds = new ArrayList<Long>();
            setTitle("Walkman - " + title);
            setListAdapter(new SearchAdapter(new ArrayList<SearchResult>()));
            return;
        }

        mSmartPlaylistIds = trackIds;
        setTitle("Walkman - " + title);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trackIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(trackIds.get(i));
        }
        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST
                },
                MediaStore.Audio.Media._ID + " IN (" + sb.toString() + ")",
                null, null);

        HashMap<Long, String[]> trackMap = new HashMap<Long, String[]>();
        if (c != null) {
            while (c.moveToNext()) {
                trackMap.put(c.getLong(0), new String[]{c.getString(1), c.getString(2)});
            }
            c.close();
        }

        ArrayList<SearchResult> results = new ArrayList<SearchResult>();
        for (Long id : trackIds) {
            String[] info = trackMap.get(id);
            if (info != null) {
                results.add(new SearchResult(RESULT_TYPE_TRACK, id, info[0], info[1]));
            }
        }

        setListAdapter(new SearchAdapter(results));
    }

    private void loadRecentlyAdded() {
        mCurrentView = MENU_VIEW_SONGS;
        mIsPlaylistTracks = false;
        mSmartPlaylistIds = null;
        hideFilterBar();
        removeAlbumHeader();
        setTitle("Walkman - Recently added");
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();

        mCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST
                },
                MediaStore.Audio.Media.IS_MUSIC + "=1",
                null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC");

        if (mCursor != null) {
            startManagingCursor(mCursor);
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    R.layout.list_item_track,
                    mCursor,
                    new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST},
                    new int[]{R.id.track_title, R.id.track_artist});
            setListAdapter(adapter);
        }
    }

    private void showCreatePlaylistDialog() {
        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Create Playlist")
                .setView(input)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) {
                            Toast.makeText(WalkmanActivity.this,
                                    "Enter a name", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        long id = PlaylistUtils.createPlaylist(WalkmanActivity.this, name);
                        if (id >= 0) {
                            Toast.makeText(WalkmanActivity.this,
                                    "Playlist created", Toast.LENGTH_SHORT).show();
                            loadPlaylistsList();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddToPlaylistDialog(final long trackId) {
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
                            PlaylistUtils.addToPlaylist(WalkmanActivity.this, plId, trackId);
                            Toast.makeText(WalkmanActivity.this,
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
                        long plId = PlaylistUtils.createPlaylist(WalkmanActivity.this, name);
                        if (plId >= 0) {
                            PlaylistUtils.addToPlaylist(WalkmanActivity.this, plId, trackId);
                            Toast.makeText(WalkmanActivity.this,
                                    "Added to " + name, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Filter bar and grid view ---

    private void showFilterBar() {
        if (mFilterBar != null) mFilterBar.setVisibility(View.VISIBLE);
        String[] labels = {"Name", "Year", "Artist"};
        if (mSortLabel != null) mSortLabel.setText(labels[mAlbumSortField]);
        if (mSortDirection != null) mSortDirection.setText(mAlbumSortAsc ? " ▲" : " ▼");
        if (mViewToggle != null) mViewToggle.setText(mAlbumGridView ? "List" : "Grid");
    }

    private void hideFilterBar() {
        if (mFilterBar != null) mFilterBar.setVisibility(View.GONE);
        if (mGridView != null) mGridView.setVisibility(View.GONE);
        getListView().setVisibility(View.VISIBLE);
        findViewById(android.R.id.empty).setVisibility(View.GONE);
    }

    private void saveAlbumPrefs() {
        getSharedPreferences("album_prefs", MODE_PRIVATE).edit()
                .putInt("sort_field", mAlbumSortField)
                .putBoolean("sort_asc", mAlbumSortAsc)
                .putBoolean("grid_view", mAlbumGridView)
                .commit();
    }

    private class AlbumGridAdapter extends BaseAdapter {
        private Cursor mData;

        AlbumGridAdapter(Cursor data) {
            mData = data;
        }

        public int getCount() {
            return mData != null && !mData.isClosed() ? mData.getCount() : 0;
        }
        public Object getItem(int pos) {
            if (mData != null && !mData.isClosed() && mData.moveToPosition(pos)) {
                return mData;
            }
            return null;
        }
        public long getItemId(int pos) {
            if (mData != null && !mData.isClosed() && mData.moveToPosition(pos)) {
                return mData.getLong(0);
            }
            return -1;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(
                        R.layout.grid_item_album, parent, false);
            }
            if (mData == null || mData.isClosed() || !mData.moveToPosition(position)) {
                return convertView;
            }
            long albumId = mData.getLong(0);
            String album = mData.getString(1);
            String artist = mData.getString(2);

            ((TextView) convertView.findViewById(R.id.album_title)).setText(album);
            ((TextView) convertView.findViewById(R.id.album_artist)).setText(
                    artist != null ? artist : "");

            ImageView art = (ImageView) convertView.findViewById(R.id.album_art);
            loadAlbumArtInto(art, albumId);

            return convertView;
        }
    }
}
