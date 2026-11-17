import {mkdir,writeFile,chmod,rename,readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import './icon.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const version='2026.10.26';
const name=process.platform==='win32'?'yt-dlp.exe':process.platform==='darwin'?'yt-dlp_macos':process.arch==='arm64'?'yt-dlp_linux_aarch64':'yt-dlp_linux';
const dir=path.join(root,'resources/resolver');await mkdir(dir,{recursive:true});
const base=`https://github.com/yt-dlp/yt-dlp/releases/download/${version}/`;
async function fetchBytes(name){const r=await fetch(base+name,{signal:AbortSignal.timeout(180000)});if(!r.ok)throw Error(`${name}: HTTP ${r.status}`);return Buffer.from(await r.arrayBuffer());}
const sums=(await fetchBytes('SHA2-256SUMS')).toString();
const expected=sums.split('\n').find(line=>line.trim().endsWith(' '+name))?.split(/\s+/)[0];
if(!expected||!/^[a-f0-9]{64}$/.test(expected))throw Error('No trusted release checksum for resolver.');
const dest=path.join(dir,process.platform==='win32'?'yt-dlp.exe':'yt-dlp');
let existing;try{existing=await readFile(dest);}catch{}
if(!existing||createHash('sha256').update(existing).digest('hex')!==expected){const bytes=await fetchBytes(name);if(createHash('sha256').update(bytes).digest('hex')!==expected)throw Error('Resolver checksum mismatch.');await writeFile(dest+'.tmp',bytes);await rename(dest+'.tmp',dest);}
if(process.platform!=='win32')await chmod(dest,0o755);
await writeFile(path.join(dir,'VERSION.json'),JSON.stringify({version,asset:name,sha256:expected,source:'https://github.com/yt-dlp/yt-dlp'}));
for(const name of ['LICENSE','THIRD_PARTY_LICENSES.txt']){
  const response=await fetch(`https://raw.githubusercontent.com/yt-dlp/yt-dlp/${version}/${name}`,{signal:AbortSignal.timeout(30000)});
  if(!response.ok)throw Error('Could not bundle resolver license notices.');
  await writeFile(path.join(dir,name),await response.text());
}
console.log(`Bundled yt-dlp ${version} (${name}), SHA256 verified`);
