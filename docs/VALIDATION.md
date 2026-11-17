# Validation

Tested locally on November 16, 2026, using JDK 21 and Android SDK 35.

- `:app:assembleDebug` passes (universal APK).
- `:app:assembleRelease -Pgamut.abis=arm64-v8a` passes locally; the APK signature verifies and both upstream license texts are bundled as assets.
- `:app:assembleDebugAndroidTest` passes (instrumentation APK compiles).
- Shared music/podcast search, Home shortcuts, Library podcast rows and theme text-color inheritance compile in both app and instrumentation APKs.
- Private saved-source cache tests pass across restart, expiry and explicit invalidation.
- Large feed and legacy HTTP-to-HTTPS upgrade tests pass. Live feeds pass for The Journal, Darknet Diaries, Hard Fork, 99% Invisible, Reply All and The Joe Rogan Experience.
- `testDebugUnitTest` passes. Optional live tests are skipped unless `SPOT_LIVE_SMOKE=1` is set.
- The live SpotAPI catalog smoke returned 25 results for “Daft Punk Get Lucky” in about 2.7 seconds from a cold session.
- The live podcast check searched the directory, parsed a NASA publisher feed and read audio bytes from an enclosure. The latest check reached the first response bytes in about 0.9 seconds after feed selection.
- `:tools:extractor-smoke:run --args=--quick` passed music extraction/audio range and song-search checks (2/2). Cold extraction plus first audio bytes took about 2.3 seconds on this connection.
- Search tests verify that playable results arrive before a slow Spotify lookup completes, and that a strongly matched source ID is retained on Spotify catalog metadata.
- Resolver tests verify that known source IDs avoid repeating candidate search and remote lookup.
- Feed parser tests cover audio enclosures, durations, stable IDs, relative URLs, duplicate entries, insecure/video enclosures, external entities and oversized input.

These timings are individual network smoke checks, not measured Android tap-to-audio latency. Networks, extractor changes, source availability and cache state affect playback.

The local Android software emulator crashed before screen verification could finish. GitHub Actions subsequently passed both Android instrumentation checks: navigation between Home, Search, Library and Podcasts, and parsing a publisher-style feed on Android. Home, Search and Podcasts screenshots were captured. The first cloud release compilation then reached its JVM metaspace limit, so the workflow now runs UI checks, unit tests and release compilation in fresh Gradle processes with a larger metaspace limit. APK publication is being retried. No physical-device or Android Auto validation has been performed.

Alpha APKs use a separate application ID (`com.lukeherc.spotmusic`) and development signing. The previous Cadence project is preserved separately and has no role in this fork's playback.
