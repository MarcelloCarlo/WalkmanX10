package com.walkman.x10mini;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class JellyfinActivity extends ListActivity {

    private static final int VIEW_ARTISTS = 0;
    private static final int VIEW_ALBUMS = 1;
    private static final int VIEW_TRACKS = 2;
    private static final int VIEW_ARTIST_ALBUMS = 3;
    private static final int VIEW_ALBUM_TRACKS = 4;
    private static final int VIEW_SEARCH = 5;

    private static final int MENU_SEARCH = 1;
    private static final int MENU_SERVER = 2;
    private static final int MENU_NOW_PLAYING = 3;
    private static final int MENU_AUDIO = 4;
    private static final int MENU_WALKMAN = 5;

    private static final int CTX_PLAY = 1;
    private static final int CTX_DOWNLOAD = 2;
    private static final int CTX_ADD_QUEUE = 3;
    private static final int CTX_DOWNLOAD_ALBUM = 4;
    private static final int CTX_DOWNLOAD_ARTIST = 5;
    private static final int CTX_QUEUE_ALBUM = 6;
    private static final int CTX_PLAY_NEXT = 7;
    private static final int CTX_PLAY_NEXT_ALBUM = 8;
    private static final int CTX_QUEUE_ARTIST = 9;
    private static final int CTX_PLAY_NEXT_ARTIST = 10;
    private static final int DOWNLOAD_NOTIFICATION_ID = 100;

    private JellyfinClient mClient;
    private MusicService mService;
    private boolean mBound = false;
    private int mCurrentView = VIEW_ARTISTS;
    private String mDrillArtistId;
    private String mDrillAlbumId;
    private String mDrillTitle;

    private TextView mTabArtists;
    private TextView mTabAlbums;
    private TextView mTabTracks;
    private LinearLayout mSearchBar;
    private EditText mSearchEdit;
    private TextView mLoadingText;
    private View mNowPlayingContainer;
    private View mNowPlayingBorder;
    private TextView mNowPlayingBar;
    private ImageView mNowPlayingArt;
    private ImageView mNowPlayingIcon;
    private Handler mHandler = new Handler();
    private Handler mSearchHandler = new Handler();
    private Runnable mSearchRunnable;
    private boolean mSearchVisible = false;

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

    private ArrayList<JellyfinClient.JellyfinItem> mItems = new ArrayList<JellyfinClient.JellyfinItem>();
    private ArrayList<JellyfinClient.JellyfinItem> mSearchResults = new ArrayList<JellyfinClient.JellyfinItem>();

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((MusicService.MusicBinder) binder).getService();
            mBound = true;
            updateNowPlayingBar();
        }
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) {
            updateNowPlayingBar();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jellyfin);

        mTabArtists = (TextView) findViewById(R.id.tab_artists);
        mTabAlbums = (TextView) findViewById(R.id.tab_albums);
        mTabTracks = (TextView) findViewById(R.id.tab_tracks);
        mSearchBar = (LinearLayout) findViewById(R.id.search_bar);
        mSearchEdit = (EditText) findViewById(R.id.search_edit);
        mLoadingText = (TextView) findViewById(R.id.loading_text);
        mNowPlayingContainer = findViewById(R.id.now_playing_container);
        mNowPlayingBorder = findViewById(R.id.now_playing_border);
        mNowPlayingBar = (TextView) findViewById(R.id.now_playing_bar);
        mNowPlayingArt = (ImageView) findViewById(R.id.now_playing_art);
        mNowPlayingIcon = (ImageView) findViewById(R.id.now_playing_icon);

        mClient = new JellyfinClient();
        mClient.loadFromPrefs(this);

        mTabArtists.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { loadArtists(); }
        });
        mTabAlbums.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { loadAlbums(null); }
        });
        mTabTracks.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { loadAllTracks(); }
        });

        TextView searchClear = (TextView) findViewById(R.id.search_clear);
        searchClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dismissSearch(); }
        });

        mSearchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                final String query = s.toString().trim();
                if (mSearchRunnable != null) mSearchHandler.removeCallbacks(mSearchRunnable);
                if (query.length() < 2) return;
                mSearchRunnable = new Runnable() {
                    public void run() { performSearch(query); }
                };
                mSearchHandler.postDelayed(mSearchRunnable, 400);
            }
        });

        mFilterBar = findViewById(R.id.filter_bar);
        mSortLabel = (TextView) findViewById(R.id.sort_label);
        mSortDirection = (TextView) findViewById(R.id.sort_direction);
        mViewToggle = (TextView) findViewById(R.id.view_toggle);
        mGridView = (GridView) findViewById(R.id.album_grid);

        SharedPreferences ap = getSharedPreferences("jf_album_prefs", MODE_PRIVATE);
        mAlbumSortField = ap.getInt("sort_field", SORT_NAME);
        mAlbumSortAsc = ap.getBoolean("sort_asc", true);
        mAlbumGridView = ap.getBoolean("grid_view", true);

        if (mSortLabel != null) {
            mSortLabel.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mAlbumSortField = (mAlbumSortField + 1) % 3;
                    saveAlbumPrefs();
                    resortAndRefreshAlbums();
                }
            });
        }
        if (mSortDirection != null) {
            mSortDirection.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mAlbumSortAsc = !mAlbumSortAsc;
                    saveAlbumPrefs();
                    resortAndRefreshAlbums();
                }
            });
        }
        if (mViewToggle != null) {
            mViewToggle.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mAlbumGridView = !mAlbumGridView;
                    saveAlbumPrefs();
                    resortAndRefreshAlbums();
                }
            });
        }
        if (mGridView != null) {
            mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    if (position < mItems.size()) {
                        JellyfinClient.JellyfinItem item = mItems.get(position);
                        if ("MusicAlbum".equals(item.type)) {
                            loadTracks(item.id, item.name, item.artist, item.year);
                        }
                    }
                }
            });
        }

        mNowPlayingContainer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(JellyfinActivity.this, NowPlayingActivity.class));
            }
        });

        mNowPlayingIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mBound) {
                    if (mService.isPlaying()) mService.pause();
                    else mService.play();
                    updateNowPlayingBar();
                }
            }
        });

        registerForContextMenu(getListView());
        if (mGridView != null) {
            registerForContextMenu(mGridView);
        }

        String openAlbumId = getIntent().getStringExtra("open_album_id");
        if (openAlbumId != null) {
            String openAlbumName = getIntent().getStringExtra("open_album_name");
            loadTracks(openAlbumId, openAlbumName != null ? openAlbumName : "Album");
        } else {
            loadArtists();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startService(new Intent(this, MusicService.class));
        bindService(new Intent(this, MusicService.class), mConnection, Context.BIND_AUTO_CREATE);
        IntentFilter f = new IntentFilter();
        f.addAction(MusicService.META_CHANGED);
        f.addAction(MusicService.PLAYSTATE_CHANGED);
        registerReceiver(mReceiver, f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClient.loadFromPrefs(this);
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
        super.onDestroy();
        if (mSearchRunnable != null) mSearchHandler.removeCallbacks(mSearchRunnable);
    }

    private void loadArtists() {
        mCurrentView = VIEW_ARTISTS;
        updateTabs();
        hideFilterBar();
        removeAlbumHeader();
        showLoading();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<JellyfinClient.JellyfinItem> result = mClient.getArtists();
                mHandler.post(new Runnable() {
                    public void run() {
                        hideLoading();
                        mItems = result;
                        setListAdapter(new JellyfinAdapter(mItems, false));
                    }
                });
            }
        }).start();
    }

    private void loadAlbums(final String artistId) {
        mCurrentView = artistId != null ? VIEW_ARTIST_ALBUMS : VIEW_ALBUMS;
        mDrillArtistId = artistId;
        updateTabs();
        removeAlbumHeader();
        showFilterBar();
        showLoading();
        final String sortBy = getJellyfinSortBy();
        final String sortOrder = getJellyfinSortOrder();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<JellyfinClient.JellyfinItem> result = mClient.getAlbums(artistId, sortBy, sortOrder);
                mHandler.post(new Runnable() {
                    public void run() {
                        hideLoading();
                        mItems = result;
                        showAlbumView();
                    }
                });
            }
        }).start();
    }

    private void loadTracks(final String parentId, final String title) {
        loadTracks(parentId, title, null, 0);
    }

    private void loadTracks(final String parentId, final String title,
                            final String artist, final int year) {
        mCurrentView = VIEW_ALBUM_TRACKS;
        mDrillAlbumId = parentId;
        mDrillTitle = title;
        hideFilterBar();
        removeAlbumHeader();
        showLoading();

        mAlbumHeader = getLayoutInflater().inflate(R.layout.album_header, null);
        ((TextView) mAlbumHeader.findViewById(R.id.header_album_name))
                .setText(title != null ? title : "Unknown Album");
        ((TextView) mAlbumHeader.findViewById(R.id.header_album_artist))
                .setText(artist != null ? artist : "");
        TextView yearView = (TextView) mAlbumHeader.findViewById(R.id.header_album_year);
        if (year > 0) {
            yearView.setText(String.valueOf(year));
        } else {
            yearView.setVisibility(View.GONE);
        }
        loadArtInto((ImageView) mAlbumHeader.findViewById(R.id.header_album_art), parentId);
        setListAdapter(null);
        getListView().addHeaderView(mAlbumHeader, null, false);

        new Thread(new Runnable() {
            public void run() {
                final ArrayList<JellyfinClient.JellyfinItem> result = mClient.getTracks(parentId);
                mHandler.post(new Runnable() {
                    public void run() {
                        hideLoading();
                        mItems = result;
                        setListAdapter(new JellyfinAdapter(mItems, false));
                    }
                });
            }
        }).start();
    }

    private void removeAlbumHeader() {
        if (mAlbumHeader != null) {
            getListView().removeHeaderView(mAlbumHeader);
            mAlbumHeader = null;
        }
    }

    private void loadAllTracks() {
        mCurrentView = VIEW_TRACKS;
        updateTabs();
        hideFilterBar();
        removeAlbumHeader();
        showLoading();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<JellyfinClient.JellyfinItem> result = mClient.getAllTracks();
                mHandler.post(new Runnable() {
                    public void run() {
                        hideLoading();
                        mItems = result;
                        setListAdapter(new JellyfinAdapter(mItems, false));
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (mSearchVisible) {
            handleSearchClick(position);
            return;
        }

        int headerCount = getListView().getHeaderViewsCount();
        int adjPos = position - headerCount;
        if (adjPos < 0 || adjPos >= mItems.size()) return;
        JellyfinClient.JellyfinItem item = mItems.get(adjPos);

        if ("MusicArtist".equals(item.type)) {
            loadAlbums(item.id);
        } else if ("MusicAlbum".equals(item.type)) {
            loadTracks(item.id, item.name, item.artist, item.year);
        } else if ("Audio".equals(item.type)) {
            playFromList(adjPos);
        }
    }

    private void handleSearchClick(int position) {
        if (position < 0 || position >= mSearchResults.size()) return;
        JellyfinClient.JellyfinItem item = mSearchResults.get(position);
        if (item.id == null) return; // header

        if ("MusicArtist".equals(item.type)) {
            hideSearchBar();
            loadAlbums(item.id);
        } else if ("MusicAlbum".equals(item.type)) {
            hideSearchBar();
            loadTracks(item.id, item.name, item.artist, item.year);
        } else if ("Audio".equals(item.type)) {
            ArrayList<JellyfinClient.JellyfinItem> tracks = new ArrayList<JellyfinClient.JellyfinItem>();
            int playIdx = 0;
            int trackCount = 0;
            for (int i = 0; i < mSearchResults.size(); i++) {
                JellyfinClient.JellyfinItem si = mSearchResults.get(i);
                if ("Audio".equals(si.type)) {
                    if (i == position) playIdx = trackCount;
                    tracks.add(si);
                    trackCount++;
                }
            }
            playJellyfinTracks(tracks, playIdx);
        }
    }

    private void playFromList(int position) {
        ArrayList<JellyfinClient.JellyfinItem> tracks = new ArrayList<JellyfinClient.JellyfinItem>();
        int playIdx = 0;
        int trackCount = 0;
        for (int i = 0; i < mItems.size(); i++) {
            JellyfinClient.JellyfinItem item = mItems.get(i);
            if ("Audio".equals(item.type)) {
                if (i == position) playIdx = trackCount;
                tracks.add(item);
                trackCount++;
            }
        }
        playJellyfinTracks(tracks, playIdx);
    }

    private void playJellyfinTracks(ArrayList<JellyfinClient.JellyfinItem> items, int startIndex) {
        if (!mBound || items.size() == 0) return;
        ArrayList<MusicService.JellyfinTrack> tracks = new ArrayList<MusicService.JellyfinTrack>();
        for (int i = 0; i < items.size(); i++) {
            JellyfinClient.JellyfinItem item = items.get(i);
            MusicService.JellyfinTrack jt = new MusicService.JellyfinTrack();
            jt.jellyfinId = item.id;
            jt.title = item.name;
            jt.artist = item.artist;
            jt.album = item.album;
            jt.albumId = item.albumId;
            jt.streamUrl = mClient.getStreamUrl(item.id);
            jt.imageUrl = mClient.getImageUrl(
                    item.albumId != null ? item.albumId : item.id, 240);
            jt.durationMs = item.durationMs;
            tracks.add(jt);
        }
        mService.setJellyfinQueue(tracks, startIndex);
        downloadArtForCurrentTrack();
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    private void downloadArtForCurrentTrack() {
        if (!mBound || !mService.isJellyfinTrack()) return;
        final MusicService.JellyfinTrack jt = mService.getJellyfinTrack();
        if (jt == null || jt.imageUrl == null) return;
        final String artAlbumId = jt.albumId != null ? jt.albumId : jt.jellyfinId;
        final File artFile = new File(Environment.getExternalStorageDirectory(),
                ".walkman_art/jf_" + artAlbumId + ".jpg");
        if (artFile.exists()) return;
        new Thread(new Runnable() {
            public void run() {
                mClient.downloadArt(artAlbumId, 240, artFile);
            }
        }).start();
    }

    private void performSearch(final String query) {
        showLoading();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<JellyfinClient.JellyfinItem> results = mClient.search(query);
                mHandler.post(new Runnable() {
                    public void run() {
                        hideLoading();
                        mCurrentView = VIEW_SEARCH;

                        ArrayList<JellyfinClient.JellyfinItem> sectioned = new ArrayList<JellyfinClient.JellyfinItem>();
                        ArrayList<JellyfinClient.JellyfinItem> artists = new ArrayList<JellyfinClient.JellyfinItem>();
                        ArrayList<JellyfinClient.JellyfinItem> albums = new ArrayList<JellyfinClient.JellyfinItem>();
                        ArrayList<JellyfinClient.JellyfinItem> tracks = new ArrayList<JellyfinClient.JellyfinItem>();

                        for (int i = 0; i < results.size(); i++) {
                            JellyfinClient.JellyfinItem it = results.get(i);
                            if ("MusicArtist".equals(it.type)) artists.add(it);
                            else if ("MusicAlbum".equals(it.type)) albums.add(it);
                            else if ("Audio".equals(it.type)) tracks.add(it);
                        }

                        if (artists.size() > 0) {
                            JellyfinClient.JellyfinItem hdr = new JellyfinClient.JellyfinItem();
                            hdr.name = "ARTISTS";
                            sectioned.add(hdr);
                            sectioned.addAll(artists);
                        }
                        if (albums.size() > 0) {
                            JellyfinClient.JellyfinItem hdr = new JellyfinClient.JellyfinItem();
                            hdr.name = "ALBUMS";
                            sectioned.add(hdr);
                            sectioned.addAll(albums);
                        }
                        if (tracks.size() > 0) {
                            JellyfinClient.JellyfinItem hdr = new JellyfinClient.JellyfinItem();
                            hdr.name = "TRACKS";
                            sectioned.add(hdr);
                            sectioned.addAll(tracks);
                        }

                        mSearchResults = sectioned;
                        setListAdapter(new SearchResultAdapter(sectioned));
                    }
                });
            }
        }).start();
    }

    private void showSearchBar() {
        mSearchBar.setVisibility(View.VISIBLE);
        mSearchEdit.requestFocus();
        mSearchVisible = true;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(mSearchEdit, 0);
    }

    private void hideSearchBar() {
        mSearchBar.setVisibility(View.GONE);
        mSearchEdit.setText("");
        mSearchVisible = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mSearchEdit.getWindowToken(), 0);
    }

    private void dismissSearch() {
        boolean wasSearch = mCurrentView == VIEW_SEARCH;
        hideSearchBar();
        if (wasSearch) loadArtists();
    }

    private void showLoading() {
        mLoadingText.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        mLoadingText.setVisibility(View.GONE);
    }

    private void updateTabs() {
        int active = mCurrentView;
        if (active == VIEW_ARTIST_ALBUMS) active = VIEW_ARTISTS;
        if (active == VIEW_ALBUM_TRACKS) active = -1;

        boolean isArtists = active == VIEW_ARTISTS;
        boolean isAlbums = active == VIEW_ALBUMS;
        boolean isTracks = active == VIEW_TRACKS;

        int activeColor = getResources().getColor(R.color.walkman_blue);
        int inactiveColor = getResources().getColor(R.color.text_tertiary);

        mTabArtists.setTextColor(isArtists ? activeColor : inactiveColor);
        mTabArtists.setTypeface(null, isArtists ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        mTabAlbums.setTextColor(isAlbums ? activeColor : inactiveColor);
        mTabAlbums.setTypeface(null, isAlbums ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        mTabTracks.setTextColor(isTracks ? activeColor : inactiveColor);
        mTabTracks.setTypeface(null, isTracks ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void updateNowPlayingBar() {
        if (!mBound || mService.getTitle() == null || mService.getTitle().length() == 0) {
            mNowPlayingContainer.setVisibility(View.GONE);
            mNowPlayingBorder.setVisibility(View.GONE);
            return;
        }
        mNowPlayingContainer.setVisibility(View.VISIBLE);
        mNowPlayingBorder.setVisibility(View.VISIBLE);
        mNowPlayingBar.setText(mService.getTitle() + " - " + mService.getArtist());
        mNowPlayingIcon.setImageResource(
                mService.isPlaying() ? R.drawable.btn_pause_selector : R.drawable.btn_play_selector);
    }

    @Override
    public void onBackPressed() {
        if (mSearchVisible) {
            dismissSearch();
        } else if (mCurrentView == VIEW_ALBUM_TRACKS && mDrillArtistId != null) {
            loadAlbums(mDrillArtistId);
        } else if (mCurrentView == VIEW_ALBUM_TRACKS || mCurrentView == VIEW_ARTIST_ALBUMS) {
            loadArtists();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SEARCH, 0, "Search")
                .setIcon(android.R.drawable.ic_menu_search);
        menu.add(0, MENU_NOW_PLAYING, 1, "Now Playing")
                .setIcon(android.R.drawable.ic_media_play);
        menu.add(0, MENU_AUDIO, 2, "Audio Settings")
                .setIcon(android.R.drawable.ic_lock_silent_mode_off);
        menu.add(0, MENU_SERVER, 3, "Server Settings")
                .setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_WALKMAN, 4, "Walkman")
                .setIcon(android.R.drawable.ic_menu_revert);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SEARCH:
                if (mSearchVisible) hideSearchBar();
                else showSearchBar();
                return true;
            case MENU_NOW_PLAYING:
                startActivity(new Intent(this, NowPlayingActivity.class));
                return true;
            case MENU_AUDIO:
                startActivity(new Intent(this, JellyfinAudioActivity.class));
                return true;
            case MENU_SERVER:
                startActivity(new Intent(this, JellyfinSettingsActivity.class));
                return true;
            case MENU_WALKMAN:
                startActivity(new Intent(this, WalkmanActivity.class));
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int headerCount = getListView().getHeaderViewsCount();
        int adjPos = info.position - headerCount;
        ArrayList<JellyfinClient.JellyfinItem> list = mSearchVisible ? mSearchResults : mItems;
        if (adjPos < 0 || adjPos >= list.size()) return;
        JellyfinClient.JellyfinItem item = list.get(adjPos);

        menu.setHeaderTitle(item.name);
        if ("Audio".equals(item.type)) {
            menu.add(0, CTX_PLAY, 0, "Play");
            menu.add(0, CTX_PLAY_NEXT, 1, "Play Next");
            menu.add(0, CTX_ADD_QUEUE, 2, "Add to Queue");
            menu.add(0, CTX_DOWNLOAD, 3, "Download");
        } else if ("MusicAlbum".equals(item.type)) {
            menu.add(0, CTX_PLAY_NEXT_ALBUM, 0, "Play Next");
            menu.add(0, CTX_QUEUE_ALBUM, 1, "Add to Queue");
            menu.add(0, CTX_DOWNLOAD_ALBUM, 2, "Download Album");
        } else if ("MusicArtist".equals(item.type)) {
            menu.add(0, CTX_PLAY_NEXT_ARTIST, 0, "Play Next");
            menu.add(0, CTX_QUEUE_ARTIST, 1, "Add to Queue");
            menu.add(0, CTX_DOWNLOAD_ARTIST, 2, "Download Artist");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int headerCount = getListView().getHeaderViewsCount();
        int adjPos = info.position - headerCount;
        ArrayList<JellyfinClient.JellyfinItem> list = mSearchVisible ? mSearchResults : mItems;
        if (adjPos < 0 || adjPos >= list.size()) return false;
        final JellyfinClient.JellyfinItem jfItem = list.get(adjPos);

        switch (item.getItemId()) {
            case CTX_PLAY:
                ArrayList<JellyfinClient.JellyfinItem> single = new ArrayList<JellyfinClient.JellyfinItem>();
                single.add(jfItem);
                playJellyfinTracks(single, 0);
                return true;
            case CTX_PLAY_NEXT:
                if (mBound) {
                    MusicService.JellyfinTrack pn = new MusicService.JellyfinTrack();
                    pn.jellyfinId = jfItem.id;
                    pn.title = jfItem.name;
                    pn.artist = jfItem.artist;
                    pn.album = jfItem.album;
                    pn.albumId = jfItem.albumId;
                    pn.streamUrl = mClient.getStreamUrl(jfItem.id);
                    pn.imageUrl = mClient.getImageUrl(
                            jfItem.albumId != null ? jfItem.albumId : jfItem.id, 240);
                    pn.durationMs = jfItem.durationMs;
                    if (!mService.isPlaying() && mService.getQueueSize() == 0) {
                        ArrayList<MusicService.JellyfinTrack> pnList =
                                new ArrayList<MusicService.JellyfinTrack>();
                        pnList.add(pn);
                        mService.setJellyfinQueue(pnList, 0);
                    } else {
                        mService.addJellyfinToQueueNext(pn);
                    }
                    Toast.makeText(this, "Playing next", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
                }
                return true;
            case CTX_ADD_QUEUE:
                if (mBound) {
                    MusicService.JellyfinTrack jt = new MusicService.JellyfinTrack();
                    jt.jellyfinId = jfItem.id;
                    jt.title = jfItem.name;
                    jt.artist = jfItem.artist;
                    jt.album = jfItem.album;
                    jt.albumId = jfItem.albumId;
                    jt.streamUrl = mClient.getStreamUrl(jfItem.id);
                    jt.imageUrl = mClient.getImageUrl(
                            jfItem.albumId != null ? jfItem.albumId : jfItem.id, 240);
                    jt.durationMs = jfItem.durationMs;
                    if (!mService.isPlaying() && mService.getQueueSize() == 0) {
                        ArrayList<MusicService.JellyfinTrack> queueList =
                                new ArrayList<MusicService.JellyfinTrack>();
                        queueList.add(jt);
                        mService.setJellyfinQueue(queueList, 0);
                    } else {
                        mService.addJellyfinToQueue(jt);
                    }
                    Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
                }
                return true;
            case CTX_DOWNLOAD:
                downloadTrack(jfItem);
                return true;
            case CTX_QUEUE_ALBUM:
                queueAlbum(jfItem);
                return true;
            case CTX_PLAY_NEXT_ALBUM:
                queueAlbumAt(jfItem, true);
                return true;
            case CTX_DOWNLOAD_ALBUM:
                downloadAlbum(jfItem);
                return true;
            case CTX_QUEUE_ARTIST:
                queueArtist(jfItem, false);
                return true;
            case CTX_PLAY_NEXT_ARTIST:
                queueArtist(jfItem, true);
                return true;
            case CTX_DOWNLOAD_ARTIST:
                downloadArtist(jfItem);
                return true;
        }
        return false;
    }

    private void queueAlbum(final JellyfinClient.JellyfinItem album) {
        queueAlbumAt(album, false);
    }

    private void queueAlbumAt(final JellyfinClient.JellyfinItem album, final boolean playNext) {
        if (!mBound) {
            Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Loading album...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<JellyfinClient.JellyfinItem> tracks = mClient.getTracks(album.id);
                mHandler.post(new Runnable() {
                    public void run() {
                        if (tracks == null || tracks.isEmpty()) {
                            Toast.makeText(JellyfinActivity.this,
                                    "No tracks found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!mBound) return;
                        boolean wasEmpty = !mService.isPlaying() && mService.getQueueSize() == 0;
                        if (wasEmpty) {
                            playJellyfinTracks(tracks, 0);
                        } else {
                            addTracksToQueue(tracks, playNext);
                            String msg = playNext ? "Playing next" : "Added " + tracks.size() + " tracks to queue";
                            Toast.makeText(JellyfinActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void queueArtist(final JellyfinClient.JellyfinItem artist, final boolean playNext) {
        if (!mBound) {
            Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Loading artist...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                ArrayList<JellyfinClient.JellyfinItem> albums =
                        mClient.getAlbums(artist.id, "SortName", "Ascending");
                final ArrayList<JellyfinClient.JellyfinItem> allTracks =
                        new ArrayList<JellyfinClient.JellyfinItem>();
                if (albums != null) {
                    for (int a = 0; a < albums.size(); a++) {
                        ArrayList<JellyfinClient.JellyfinItem> tracks = mClient.getTracks(albums.get(a).id);
                        if (tracks != null) allTracks.addAll(tracks);
                    }
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        if (allTracks.isEmpty()) {
                            Toast.makeText(JellyfinActivity.this,
                                    "No tracks found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!mBound) return;
                        boolean wasEmpty = !mService.isPlaying() && mService.getQueueSize() == 0;
                        if (wasEmpty) {
                            playJellyfinTracks(allTracks, 0);
                        } else {
                            addTracksToQueue(allTracks, playNext);
                            String msg = playNext ? "Playing next" : "Added " + allTracks.size() + " tracks to queue";
                            Toast.makeText(JellyfinActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void addTracksToQueue(ArrayList<JellyfinClient.JellyfinItem> tracks, boolean playNext) {
        int start = playNext ? tracks.size() - 1 : 0;
        int end = playNext ? -1 : tracks.size();
        int step = playNext ? -1 : 1;
        for (int i = start; i != end; i += step) {
            JellyfinClient.JellyfinItem t = tracks.get(i);
            MusicService.JellyfinTrack jt = new MusicService.JellyfinTrack();
            jt.jellyfinId = t.id;
            jt.title = t.name;
            jt.artist = t.artist;
            jt.album = t.album;
            jt.albumId = t.albumId;
            jt.streamUrl = mClient.getStreamUrl(t.id);
            jt.imageUrl = mClient.getImageUrl(
                    t.albumId != null ? t.albumId : t.id, 240);
            jt.durationMs = t.durationMs;
            if (playNext) {
                mService.addJellyfinToQueueNext(jt);
            } else {
                mService.addJellyfinToQueue(jt);
            }
        }
    }

    private Notification buildDownloadNotification(String title, int progress, int total) {
        Notification n = new Notification(
                android.R.drawable.stat_sys_download,
                "Downloading " + title,
                System.currentTimeMillis());
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        String text;
        if (total <= 1) {
            text = "Downloading...";
        } else {
            text = "Downloading " + progress + "/" + total;
        }
        android.widget.RemoteViews rv = new android.widget.RemoteViews(getPackageName(),
                android.R.layout.simple_list_item_2);
        rv.setTextViewText(android.R.id.text1, title);
        rv.setTextViewText(android.R.id.text2, text);
        n.contentView = rv;
        return n;
    }

    private void showDownloadComplete(String title, int downloaded, int total) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification n = new Notification(
                android.R.drawable.stat_sys_download_done,
                "Download complete",
                System.currentTimeMillis());
        n.flags |= Notification.FLAG_AUTO_CANCEL;
        String text;
        if (total <= 1) {
            text = downloaded > 0 ? "Download complete" : "Download failed";
        } else {
            text = "Downloaded " + downloaded + "/" + total + " tracks";
        }
        android.widget.RemoteViews rv = new android.widget.RemoteViews(getPackageName(),
                android.R.layout.simple_list_item_2);
        rv.setTextViewText(android.R.id.text1, title);
        rv.setTextViewText(android.R.id.text2, text);
        n.contentView = rv;
        nm.notify(DOWNLOAD_NOTIFICATION_ID, n);
    }

    private void downloadTrack(final JellyfinClient.JellyfinItem item) {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final String title = item.name != null ? item.name : "track";
        nm.notify(DOWNLOAD_NOTIFICATION_ID, buildDownloadNotification(title, 0, 1));
        new Thread(new Runnable() {
            public void run() {
                String artist = item.artist != null ? item.artist : "Unknown";
                String album = item.album != null ? item.album : "Unknown";
                String name = sanitizeFilename(title);

                File dir = new File(Environment.getExternalStorageDirectory(),
                        "Music/Jellyfin/" + sanitizeFilename(artist) + "/" + sanitizeFilename(album));
                final File outFile = new File(dir, name + ".mp3");

                final boolean ok = mClient.downloadTrack(item.id, outFile);
                mHandler.post(new Runnable() {
                    public void run() {
                        if (ok) {
                            sendBroadcast(new Intent(
                                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    Uri.fromFile(outFile)));
                        }
                        showDownloadComplete(title, ok ? 1 : 0, 1);
                    }
                });
            }
        }).start();
    }

    private void downloadAlbum(final JellyfinClient.JellyfinItem album) {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final String albumTitle = album.name != null ? album.name : "Album";
        nm.notify(DOWNLOAD_NOTIFICATION_ID, buildDownloadNotification(albumTitle, 0, 0));
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<JellyfinClient.JellyfinItem> tracks = mClient.getTracks(album.id);
                if (tracks == null || tracks.isEmpty()) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            nm.cancel(DOWNLOAD_NOTIFICATION_ID);
                            Toast.makeText(JellyfinActivity.this,
                                    "No tracks found", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                int count = 0;
                for (int i = 0; i < tracks.size(); i++) {
                    JellyfinClient.JellyfinItem track = tracks.get(i);
                    String artist = track.artist != null ? track.artist : "Unknown";
                    String albumName = track.album != null ? track.album :
                            (album.name != null ? album.name : "Unknown");
                    String name = track.name != null ? track.name : "track";
                    File dir = new File(Environment.getExternalStorageDirectory(),
                            "Music/Jellyfin/" + sanitizeFilename(artist) + "/" + sanitizeFilename(albumName));
                    File outFile = new File(dir, sanitizeFilename(name) + ".mp3");
                    if (mClient.downloadTrack(track.id, outFile)) {
                        sendBroadcast(new Intent(
                                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.fromFile(outFile)));
                        count++;
                    }
                    final int progress = i + 1;
                    final int total = tracks.size();
                    mHandler.post(new Runnable() {
                        public void run() {
                            nm.notify(DOWNLOAD_NOTIFICATION_ID,
                                    buildDownloadNotification(albumTitle, progress, total));
                        }
                    });
                }
                final int downloaded = count;
                final int total = tracks.size();
                mHandler.post(new Runnable() {
                    public void run() {
                        showDownloadComplete(albumTitle, downloaded, total);
                    }
                });
            }
        }).start();
    }

    private void downloadArtist(final JellyfinClient.JellyfinItem artist) {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final String artistTitle = artist.name != null ? artist.name : "Artist";
        nm.notify(DOWNLOAD_NOTIFICATION_ID, buildDownloadNotification(artistTitle, 0, 0));
        new Thread(new Runnable() {
            public void run() {
                ArrayList<JellyfinClient.JellyfinItem> albums =
                        mClient.getAlbums(artist.id, "SortName", "Ascending");
                if (albums == null || albums.isEmpty()) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            nm.cancel(DOWNLOAD_NOTIFICATION_ID);
                            Toast.makeText(JellyfinActivity.this,
                                    "No albums found", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                int count = 0;
                int totalTracks = 0;
                ArrayList<JellyfinClient.JellyfinItem> allTracks = new ArrayList<JellyfinClient.JellyfinItem>();
                for (int a = 0; a < albums.size(); a++) {
                    JellyfinClient.JellyfinItem album = albums.get(a);
                    ArrayList<JellyfinClient.JellyfinItem> tracks = mClient.getTracks(album.id);
                    if (tracks != null) allTracks.addAll(tracks);
                }
                totalTracks = allTracks.size();
                for (int i = 0; i < allTracks.size(); i++) {
                    JellyfinClient.JellyfinItem track = allTracks.get(i);
                    String trackArtist = track.artist != null ? track.artist :
                            (artist.name != null ? artist.name : "Unknown");
                    String albumName = track.album != null ? track.album : "Unknown";
                    String name = track.name != null ? track.name : "track";
                    File dir = new File(Environment.getExternalStorageDirectory(),
                            "Music/Jellyfin/" + sanitizeFilename(trackArtist) + "/" + sanitizeFilename(albumName));
                    File outFile = new File(dir, sanitizeFilename(name) + ".mp3");
                    if (mClient.downloadTrack(track.id, outFile)) {
                        sendBroadcast(new Intent(
                                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.fromFile(outFile)));
                        count++;
                    }
                    final int progress = i + 1;
                    final int total = totalTracks;
                    mHandler.post(new Runnable() {
                        public void run() {
                            nm.notify(DOWNLOAD_NOTIFICATION_ID,
                                    buildDownloadNotification(artistTitle, progress, total));
                        }
                    });
                }
                final int downloaded = count;
                final int total = totalTracks;
                mHandler.post(new Runnable() {
                    public void run() {
                        showDownloadComplete(artistTitle, downloaded, total);
                    }
                });
            }
        }).start();
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    // Adapter for browsing
    private class JellyfinAdapter extends BaseAdapter {
        private ArrayList<JellyfinClient.JellyfinItem> mData;
        private boolean mShowArt;

        JellyfinAdapter(ArrayList<JellyfinClient.JellyfinItem> data, boolean showArt) {
            mData = data;
            mShowArt = showArt;
        }

        public int getCount() { return mData.size(); }
        public Object getItem(int pos) { return mData.get(pos); }
        public long getItemId(int pos) { return pos; }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            if (mShowArt) {
                v = getLayoutInflater().inflate(R.layout.list_item_album, parent, false);
            } else {
                v = getLayoutInflater().inflate(R.layout.list_item_track, parent, false);
            }

            JellyfinClient.JellyfinItem item = mData.get(position);
            TextView title = (TextView) v.findViewById(R.id.track_title);
            TextView sub = (TextView) v.findViewById(R.id.track_artist);

            if (title != null) title.setText(item.name);
            if (sub != null) {
                String subText = "";
                if ("Audio".equals(item.type)) {
                    subText = item.artist != null ? item.artist : "";
                } else if ("MusicAlbum".equals(item.type)) {
                    subText = item.artist != null ? item.artist : "";
                } else if ("MusicArtist".equals(item.type)) {
                    subText = "Artist";
                }
                sub.setText(subText);
            }

            if (mShowArt) {
                ImageView art = (ImageView) v.findViewById(R.id.album_art);
                if (art != null && item.hasImage) {
                    loadArtInto(art, item.id);
                }
            }

            return v;
        }
    }

    // Search results adapter with section headers
    private class SearchResultAdapter extends BaseAdapter {
        private ArrayList<JellyfinClient.JellyfinItem> mData;

        SearchResultAdapter(ArrayList<JellyfinClient.JellyfinItem> data) { mData = data; }
        public int getCount() { return mData.size(); }
        public Object getItem(int pos) { return mData.get(pos); }
        public long getItemId(int pos) { return pos; }
        public int getViewTypeCount() { return 2; }
        public int getItemViewType(int pos) {
            return mData.get(pos).id == null ? 0 : 1;
        }
        public boolean isEnabled(int pos) {
            return mData.get(pos).id != null;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            JellyfinClient.JellyfinItem item = mData.get(position);
            if (item.id == null) {
                View v = getLayoutInflater().inflate(R.layout.list_item_search_header, parent, false);
                TextView tv = (TextView) v.findViewById(R.id.header_title);
                tv.setText(item.name);
                return v;
            }
            View v = getLayoutInflater().inflate(R.layout.list_item_track, parent, false);
            TextView title = (TextView) v.findViewById(R.id.track_title);
            TextView sub = (TextView) v.findViewById(R.id.track_artist);
            title.setText(item.name);
            if ("Audio".equals(item.type)) {
                sub.setText(item.artist != null ? item.artist : "");
            } else if ("MusicAlbum".equals(item.type)) {
                sub.setText(item.artist != null ? item.artist : "Album");
            } else {
                sub.setText("Artist");
            }
            return v;
        }
    }

    private void loadArtInto(final ImageView iv, final String itemId) {
        final File artFile = new File(Environment.getExternalStorageDirectory(),
                ".walkman_art/jf_" + itemId + ".jpg");
        if (artFile.exists()) {
            Bitmap bm = BitmapFactory.decodeFile(artFile.getAbsolutePath());
            if (bm != null) {
                iv.setImageBitmap(bm);
                return;
            }
        }
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = mClient.downloadArt(itemId, 80, artFile);
                if (ok) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            Bitmap bm = BitmapFactory.decodeFile(artFile.getAbsolutePath());
                            if (bm != null) iv.setImageBitmap(bm);
                        }
                    });
                }
            }
        }).start();
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
        getSharedPreferences("jf_album_prefs", MODE_PRIVATE).edit()
                .putInt("sort_field", mAlbumSortField)
                .putBoolean("sort_asc", mAlbumSortAsc)
                .putBoolean("grid_view", mAlbumGridView)
                .commit();
    }

    private void sortAlbums() {
        if (mItems == null || mItems.size() == 0) return;
        final int field = mAlbumSortField;
        final boolean asc = mAlbumSortAsc;
        Collections.sort(mItems, new Comparator<JellyfinClient.JellyfinItem>() {
            public int compare(JellyfinClient.JellyfinItem a, JellyfinClient.JellyfinItem b) {
                int cmp;
                switch (field) {
                    case SORT_YEAR:
                        cmp = a.year - b.year;
                        break;
                    case SORT_ARTIST: {
                        String sa = a.artist != null ? a.artist.toLowerCase() : "";
                        String sb = b.artist != null ? b.artist.toLowerCase() : "";
                        cmp = sa.compareTo(sb);
                        break;
                    }
                    default: {
                        String na = a.name != null ? a.name.toLowerCase() : "";
                        String nb = b.name != null ? b.name.toLowerCase() : "";
                        cmp = na.compareTo(nb);
                        break;
                    }
                }
                return asc ? cmp : -cmp;
            }
        });
    }

    private String getJellyfinSortBy() {
        switch (mAlbumSortField) {
            case SORT_YEAR: return "ProductionYear,SortName";
            case SORT_ARTIST: return "AlbumArtist,SortName";
            default: return "SortName";
        }
    }

    private String getJellyfinSortOrder() {
        return mAlbumSortAsc ? "Ascending" : "Descending";
    }

    private void resortAndRefreshAlbums() {
        showFilterBar();
        loadAlbums(mDrillArtistId);
    }

    private void showAlbumView() {
        if (mAlbumGridView) {
            getListView().setVisibility(View.GONE);
            findViewById(android.R.id.empty).setVisibility(View.GONE);
            mGridView.setVisibility(View.VISIBLE);
            mGridView.setAdapter(new JellyfinGridAdapter(mItems));
        } else {
            mGridView.setVisibility(View.GONE);
            getListView().setVisibility(View.VISIBLE);
            setListAdapter(new JellyfinAdapter(mItems, true));
        }
    }

    private class JellyfinGridAdapter extends BaseAdapter {
        private ArrayList<JellyfinClient.JellyfinItem> mData;

        JellyfinGridAdapter(ArrayList<JellyfinClient.JellyfinItem> data) {
            mData = data;
        }

        public int getCount() { return mData.size(); }
        public Object getItem(int pos) { return mData.get(pos); }
        public long getItemId(int pos) { return pos; }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(
                        R.layout.grid_item_album, parent, false);
            }
            JellyfinClient.JellyfinItem item = mData.get(position);
            ((TextView) convertView.findViewById(R.id.album_title)).setText(item.name);
            ((TextView) convertView.findViewById(R.id.album_artist)).setText(
                    item.artist != null ? item.artist : "");
            ImageView art = (ImageView) convertView.findViewById(R.id.album_art);
            art.setImageResource(R.drawable.musicplayer_default_album);
            if (item.hasImage) {
                loadArtInto(art, item.id);
            }
            return convertView;
        }
    }
}
