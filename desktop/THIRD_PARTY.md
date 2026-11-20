# Desktop attribution

Cadence is based on serptail/Gamut (AGPL-3.0). The native Spotify catalog adapter follows Aran404/SpotAPI and retains its GPL-3.0-only notice. Spotify provides metadata, not the audio played by this app.

- Gamut: https://github.com/serptail/gamut — AGPL-3.0.
- SpotAPI: https://github.com/Aran404/SpotAPI — GPL-3.0. Its license and the project's AGPL text are included with every desktop package.
- Electron: https://github.com/electron/electron — MIT; Chromium, Node.js and bundled component notices ship in Electron's license files.
- yt-dlp: https://github.com/yt-dlp/yt-dlp — source under Unlicense; standalone releases include additional components under their respective licenses, including GPLv3+. The exact binary release and SHA256 are recorded in resources/resolver/VERSION.json. Release source, component licenses and notices: https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26 and https://github.com/yt-dlp/yt-dlp/blob/master/THIRD_PARTY_LICENSES.txt.
- fast-xml-parser: https://github.com/NaturalIntelligence/fast-xml-parser — MIT.
- hls.js: https://github.com/video-dev/hls.js — BSD-2-Clause; its license is bundled. HLS manifests, keys and segments are served through opaque app URLs and verified public HTTPS requests.

The app has no torrent client and needs no separate server. Anonymous Spotify browsing cannot access private playlists or Spotify's account-specific Daily Mixes. Cadence builds its own mixes from locally stored listening history and public Spotify metadata.
# Offline subtitles

Whisper Tiny model weights are MIT licensed by OpenAI. whisper.cpp v1.9.5 is MIT licensed by the ggml authors. Both licenses are bundled in the application. The model and native source downloads are pinned and SHA256 verified by `scripts/prepare_speech.py`.

The FFmpeg decoder comes from the checksum-pinned platform assets of [ffmpeg-static b6.1.1](https://github.com/eugeneware/ffmpeg-static/releases/tag/b6.1.1). The platform's exact license, build attribution and source locations are retained alongside the decoder as `speech/FFmpeg-LICENSE.txt` and `speech/FFmpeg-README.txt`. Different platforms may carry different FFmpeg builds; b6.1.1 identifies the binary distribution, not FFmpeg's own version.
