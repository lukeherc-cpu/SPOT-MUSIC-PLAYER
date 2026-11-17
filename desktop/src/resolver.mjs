import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile,writeFile,rename,mkdir,stat } from 'node:fs/promises';
import { createWriteStream,createReadStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import path from 'node:path';
import { createHash,randomUUID } from 'node:crypto';
import { publicRequest } from './public-network.mjs';

const run=promisify(execFile);
export class Resolver {
  constructor(directory,binary,runtime,runner=run){this.directory=directory;this.binary=binary;this.runtime=runtime;this.run=runner;this.cache=new Map();this.active=new Map();this.tokens=new Map();this.tail=Promise.resolve();}
  async load(){await mkdir(this.directory,{recursive:true});try{for(const entry of JSON.parse(await readFile(path.join(this.directory,'sources.json'),'utf8')))if(entry.expires>Date.now()||entry.file)this.cache.set(entry.id,entry);}catch(e){if(e.code!=='ENOENT')throw e;}}
  persist(){this.tail=this.tail.catch(()=>{}).then(async()=>{const file=path.join(this.directory,'sources.json');const values=[...this.cache.values()].filter(x=>x.file||x.expires>Date.now()).slice(-500);await writeFile(file+'.tmp',JSON.stringify(values),{mode:0o600});await rename(file+'.tmp',file);});return this.tail;}
  async resolve(track,force=false){
    const saved=this.cache.get(track.isrc);
    if(saved?.file){try{await stat(path.join(this.directory,saved.file));return this.token(saved);}catch{this.cache.delete(track.isrc);}}
    if(!force&&saved?.expires>Date.now()+60000)return this.token(saved);
    if(this.active.has(track.isrc))return this.active.get(track.isrc);
    const promise=(async()=>{
      let url,mime='audio/mp4',videoId=null;
      if(track.source==='PODCAST'){url=track.directAudioUrl;if(!url)throw Error('This episode has no audio enclosure.');mime='audio/mpeg';}
      else{
        const target=saved?.videoId?`https://www.youtube.com/watch?v=${saved.videoId}`:`ytsearch1:${track.artist} ${track.title} official audio`;
        let stdout;
        try{({stdout}=await this.run(this.binary,['--no-playlist','--no-warnings','--socket-timeout','15','--js-runtimes',`node:${this.runtime}`,'--dump-single-json','-f','bestaudio[ext=m4a]/bestaudio[acodec=opus]/bestaudio/best','--',target],{timeout:55000,maxBuffer:12e6,windowsHide:true,env:{...process.env,ELECTRON_RUN_AS_NODE:'1'}}));}
        catch{throw Error('Could not find an available music stream. Try another song or update Spot Music.');}
        const data=JSON.parse(stdout),item=data.entries?.[0]||data;
        url=item.url;videoId=/^[A-Za-z0-9_-]{11}$/.test(item.id)?item.id:null;
        mime=item.ext==='webm'?'audio/webm':item.ext==='ogg'?'audio/ogg':'audio/mp4';
        const u=new URL(url);if(u.protocol!=='https:'||!u.hostname.endsWith('.googlevideo.com'))throw Error('The music provider returned an unsupported stream.');
      }
      const u=new URL(url);if(u.protocol!=='https:')throw Error('Only HTTPS audio is supported.');
      const remoteExpiry=u.searchParams.has('expire')?Number(u.searchParams.get('expire'))*1000-120000:null;
      if(remoteExpiry!==null && (!Number.isFinite(remoteExpiry)||remoteExpiry<Date.now()+60000))throw Error('The source expired before playback. Try again.');
      const entry={id:track.isrc,url,mime,videoId,expires:Math.min(Date.now()+3*3600000,remoteExpiry===null?Date.now()+3*3600000:remoteExpiry)};
      this.cache.set(track.isrc,entry);await this.persist();return this.token(entry);
    })();
    this.active.set(track.isrc,promise);try{return await promise;}finally{this.active.delete(track.isrc);}
  }
  token(entry){const token=randomUUID();if(this.tokens.size>=250)this.tokens.delete(this.tokens.keys().next().value);this.tokens.set(token,entry);return {url:`spot-audio://stream/${token}`,saved:!!entry.file};}
  async response(request){
    const token=new URL(request.url).pathname.slice(1),entry=this.tokens.get(token);if(!entry)return new Response('Unknown audio',{status:404});
    const range=request.headers.get('range');if(range&&!/^bytes=\d*-\d*$/.test(range))return new Response(null,{status:416});
    if(entry.file){
      const file=path.join(this.directory,entry.file),size=(await stat(file)).size;
      let start=0,end=size-1;
      if(range){const [a,b]=range.slice(6).split('-');if(a==='')start=Math.max(0,size-Number(b));else{start=Number(a);if(b!=='')end=Math.min(end,Number(b));}if(start>end||start>=size)return new Response(null,{status:416,headers:{'Content-Range':`bytes */${size}`}});}
      return new Response(Readable.toWeb(createReadStream(file,{start,end})),{status:range?206:200,headers:{'Content-Type':entry.mime,'Content-Length':String(end-start+1),'Accept-Ranges':'bytes',...(range?{'Content-Range':`bytes ${start}-${end}/${size}`}:{})}});
    }
    const r=await publicRequest(entry.url,{range});
    if([403,410].includes(r.statusCode)){this.cache.delete(entry.id);await this.persist();}
    const headers={'Content-Type':r.headers['content-type']||entry.mime,'Accept-Ranges':'bytes'};
    for(const name of ['content-length','content-range'])if(r.headers[name])headers[name]=r.headers[name];
    return new Response(Readable.toWeb(r),{status:r.statusCode,headers});
  }
  async download(track){
    const source=await this.resolve(track),entry=this.tokens.get(new URL(source.url).pathname.slice(1));if(entry.file)return {saved:true};
    const file=createHash('sha256').update(track.isrc).digest('hex')+'.audio';
    const r=await publicRequest(entry.url);if(r.statusCode!==200){r.destroy();throw Error('Audio download failed.');}
    let size=0;r.on('data',chunk=>{size+=chunk.length;if(size>500e6)r.destroy(Error('Audio file is too large.'));});
    const dest=path.join(this.directory,file);
    await pipeline(r,createWriteStream(dest+'.part',{mode:0o600}));await rename(dest+'.part',dest);
    this.cache.set(track.isrc,{id:track.isrc,file,track,mime:r.headers['content-type']||entry.mime,expires:0});await this.persist();return{saved:true};
  }
}
