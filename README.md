# Walkman X10 Mini

A native Android music player inspired by the Sony Ericsson Walkman, built for the Xperia X10 Mini and other low-end Android devices. Plays local music and streams from a Jellyfin media server — with zero external dependencies.

## Features

### Local Music
- Browse by **Artists**, **Albums**, and **Tracks** via MediaStore
- **Playlists**: create, rename, delete, and manage playlists including smart playlists (Favourites, Recently Played, Most Played, Recently Added)
- **Search** across artists, albums, and tracks
- Album view with **grid/list toggle**, **sort** (Name / Year / Artist), and **ascending/descending** order
- **Context menu**: play, edit info, add to playlist, set as ringtone, delete

### Now Playing
- Full-screen player with album art, seek bar, and transport controls
- **Shuffle** and **repeat** modes (off / all / one)
- **LRC lyrics** with timed line highlighting
- **Favourite** toggle and **add to playlist** from the player
- Landscape layout support

### Jellyfin Integration
- Browse a Jellyfin server: artists, albums, tracks, and search
- Stream with configurable transcoding: MP3, AAC, OGG, WAV, or direct
- Adjustable bitrate (64–320 kbps) and sample rate
- Download tracks to device storage
- Album art loaded from server and cached locally

### Metadata & Album Art
- **MusicBrainz** lookup to auto-correct track metadata
- Album art download from **Cover Art Archive**
- Writes **ID3v1 and ID3v2** tags directly to audio files
- Bulk metadata download for entire library via background service

### System Integration
- **Home-screen widget** with album art and transport controls (prev / play-pause / next)
- **Media button** support (headset, Bluetooth)
- **Phone call** auto-pause and resume
- Custom **notification** with playback controls
- Handles `ACTION_VIEW` intents for audio files

## Screenshots

_Coming soon._

## Technical Details

| | |
|---|---|
| **Package** | `com.walkman.x10mini` |
| **Version** | 5.1.0 |
| **Min SDK** | 7 (Android 2.1 Eclair) |
| **Target SDK** | 28 (Android 9 Pie) |
| **Language** | Java 1.7 |
| **Dependencies** | None |
| **Build System** | Gradle 5.6.4, AGP 3.2.1 |

### Architecture

The app uses only the Android SDK with no third-party libraries. JSON parsing, HTTP networking, ID3 tag writing, and image loading are all implemented from scratch.

| Component | Description |
|---|---|
| `WalkmanActivity` | Main library browser with tabs, search, filter bar, and playlist management |
| `NowPlayingActivity` | Full-screen player with lyrics, favourites, and playlist integration |
| `MusicService` | Bound + started service handling MediaPlayer, queue, shuffle/repeat, notifications, and wake lock |
| `JellyfinActivity` | Jellyfin library browser with grid/list views and album art |
| `JellyfinClient` | HTTP client for Jellyfin API: auth, browsing, streaming URLs, downloads |
| `EditInfoActivity` | Track metadata editor with MusicBrainz lookup |
| `MetadataService` | Background IntentService for bulk MusicBrainz metadata updates |
| `MetadataUtils` | MusicBrainz API, Cover Art Archive, and ID3 tag read/write utilities |
| `LrcParser` | Synchronized lyrics parser for `.lrc` files |
| `PlaylistUtils` | MediaStore playlist operations (create, add, remove, favourites) |
| `NowPlayingWidget` | Home-screen AppWidgetProvider |
| `MediaButtonReceiver` | Hardware media key event handler |

### Theme

Dark UI with a Sony Walkman-inspired blue accent (`#5ab0e3`), using `Theme.Black.NoTitleBar`.

## Building

Open the project in **Android Studio** and build from the IDE. The project uses an older Gradle/AGP version compatible with Android Studio 3.x.

```
compileSdkVersion 28
minSdkVersion 7
targetSdkVersion 28
```

## Target Device

Originally built for the **Sony Ericsson Xperia X10 Mini**:
- Android 2.1 (API 7)
- ARMv6 processor
- 240x320 QVGA display
- 128 MB RAM

The app runs on any Android 2.1+ device but is optimized for small screens and constrained hardware.

## Permissions

| Permission | Reason |
|---|---|
| `WAKE_LOCK` | Keep CPU alive during playback |
| `WRITE_EXTERNAL_STORAGE` | Save downloaded tracks and album art |
| `READ_PHONE_STATE` | Pause playback on incoming calls |
| `INTERNET` | Jellyfin streaming, MusicBrainz lookups, album art downloads |
| `WRITE_SETTINGS` | Set tracks as ringtone |
| `VIBRATE` | Haptic feedback |
