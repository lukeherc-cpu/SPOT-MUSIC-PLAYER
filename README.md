# Spot Music

Android music and podcast player built on Gamut's playback engine, with a new Spotify-style interface and native Kotlin integration of [Aran404/SpotAPI](https://github.com/Aran404/SpotAPI).

[Download Android alpha APKs](https://github.com/lukeherc-cpu/SPOT-MUSIC-PLAYER/releases) · [Source](https://github.com/lukeherc-cpu/SPOT-MUSIC-PLAYER)

- Home with history-driven Daily Mixes, All / Music / Podcasts filters, saved shortcuts and artwork rails. Mixes refresh after listening changes and every 30 minutes.
- Dedicated Music, genre, new-release and Charts browse pages. Charts opens real Spotify US/global chart collections. Song menus include Go to album; album identities persist in your library.
- One search for songs, podcasts, Spotify albums, artists and playlists.
- Your Library with liked songs, playlists, followed shows and downloaded music.
- Import public Spotify playlist and album links, including every page of songs. Source order and artwork are retained; duplicate songs are stored once. Imports commit atomically. Private playlists and Liked Songs require account access and are not supported by anonymous import.
- Spotify-style collection pages, full player, compact now-playing bar, queue, lyrics and flat dark settings. The former Gamut navigation and screen tree are removed from the application entry point.
- Publisher RSS podcast audio, following, resume and 15-second seeking.
- Persistent song-source mappings, private expiring audio URLs, audio caching and saved local file paths for faster repeat playback.

Spotify supplies catalog metadata. Music audio uses Gamut's existing source matching and Media3 player; this app does not stream decrypted Spotify audio. No Real-Debrid account, torrent client or separate server is needed. Spotify's public web protocol can change independently of this application.

Android 8+; alpha releases target ARM64 devices. Package: `com.lukeherc.spotmusic`. Development-signed alpha builds.

## Build

Use JDK 21 and Android SDK 35:

```sh
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest testDebugUnitTest
bash ./gradlew :app:assembleRelease -Pgamut.abis=arm64-v8a
```

[Spotify integration](docs/SPOTAPI_INTEGRATION.md) · [Podcasts](docs/PODCASTS.md) · [Validation](docs/VALIDATION.md)

## Attribution and license

Based on [Gamut by serptail](https://github.com/serptail/gamut), licensed under [AGPL-3.0](LICENSE). SpotAPI's adapted protocol code retains its GPL-3.0 notices and [license](docs/licenses/SpotAPI-GPL-3.0.txt). Both license texts are bundled in the app and available in Settings. Original project documentation is preserved in [the upstream README](docs/UPSTREAM_README.md).

Spot Music is an independent project and is not affiliated with Spotify.
