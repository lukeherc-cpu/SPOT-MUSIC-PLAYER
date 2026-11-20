import {execFileSync} from 'node:child_process';
import path from 'node:path';
const binary=process.platform==='darwin'?path.join('dist',process.arch==='arm64'?'mac-arm64':'mac','Cadence.app/Contents/MacOS/Cadence'):process.platform==='win32'?path.join('dist','win-unpacked','Cadence.exe'):path.join('dist','linux-unpacked','spot-music-desktop');
const executable=path.resolve(binary);
const version=execFileSync(executable,['--version'],{env:{...process.env,ELECTRON_RUN_AS_NODE:'1'},encoding:'utf8'}).trim();
if(!/^v\d+\./.test(version))throw Error('Packaged JavaScript runtime did not start.');
execFileSync(process.execPath,['node_modules/@playwright/test/cli.js','test'],{env:{...process.env,SPOT_PACKAGED_EXEC:executable},stdio:'inherit'});
