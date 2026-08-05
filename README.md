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
- **Synced lyrics** from **LRCLIB** with timed line highlighting and auto-scroll
- **LRC file** support for local `.lrc` files alongside audio files
- **Jellyfin lyrics** support (synced and plain)
- **Plain lyrics** fallback via LRCLIB and ChartLyrics
- **Favourite** toggle and **add to playlist** from the player
- Landscape layout support

### Jellyfin Integration
- Browse a Jellyfin server: artists, albums, tracks, and search
- Stream with configurable transcoding: MP3, AAC, OGG, WAV, or direct
- Adjustable bitrate (64–320 kbps) and sample rate
- Download tracks to device storage
- Album art loaded from server and cached locally

### Metadata & Album Art
- **MusicBrainz** lookup to auto-correct track metadata (via HTTPS/wolfSSL with HTTP fallback)
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
| **Native Code** | C (wolfSSL via NDK r16b) |
| **Dependencies** | None (wolfSSL bundled as prebuilt .so) |
| **Build System** | Gradle 5.6.4, AGP 3.2.1 |

### Architecture

The app uses only the Android SDK with no third-party libraries. JSON parsing, HTTP networking, ID3 tag writing, image loading, and TLS are all implemented from scratch or bundled at the native level.

| Component | Description |
|---|---|
| `WalkmanActivity` | Main library browser with tabs, search, filter bar, and playlist management |
| `NowPlayingActivity` | Full-screen player with lyrics, favourites, and playlist integration |
| `MusicService` | Bound + started service handling MediaPlayer, queue, shuffle/repeat, notifications, and wake lock |
| `JellyfinActivity` | Jellyfin library browser with grid/list views and album art |
| `JellyfinClient` | HTTP client for Jellyfin API: auth, browsing, streaming URLs, downloads, lyrics |
| `EditInfoActivity` | Track metadata editor with MusicBrainz lookup |
| `MetadataService` | Background IntentService for bulk MusicBrainz metadata updates |
| `MetadataUtils` | MusicBrainz API, Cover Art Archive, LRCLIB lyrics, and ID3 tag read/write utilities |
| `LrcParser` | Synchronized lyrics parser for `.lrc` files with binary-search line lookup |
| `TlsHelper` | HTTPS client using wolfSSL JNI with Java DNS resolution and HTTP response parsing |
| `WolfSSLNative` | JNI bridge to native wolfSSL library for TLS 1.3 on Android 2.1 |
| `PlaylistUtils` | MediaStore playlist operations (create, add, remove, favourites) |
| `NowPlayingWidget` | Home-screen AppWidgetProvider |
| `MediaButtonReceiver` | Hardware media key event handler |

### Theme

Dark UI with a Sony Walkman-inspired blue accent (`#5ab0e3`), using `Theme.Black.NoTitleBar`.

### TLS / HTTPS

Android 2.1's built-in SSL stack cannot negotiate modern TLS, so the app bundles [wolfSSL](https://www.wolfssl.com/) as a native shared library to provide TLS 1.3 support. Key details:

- **Cipher**: ChaCha20-Poly1305 only (AES-GCM has alignment issues on ARMv6)
- **ARM mode**: forced 32-bit ARM instructions (`LOCAL_ARM_MODE := arm`) — Thumb-1 breaks crypto on ARMv6
- **DNS resolution**: performed in Java via `InetAddress.getByName()` (native `gethostbyname()` doesn't pick up Android's DNS settings), then the resolved IP is passed to native code
- **SNI**: hostname sent via TLS SNI extension while connecting by IP

## Building

### APK

Open the project in **Android Studio** and build/deploy from the IDE.

```
compileSdkVersion 28
minSdkVersion 7
targetSdkVersion 28
```

**Note:** If you have `android-36.1` (or any non-integer API level) in your SDK, move `platforms/android-36.1` and `sources/android-36.1` out of the SDK directory before building — AGP 3.2.1 cannot parse non-integer API levels.

### Native Library (wolfSSL)

The prebuilt `libwolfssljni.so` is checked into `app/src/main/jni/libs/armeabi/`. To rebuild from source:

```bash
./build-wolfssl.sh
```

Requires Docker. The script runs an `ubuntu:20.04` container on `linux/amd64`, downloads NDK r16b, clones wolfSSL v5.7.6-stable, and cross-compiles for armeabi (ARMv5/ARMv6).

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
