# Spot Music desktop

One Electron desktop app packaged for Windows x64, macOS Intel/Apple Silicon and Linux x64. This is a local desktop application, with no separately hosted service. Libraries are currently local to each installation.

Node.js 24 is required for development; packaged apps include their own runtime and audio resolver.

```sh
npm ci
npx --no install-electron --no
npm run prepare:resolver
npm test
npm run test:ui
npm start
npm run dist
```

On a headless Linux runner, use `xvfb-run -a npm run test:ui`. The test disables Electron's OS sandbox only for its isolated test process; production windows retain Chromium sandboxing, context isolation and disabled Node integration.

`src/catalog.mjs` adapts the same public SpotAPI protocol used by Android. `src/discovery.mjs` supplies category pages and local-history mixes. Podcast feeds and audio requests validate public DNS addresses, pin the validated addresses for the connection, bound redirects and reject XML external entities. Only a fixed, validated IPC surface is exposed to the local renderer. Source URLs stay in the main process and audio is served through a private application protocol.

The release workflow runs unit and real Electron navigation/local-playback checks on each target OS before producing installers. Packages include the pinned, checksum-verified yt-dlp binary; its release is recorded in `scripts/resolver.mjs` and in each package's resolver manifest. First-time music playback still depends on source lookup and availability. Repeat playback reuses saved source URLs until expiry or saved local files when available.

Installers are development alpha builds; Windows installers are unsigned, and macOS builds are unsigned and unnotarized. No developer certificate or publishing credential is embedded. See [attribution](THIRD_PARTY.md).
