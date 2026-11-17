import {readdir,readFile,stat} from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
const platform=process.platform;
const root=platform==='darwin'?path.join('dist',(process.env.SPOT_TARGET_ARCH||process.arch)==='arm64'?'mac-arm64':'mac','Spot Music.app/Contents/Resources'):path.join('dist',platform==='win32'?'win-unpacked':'linux-unpacked','resources');
for(const file of ['app.asar','resolver/VERSION.json','licenses/Spot-Music-AGPL-3.0.txt','licenses/SpotAPI-GPL-3.0.txt','licenses/HLS-js-BSD-2-Clause.txt']){const s=await stat(path.join(root,file));if(s.size<10)throw Error(`Missing packaged resource: ${file}`);}
const manifest=JSON.parse(await readFile(path.join(root,'resolver/VERSION.json'),'utf8'));
const binary=await readFile(path.join(root,'resolver',platform==='win32'?'yt-dlp.exe':'yt-dlp'));
if(createHash('sha256').update(binary).digest('hex')!==manifest.sha256)throw Error('Packaged resolver checksum mismatch.');
const files=(await readdir('dist')).filter(x=>/\.(exe|dmg|zip|AppImage|deb)$/.test(x));if(!files.length)throw Error('No desktop installer was produced.');
for(const file of files)if((await stat(path.join('dist',file))).size<1e6)throw Error('Installer is unexpectedly small.');
console.log('Verified native installers, app archive, licenses and resolver:',files.join(', '));
