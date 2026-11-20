import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile,writeFile,rename,mkdir,stat } from 'node:fs/promises';
import { createWriteStream,createReadStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import path from 'node:path';
import { createHash,randomUUID } from 'node:crypto';
import {isIP} from 'node:net';
import { publicRequest } from './public-network.mjs';

const run=promisify(execFile);
export class Resolver {
  constructor(directory,binary,runtime,runner=run,requester=publicRequest){this.request=requester;this.directory=directory;this.binary=binary;this.runtime=runtime;this.run=runner;this.cache=new Map();this.active=new Map();this.tokens=new Map();this.tail=Promise.resolve();}
  async load(){await mkdir(this.directory,{recursive:true});try{for(const entry of JSON.parse(await readFile(path.join(this.directory,'sources.json'),'utf8')))if(entry.expires>Date.now()||entry.file)this.cache.set(entry.id,entry);}catch(e){if(e.code!=='ENOENT')throw e;}}
  persist(){this.tail=this.tail.catch(()=>{}).then(async()=>{const file=path.join(this.directory,'sources.json');const entries=[...this.cache.values()];const values=[...entries.filter(x=>x.file),...entries.filter(x=>!x.file&&x.expires>Date.now()).slice(-500)];await writeFile(file+'.tmp',JSON.stringify(values),{mode:0o600});await rename(file+'.tmp',file);});return this.tail;}
  /** YouTube uploads for a query, without resolving any streams. */
  async search(query,count=10){
    const q=String(query||'').replace(/\s+/g,' ').trim().slice(0,200);if(q.length<2)return[];
    let stdout;try{({stdout}=await this.run(this.binary,['--flat-playlist','--no-warnings','--socket-timeout','15','--dump-single-json','--',`ytsearch${Math.max(1,Math.min(20,count))}:${q}`],{timeout:45000,maxBuffer:12e6,windowsHide:true,env:{...process.env,ELECTRON_RUN_AS_NODE:'1'}}));}
    catch{throw Error('YouTube search is unavailable. Try again.');}
    const data=JSON.parse(stdout);
    return (Array.isArray(data.entries)?data.entries:[]).filter(x=>/^[A-Za-z0-9_-]{11}$/.test(x?.id)).map(x=>({videoId:x.id,title:String(x.title||'').slice(0,300),channel:String(x.channel||x.uploader||'').slice(0,200),durationMs:Math.max(0,Math.round((Number(x.duration)||0)*1000)),views:Number(x.view_count)||0}));
  }
  async resolve(track,force=false,codec='auto'){
    const saved=this.cache.get(track.isrc);
    if(saved?.file){try{await stat(path.join(this.directory,saved.file));return this.token(saved);}catch{this.cache.delete(track.isrc);}}
    if(!force&&saved?.expires>Date.now()+60000)return this.token(saved);
    if(this.active.has(track.isrc))return this.active.get(track.isrc);
    const promise=(async()=>{
      let url,mime='audio/mp4',videoId=null,extra={};
      if(track.source==='PODCAST'){url=track.directAudioUrl;if(!url)throw Error('This episode has no audio enclosure.');mime='audio/mpeg';}
      else{
        let targetId=track.youtubeVideoId||saved?.videoId;
        const execute=async(hls=false)=>{
          const target=targetId?`https://www.youtube.com/watch?v=${targetId}`:`ytsearch1:${track.artist} ${track.title} ${hls?'official music video':'official audio'}`;
          const format=hls?'bestaudio[protocol^=m3u8]/worst[protocol^=m3u8]/bestaudio':codec==='opus'?'bestaudio[acodec=opus][ext=webm]/bestaudio[acodec=opus]/bestaudio[ext=m4a]/bestaudio':'bestaudio[ext=m4a]/bestaudio[acodec=opus]/bestaudio/best';
          let stdout;
          try{({stdout}=await this.run(this.binary,['--no-playlist','--no-warnings','--socket-timeout','15','--js-runtimes',`node:${this.runtime}`,'--extractor-args',`youtube:player_client=${hls?'web_safari':'visionos'}`,'--dump-single-json','-f',format,'--',target],{timeout:55000,maxBuffer:12e6,windowsHide:true,env:{...process.env,ELECTRON_RUN_AS_NODE:'1'}}));}
          catch(e){console.warn('Music lookup error:',String(e.stderr||e.message).replace(/https?:\/\/\S+/g,'[url]').slice(-1000));throw Error('Could not find an available music stream. Try another song or update Cadence.');}
          const data=JSON.parse(stdout),item=data.entries?.[0]||data;
          const u=new URL(item.url);if(u.protocol!=='https:'||!(u.hostname.endsWith('.googlevideo.com')))throw Error('The music provider returned an unsupported stream.');
          targetId=/^[A-Za-z0-9_-]{11}$/.test(item.id)?item.id:targetId;
          const headers={};for(const [name,value]of Object.entries(item.http_headers||{}))if(['user-agent','accept','accept-language','origin','referer'].includes(name.toLowerCase())&&typeof value==='string'&&value.length<2000)headers[name]=value;
          const pieces=u.pathname.split('/'),signedAddress=u.searchParams.get('ip')||decodeURIComponent(pieces[pieces.indexOf('ip')+1]||'');
          return {url:item.url,videoId:targetId,headers,family:isIP(signedAddress)||undefined,kind:String(item.protocol).startsWith('m3u8')?'hls':'direct',mime:item.ext==='webm'?'audio/webm':item.ext==='ogg'?'audio/ogg':'audio/mp4'};
        };
        let resolved=await execute(codec==='hls');
        if(resolved.kind!=='hls'){
          const probe=await this.request(resolved.url,{range:'bytes=0-0',headers:resolved.headers,family:resolved.family});const status=probe.statusCode;probe.destroy();
          if(status===403||status===410){
            try{resolved=await execute(true);}catch(error){if(track.youtubeVideoId)throw error;targetId=null;resolved=await execute(true);}
          }
          else if(status!==200&&status!==206)throw Error(`Music provider returned HTTP ${status}. Try another song.`);
        }
        ({url,mime,videoId}=resolved);extra={kind:resolved.kind,headers:resolved.headers,family:resolved.family};
      }
      const u=new URL(url);if(u.protocol!=='https:')throw Error('Only HTTPS audio is supported.');
      const remoteExpiry=u.searchParams.has('expire')?Number(u.searchParams.get('expire'))*1000-120000:null;
      if(remoteExpiry!==null && (!Number.isFinite(remoteExpiry)||remoteExpiry<Date.now()+60000))throw Error('The source expired before playback. Try again.');
      const entry={id:track.isrc,url,mime,videoId,...extra,expires:Math.min(Date.now()+3*3600000,remoteExpiry===null?Date.now()+3*3600000:remoteExpiry)};
      this.cache.set(track.isrc,entry);await this.persist();return this.token(entry);
    })();
    this.active.set(track.isrc,promise);try{return await promise;}finally{this.active.delete(track.isrc);}
  }
  token(entry){const token=randomUUID();if(this.tokens.size>=10000)this.tokens.delete(this.tokens.keys().next().value);this.tokens.set(token,entry);return {url:`spot://app/audio/${token}`,saved:!!entry.file,kind:entry.kind||'direct'};}
  async response(request){
    const token=new URL(request.url).pathname.split('/').at(-1),entry=this.tokens.get(token);if(!entry)return new Response('Unknown audio',{status:404});
    if(entry.kind==='hls')return this.manifest(entry);
    const range=request.headers.get('range');if(range&&!/^bytes=\d*-\d*$/.test(range))return new Response(null,{status:416});
    if(entry.file){
      const file=path.join(this.directory,entry.file),size=(await stat(file)).size;
      let start=0,end=size-1;
      if(range){const [a,b]=range.slice(6).split('-');if(a==='')start=Math.max(0,size-Number(b));else{start=Number(a);if(b!=='')end=Math.min(end,Number(b));}if(start>end||start>=size)return new Response(null,{status:416,headers:{'Content-Range':`bytes */${size}`}});}
      return new Response(Readable.toWeb(createReadStream(file,{start,end})),{status:range?206:200,headers:{'Content-Type':entry.mime,'Content-Length':String(end-start+1),'Accept-Ranges':'bytes',...(range?{'Content-Range':`bytes ${start}-${end}/${size}`}:{})}});
    }
    const r=await this.request(entry.url,{range,headers:entry.headers,family:entry.family});
    if([403,410].includes(r.statusCode)){this.cache.delete(entry.id);await this.persist();}
    const headers={'Content-Type':r.headers['content-type']||entry.mime,'Accept-Ranges':'bytes'};
    for(const name of ['content-length','content-range'])if(r.headers[name])headers[name]=r.headers[name];
    return new Response(Readable.toWeb(r),{status:r.statusCode,headers});
  }
  async manifest(entry){
    let body;
    if(entry.file)body=await readFile(path.join(this.directory,entry.file),'utf8');
    else{
      const r=await this.request(entry.url,{headers:entry.headers,family:entry.family});
      if(r.statusCode<200||r.statusCode>=300){r.destroy();return new Response(null,{status:r.statusCode});}
      const chunks=[];let size=0;for await(const chunk of r){size+=chunk.length;if(size>2e6){r.destroy();throw Error('Manifest is too large.');}chunks.push(chunk);}body=Buffer.concat(chunks).toString('utf8');
    }
    if(!body.trimStart().startsWith('#EXTM3U'))throw Error('Invalid music manifest.');
    const rewritten=rewriteManifest(body,entry.url,(url,isManifest)=>{
      const offline=entry.offlineAssets?.[url];
      if(entry.file&&!offline)throw Error('An offline audio segment is missing.');
      return this.token({id:entry.id,url,headers:entry.headers,family:entry.family,kind:isManifest?'hls':'segment',mime:isManifest?'application/vnd.apple.mpegurl':'application/octet-stream',...(offline?{file:offline,offlineAssets:entry.offlineAssets}:{}),expires:entry.expires}).url;
    });
    return new Response(rewritten,{headers:{'Content-Type':'application/vnd.apple.mpegurl','Cache-Control':'no-store'}});
  }
  async downloadHls(track,entry){
    const folder=createHash('sha256').update(track.isrc).digest('hex'),directory=path.join(this.directory,folder);
    await mkdir(directory,{recursive:true});const assets={};let bytes=0,count=0;
    const save=async(url,depth=0)=>{
      if(assets[url])return assets[url];if(++count>5000||depth>4)throw Error('Audio collection is too large to save.');
      const r=await this.request(url,{headers:entry.headers,family:entry.family});if(r.statusCode!==200){r.destroy();throw Error('An audio segment could not be downloaded.');}
      const chunks=[];for await(const chunk of r){bytes+=chunk.length;if(bytes>500e6){r.destroy();throw Error('Audio file is too large.');}chunks.push(chunk);}
      const data=Buffer.concat(chunks),isManifest=data.subarray(0,32).toString().trimStart().startsWith('#EXTM3U');
      const name=path.join(folder,createHash('sha256').update(url).digest('hex')+(isManifest?'.m3u8':'.segment'));assets[url]=name;
      await writeFile(path.join(this.directory,name),data,{mode:0o600});
      if(isManifest){const children=[];rewriteManifest(data.toString('utf8'),url,child=>{children.push(child);return child;});for(const child of [...new Set(children)])await save(child,depth+1);}
      return name;
    };
    const file=await save(entry.url);
    this.cache.set(track.isrc,{...entry,file,offlineAssets:assets,track,expires:0});await this.persist();return {saved:true};
  }
  async download(track){
    const source=await this.resolve(track),entry=this.tokens.get(new URL(source.url).pathname.split('/').at(-1));if(entry.file)return {saved:true};
    if(entry.kind==='hls')return this.downloadHls(track,entry);
    const file=createHash('sha256').update(track.isrc).digest('hex')+'.audio';
    const r=await this.request(entry.url,{headers:entry.headers,family:entry.family});if(r.statusCode!==200){r.destroy();throw Error('Audio download failed.');}
    let size=0;r.on('data',chunk=>{size+=chunk.length;if(size>500e6)r.destroy(Error('Audio file is too large.'));});
    const dest=path.join(this.directory,file);
    await pipeline(r,createWriteStream(dest+'.part',{mode:0o600}));await rename(dest+'.part',dest);
    this.cache.set(track.isrc,{id:track.isrc,file,track,mime:r.headers['content-type']||entry.mime,expires:0});await this.persist();return{saved:true};
  }
}

export function rewriteManifest(body,base,replace){
  let count=0;
  const link=raw=>{if(++count>5000)throw Error('Too many audio segments.');const u=new URL(raw,base);if(u.protocol!=='https:'||!u.hostname.endsWith('.googlevideo.com'))throw Error('Unsupported audio segment host.');return replace(u.href,/\.m3u8$/.test(u.pathname));};
  return body.split(/\r?\n/).map(line=>{
    if(!line.trim())return line;
    if(line.startsWith('#'))return line.replace(/URI="([^"]+)"/g,(_all,url)=>`URI="${link(url)}"`);
    return link(line.trim());
  }).join('\n');
}
