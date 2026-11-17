# Spotify catalog discovery

Spot Music adapts the anonymous public catalog protocol from [Aran404/SpotAPI](https://github.com/Aran404/SpotAPI), baseline commit `13cb48ddc221aa6f7d2bf1b8229bc4b158247916` (SpotAPI 2.1). This is a native Kotlin adaptation, not an embedded Python interpreter or a Python server.

`core/core-network/SpotApiCatalog.kt` follows SpotAPI's web-player session bootstrap, client-token exchange, dynamic `searchDesktop` persisted-query discovery and nested search result parsing. Sessions and tokens stay in memory. Search results have a bounded, ten-minute cache. No Spotify account cookie, developer key or Premium account is required for public catalog search. Requests are restricted to the four Spotify hosts used by the protocol and redirects are disabled.

Spotify track IDs, title, artist, album, artwork and duration enter the shared catalog. The existing Gamut resolver matches those metadata to a playable source; SpotAPI does not supply an audio URL in this integration. This app does not decrypt Spotify audio or use Spotify previews as full songs. Spotify search can change independently of the app; errors show an explicit notice while the other catalog providers continue working.

Search providers publish independently so a cold Spotify session does not hold back other results. Catalog tracks reuse a strongly matched YouTube ID from the same search results when available, so tapping the result need not repeat the candidate search. Visible results begin bounded source prefetching when allowed by the user's preloading and network settings. Gamut's existing audio cache and upcoming-track preload remain active. Playback now starts after 500 ms of audio buffer instead of 1,500 ms; rebuffer recovery keeps its 3,000 ms threshold. The launch screen also stops waiting for remote discovery data after 400 ms instead of up to ten seconds. These are configured thresholds, not a promise of sub-second end-to-end playback. Source extraction, the network and publisher availability still affect first audio.

SpotAPI's GPL-3.0 license is reproduced in `docs/licenses/SpotAPI-GPL-3.0.txt`. Gamut and this derivative application remain AGPL-3.0 under the root LICENSE. The About screen credits both upstream projects.

Run the optional live catalog smoke test with `SPOT_LIVE_SMOKE=1 ./gradlew :core:core-network:testDebugUnitTest`; ordinary tests use recorded envelopes and fake providers without contacting Spotify.
