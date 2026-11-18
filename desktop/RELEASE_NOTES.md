Desktop alpha for Windows, macOS and Linux.

- Automatic offline podcast subtitles with bundled multilingual Whisper Tiny when publisher text is missing. Generated cues are timed and cached; first-play processing takes time and recognition may contain errors.
- Tap cover art for lyrics or subtitles, expand the text to full screen, and tap text to return to the cover without seeking.
- Clean-version lyric filtering, skeleton content loading and randomized shuffle starting songs.
- Removes the duplicate Listen again section while keeping Recently played.

- Spotify-style desktop Home, Search, Library, collection pages, song menus and full player.
- Real Spotify US/global charts and dedicated Music, genre and new-release discovery pages.
- Daily Mixes built from this app's listening history, with recommendations that refresh as you listen.
- Spotify catalog integration adapted from Aran404/SpotAPI. Import public playlist and album links with pagination and atomic saves. Private Spotify playlists and account-specific Spotify mixes are not supported.
- Publisher podcast feeds, following, resume and 15-second seeking.
- Bundled audio resolver, cached source URLs, upcoming-track prefetch and saved local audio for faster repeat playback. No separate server, Python installation or Real-Debrid account is required.
- Local library and listening history; desktop and Android libraries do not sync automatically.

Downloads: Windows x64 `.exe` installer; macOS Intel `mac-x64` or Apple Silicon `mac-arm64` `.dmg`/`.zip`; Linux x64 `.AppImage` or `.deb`. Choose the matching OS and architecture. Prerelease desktop installers are unsigned and macOS builds are not notarized.

Music uses matched public audio sources; Spotify supplies metadata. Independent project, not affiliated with Spotify. AGPL-3.0 with SpotAPI GPL-3.0 attribution retained. Third-party notices and resolver source/version information are included in each package.
# Playback update

- Fixes fresh music streams rejected by the provider: selects the verified client explicitly and checks direct audio before offering it to the player.
- Preserves provider headers and the signed stream's IP family; retries an alternate format and HLS when needed.
- Bundles HLS playback and offline manifest/segment support without requiring a server or a separate runtime.
- Keeps the existing library and saved audio. Packaged tests now decode HLS audio as well as saved WAV playback and seeking.
