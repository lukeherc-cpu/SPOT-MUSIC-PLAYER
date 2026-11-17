# Podcasts

Home → Podcasts opens podcast directory search. Your Library → Your podcasts opens followed shows. Search uses Apple's public podcast directory; audio streams directly from publishers' RSS `<enclosure>` URLs through the same Media3 player, background media session, audio cache and queue used for music. No hosted proxy, Real-Debrid account, Prowlarr, Soulseek or torrent client is involved.

Follow/unfollow and episode positions are stored locally. Tapping Resume restores the last saved position. Progress is saved about every five seconds and on pause. The full player includes Back 15 sec / Forward 15 sec buttons. Previous/Next move between episodes. Optional Gamut account sync and the existing music backup exporter do not include podcast follows or positions. Publisher audio URLs are not included in exported music backups.

The reader supports ordinary RSS feeds, HTTPS audio enclosures and duration tags. It rejects external XML entities, oversized feeds, non-audio enclosures and unsafe feed redirects. Feeds are capped at 500 items and the in-memory episode cache at 20 shows. HTTP-only, login-only, paid and Spotify-exclusive shows have no supported publisher audio enclosure and cannot play through this reader. Directory results may include a show whose publisher no longer has a working feed; refresh errors appear in the episode screen.
