# Player and discovery update

Android source uses the public Spotify catalogue adaptation from Aran404/SpotAPI. Artist identities, explicit ratings and the largest artwork source now survive local library storage. Room v11 adds an optional metadata column; v9 and v10 libraries migrate without deleting favorites, playlist membership or saved-file paths.

The save action opens a `Saved in` sheet after adding an unsaved track to Liked Songs. Liked status and other playlist memberships can be changed in the same sheet. Linked artist credits open a picker for all credited artists. Artist profiles expose public listener statistics, biography, popular songs, discography and latest releases when Spotify supplies those fields; the right-hand navigation rail supports taps and thumb drags.

Home removes the duplicate Listen again section, keeps Recently played, refreshes on pull-down, and uses local music and podcast listening history for its own recommendations. Larger release and episode cards use real catalogue/feed content. Music-video cards explicitly open YouTube. The YouTube search filter preserves the chosen video identity, including clean-version searches; videos without an explicit-content rating remain unlabelled.

The player has a fixed layout and a square cover/text slot. Tapping the art performs a 380 ms 3D flip; lyrics or subtitles occupy the same square. Tapping text returns to the cover instead of seeking, including from the expanded reading view. Clean-version lyrics mask explicit words while preserving provider timestamps. Content loading uses skeleton placeholders, including Home, lyrics and subtitles.

Podcasting 2.0 VTT, SRT, JSON and plain-text transcripts are preferred. If a publisher supplies none, or its text cannot be retrieved, bundled multilingual Whisper Tiny generates timed subtitles automatically on the device. Android decodes bounded windows ahead of playback with MediaCodec; desktop bundles its audio decoder and native recognizer. Generated cues and processed windows are saved for future plays and seeks. No microphone access or transcription server is used. Automatic text can contain recognition mistakes and takes processing time on first playback; silence is skipped, and plain publisher text is never given invented timestamps.

The artist rail occupies the middle of the right edge, with a spring-driven thumb indicator, curved label movement and a floating section label inspired by the supplied Niagara recording. Artist credit popups load and cache profile photos. Song rows support left-swipe queueing and long-press menus with contextual local-playlist removal. Shuffle keeps its setting and selects a random collection starting track; explicitly tapped songs remain the starting song.

The cover flip and mini-player container transform adapt ReadAlong's GPL-3.0 player interactions at commit 6e708f88581b93a50bcd6285d2f1589b338ea655. Its source and license are credited in Settings and `docs/licenses/ReadAlong-GPL-3.0.txt`:
- https://github.com/readalongapp/ReadAlong/blob/6e708f88581b93a50bcd6285d2f1589b338ea655/app/src/main/java/com/example/readalong/ui/player/PlayerScreen.kt
- https://github.com/readalongapp/ReadAlong/blob/6e708f88581b93a50bcd6285d2f1589b338ea655/app/src/main/java/com/example/readalong/ui/player/PlayerSheet.kt

## Android update compatibility

Android verifies package identity, version code and signing identity when updating an installed app. The retained local key is:

| Download channel | Certificate SHA256 |
| --- | --- |
| Local APK from this chat | `c9ae87a69f11c3e4852f64aaac4c32d7bb2891b09fc1883ea2d378314576a0e0` |

Alpha 12 uses version code 10012 and package `com.lukeherc.spotmusic`, matching the retained local APK family. Settings shows the signing identity.

The old GitHub workflow cached a path that did not exist. Its logs confirm the key was never saved; public alphas 5, 6 and 8 have different certificates. Those private keys are unavailable, so an in-place update of those APKs cannot be signed. A certificate extracted from an APK cannot replace its private key. Keep a legacy installation installed if its library is needed.

Future GitHub publication requires four durable Actions signing secrets and checks the retained certificate against `.github/android-signing.sha256`; missing or mismatched credentials withhold publication. `scripts/configure_android_signing.py` securely uploads an existing matching key through an authenticated GitHub CLI without committing it. Connected GitHub tools cannot write these secrets.

The emulator check installs an alpha-8-source APK with the current test key, seeds a real v10 library, uses `adb install -r`, and verifies preserved favorites, playlist order, files and preferences. This validates the migration and update flow with matching keys; it cannot overcome the missing private keys of old public releases.

On Android 8–12 the app selects its bundled yt-dlp resolver because NewPipe 0.27 references a java.net API introduced in Android 13. Optional extractor linkage errors also fall back without terminating playback or background preparation.

## Desktop playback fix

The Linux failure was reproduced with fresh provider URLs returning HTTP 403 while saved sources could still play. The resolver now explicitly selects the verified `visionos` provider client, validates fresh direct streams, carries required headers and keeps the signed stream's IP family. Recovery can choose Opus or HLS. The bundled hls.js player handles manifests and segments through opaque app URLs; HLS downloads save their actual manifest and media for offline replay. Explicitly chosen YouTube IDs are retained during recovery. No separate server is required.

Validation includes unit checks for source rejection/recovery and offline HLS persistence, a synthetic AAC transport-stream fixture decoded by the packaged UI, and an opt-in `node scripts/live-audio-check.mjs` check through normal Play buttons in an isolated profile. A Linux AppImage check played fresh Rick Astley and Daft Punk streams; provider availability and network conditions can still vary. Local libraries are not changed by these checks.
