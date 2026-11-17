import {writeFile,mkdir} from 'node:fs/promises';
import {deflateSync} from 'node:zlib';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const size=512,data=Buffer.alloc((size*4+1)*size);
for(let y=0;y<size;y++){const row=y*(size*4+1);for(let x=0;x<size;x++){const at=row+1+x*4;const bar=x>=112&&x<180&&y>=220&&y<376||x>=222&&x<290&&y>=128&&y<376||x>=332&&x<400&&y>=176&&y<376;data[at]=bar?30:18;data[at+1]=bar?215:18;data[at+2]=bar?96:18;data[at+3]=255;}}
function crc(buffer){let c=0xffffffff;for(const b of buffer){c^=b;for(let n=0;n<8;n++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function chunk(type,body){const tag=Buffer.from(type),length=Buffer.alloc(4),checksum=Buffer.alloc(4);length.writeUInt32BE(body.length);checksum.writeUInt32BE(crc(Buffer.concat([tag,body])));return Buffer.concat([length,tag,body,checksum]);}
const header=Buffer.alloc(13);header.writeUInt32BE(size);header.writeUInt32BE(size,4);header[8]=8;header[9]=6;
const dir=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../resources');await mkdir(dir,{recursive:true});
await writeFile(path.join(dir,'icon.png'),Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',header),chunk('IDAT',deflateSync(data)),chunk('IEND',Buffer.alloc(0))]));
