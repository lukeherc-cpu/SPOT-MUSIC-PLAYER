# Player and discovery update

Android source uses the public Spotify catalogue adaptation from Aran404/SpotAPI. Artist identities, explicit ratings and the largest artwork source now survive local library storage. Room v11 adds an optional metadata column; v9 and v10 libraries migrate without deleting favorites, playlist membership or saved-file paths.

The save action opens a `Saved in` sheet after adding an unsaved track to Liked Songs. Liked status and other playlist memberships can be changed in the same sheet. Linked artist credits open a picker for all credited artists. Artist profiles expose public listener statistics, biography, popular songs, discography and latest releases when Spotify supplies those fields; the right-hand navigation rail supports taps and thumb drags.

Home removes the duplicate Listen again section, keeps Recently played, refreshes on pull-down, and uses local music and podcast listening history for its own recommendations. Larger release and episode cards use real catalogue/feed content. Music-video cards explicitly open YouTube. The YouTube search filter preserves the chosen video identity, including clean-version searches; videos without an explicit-content rating remain unlabelled.

The player has a fixed layout and a square cover/text slot. Tapping the art performs a 380 ms 3D flip; lyrics or publisher subtitles occupy the same square. The expand control opens a full-screen reading panel. Podcasting 2.0 VTT, SRT, JSON and plain-text transcripts are supported; plain text is never given invented timestamps. Episodes whose publisher supplies no transcript show an availability message. No speech-recognition service or uploaded audio is used.

The cover flip and mini-player container transform adapt ReadAlong's GPL-3.0 player interactions at commit 6e708f88581b93a50bcd6285d2f1589b338ea655. Its source and license are credited in Settings and `docs/licenses/ReadAlong-GPL-3.0.txt`:
- https://github.com/readalongapp/ReadAlong/blob/6e708f88581b93a50bcd6285d2f1589b338ea655/app/src/main/java/com/example/readalong/ui/player/PlayerScreen.kt
- https://github.com/readalongapp/ReadAlong/blob/6e708f88581b93a50bcd6285d2f1589b338ea655/app/src/main/java/com/example/readalong/ui/player/PlayerSheet.kt

## Android update compatibility

Android verifies package identity, version code and signing identity when updating an installed app. Development builds have two established signing identities:

| Download channel | Certificate SHA256 |
| --- | --- |
| Local APK from this chat | `c9ae87a69f11c3e4852f64aaac4c32d7bb2891b09fc1883ea2d378314576a0e0` |
| GitHub Releases | `bab3f8d16bf11feee9b15791faac0909e1e9840b27fb79bf21e07a22e3fdce4b` |

Use the same channel as the installed APK. Both identities are retained; they cannot be exchanged by an ordinary APK update. Settings now shows the installed update channel. Alpha 9 has version code 10009 and the same package `com.lukeherc.spotmusic`. The release workflow refuses publication if the GitHub signing certificate changes. Its emulator check installs an alpha-8-source APK, seeds a real v10 library, uses `adb install -r` for the new build and verifies preserved favorites, playlist order, files and preferences.

## Desktop playback fix

The Linux failure was reproduced with fresh provider URLs returning HTTP 403 while saved sources could still play. The resolver now explicitly selects the verified `visionos` provider client, validates fresh direct streams, carries required headers and keeps the signed stream's IP family. Recovery can choose Opus or HLS. The bundled hls.js player handles manifests and segments through opaque app URLs; HLS downloads save their actual manifest and media for offline replay. Explicitly chosen YouTube IDs are retained during recovery. No separate server is required.

Validation includes unit checks for source rejection/recovery and offline HLS persistence, a synthetic AAC transport-stream fixture decoded by the packaged UI, and an opt-in `node scripts/live-audio-check.mjs` check through normal Play buttons in an isolated profile. A Linux AppImage check played fresh Rick Astley and Daft Punk streams; provider availability and network conditions can still vary. Local libraries are not changed by these checks.
