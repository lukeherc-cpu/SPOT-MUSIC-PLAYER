import {readdir,readFile,writeFile,copyFile} from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
const platform=process.env.PLATFORM,arch=process.env.ARCH,tag=process.env.RELEASE_TAG;
if(!platform||!arch||!tag)throw Error('Missing release identifiers.');
const files=(await readdir('dist')).filter(x=>/\.(exe|dmg|zip|AppImage|deb)$/.test(x)).map(x=>path.join('dist',x));
const sums=[];for(const file of files)sums.push(`${createHash('sha256').update(await readFile(file)).digest('hex')}  ${path.basename(file)}`);
const checksum=`dist/SHA256SUMS-${platform}-${arch}.txt`;await writeFile(checksum,sums.join('\n')+'\n');files.push(checksum);
for(const name of ['home','player']){const dest=`dist/desktop-${platform}-${arch}-${name}.png`;await copyFile(`test-results/screenshots/${name}.png`,dest);files.push(dest);}
execFileSync('gh',['release','upload',tag,...files,'--clobber','--repo',process.env.GITHUB_REPOSITORY],{stdio:'inherit'});
