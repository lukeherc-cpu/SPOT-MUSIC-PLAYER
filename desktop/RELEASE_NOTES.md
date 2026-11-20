Desktop alpha for Windows, macOS and Linux.

- Log in with Spotify on the Import page, then bring over some or all of your playlists and your mixes (Daily Mix, Discover Weekly, Release Radar and more). The sign-in session is kept encrypted on this computer.
- Make a clean version in the song menu for explicit songs. Both the explicit and clean versions stay in your library, side by side. If no clean version turns up on its own, pick a YouTube upload from a list, with a preview.
- Your mixes on Home: an artist mix for each favorite artist, plus Chill, Upbeat and Late Night mixes.
- New episodes row and a blue dot on followed shows that have an episode you haven't opened.
- Home shows your last mixes at once on launch while fresh ones load.
- Search's Browse all has more genres, moods and podcast topics.
- A playlist or album's big play button pauses what it started. Lyrics load as soon as a song starts.
- New layout: Home, Search, Discover and Your Library tabs across the top, with search that opens in place on every page, and a profile tab for settings.
- Light mode, plus a "match my computer" option.
- Songs, albums, playlists, podcasts and artists each have their own cover shape and badge.
- Customize Home: pin, reorder, hide or add rows from your library, and turn recommendation rows off.
- Discover tab with 30-second previews by genre, and Saves for things you want to hear later.
- Artist pages with Overview, Releases (list or grid, collapsible) and About tabs. Album pages show every contributing artist.
- Select several songs at once to like, queue, save or add them to a playlist. Create and name a new playlist while adding songs.
- Like and Add to playlist are separate buttons. Song menus add Save for later, Go to artist, Song credits, Sleep timer and Hide song.
- Queue grouped by where songs came from, with remove, reorder, shuffle-group and clear controls.
- Player bar tinted by the cover art while playing, a lyrics popup with adjustable text size, and tap a lyric line to jump to it.
- Song radio and artist radio, and Autoplay that keeps playing similar songs when your queue ends (can be turned off in Profile).
- Artist pages show monthly listeners, followers, verified status, the latest release and Fans also like.
- Your Library in the sidebar is grouped into Playlists, Saves, Artists and Podcasts, with a full-page view, sorting and smart playlists by artist.

- Fixes Linux subtitle generation from streamed podcasts by keeping publisher HTTPS and byte-range requests in the app's validated network transport.
- Handles repeated RSS/iTunes title and duration fields, including NPR feeds.

- Automatic offline podcast subtitles with bundled multilingual Whisper Tiny when publisher text is missing. Generated cues are timed and cached; first-play processing takes time and recognition may contain errors.
- Tap cover art for lyrics or subtitles and expand the text to full screen.
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
