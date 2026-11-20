import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdir,readFile,writeFile,rename,rm,stat} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {createServer} from 'node:http';
import path from 'node:path';
import {setTimeout as sleep} from 'node:timers/promises';
import {readPublic,secureUrl,publicRequest} from './public-network.mjs';
import {episodes} from './podcasts.mjs';
import {fold,plainTitle} from './search.mjs';

const run=promisify(execFile),WINDOW=20000;
// Native static decoders can crash in host DNS/TLS libraries. Node owns validated
// publisher HTTPS and redirects; the decoder receives a private loopback URL with
// byte ranges intact, so seeking never requires downloading the whole episode.
export async function decoderInput(source,signal,request=publicRequest){
  if(path.isAbsolute(source))return{url:source,close:async()=>{}};
  const publisher=secureUrl(source).href,token='/'+randomUUID(),controller=new AbortController();
  signal.throwIfAborted();
  const server=createServer(async(incoming,response)=>{
    if(incoming.url!==token){response.writeHead(404).end();return;}
    if(!['GET','HEAD'].includes(incoming.method)){response.writeHead(405).end();return;}
    const range=incoming.headers.range;
    if(range&&!/^bytes=\d*-\d*$/.test(range)){response.writeHead(416).end();return;}
    let upstream;
    try{
      upstream=await request(publisher,{range,headers:{'Accept-Encoding':'identity'},signal:controller.signal});
      if(response.destroyed){upstream.destroy();return;}
      const headers={};for(const name of ['content-type','content-length','content-range','accept-ranges'])if(upstream.headers[name])headers[name]=upstream.headers[name];
      response.writeHead(upstream.statusCode||502,headers);
      response.on('close',()=>upstream.destroy());upstream.on('error',()=>response.destroy());
      if(incoming.method==='HEAD'){upstream.destroy();response.end();}else upstream.pipe(response);
    }catch{upstream?.destroy();if(!response.destroyed){if(response.headersSent)response.destroy();else response.writeHead(502).end();}}
  });
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  const abort=()=>{controller.abort();server.closeAllConnections();};
  signal.addEventListener('abort',abort,{once:true});if(signal.aborted)abort();
  let closed=false;
  return{url:`http://127.0.0.1:${server.address().port}${token}`,close:async()=>{if(closed)return;closed=true;signal.removeEventListener('abort',abort);abort();await new Promise(resolve=>server.close(resolve));}};
}
export function parseTranscript(raw,type='text/vtt'){
  if(Buffer.byteLength(raw)>6e6)throw Error('Transcript is too large.');
  const clean=s=>String(s||'').replace(/<[^>]*>/g,'').replace(/&amp;/g,'&').replace(/&quot;/g,'"').trim();
  if(type.includes('json')){
    const root=JSON.parse(raw),items=Array.isArray(root)?root:root.segments||root.results||[];
    const cues=items.map(x=>({startMs:Number(x.startTime??x.start)*1000,endMs:Number(x.endTime??x.end)*1000,text:clean(x.body??x.text)})).filter(x=>Number.isFinite(x.startMs)&&Number.isFinite(x.endMs)&&x.startMs>=0&&x.endMs>x.startMs&&x.text);
    if(!cues.length)throw Error('No subtitle cues.');return{cues,timed:true};
  }
  const stamp=s=>s.replace(',','.').split(':').reduce((a,n)=>a*60+Number(n),0)*1000;
  const normalized=raw.replace(/\r/g,''),blocks=normalized.split(/\n\s*\n/),cues=[];
  for(const block of blocks){const m=block.match(/((?:\d+:)?\d{2}:\d{2}[.,]\d+)\s*-->\s*((?:\d+:)?\d{2}:\d{2}[.,]\d+)[^\n]*\n([\s\S]*)/);if(m){const startMs=stamp(m[1]),endMs=stamp(m[2]),text=clean(m[3]);if(endMs>startMs&&text)cues.push({startMs,endMs,text});}}
  if(cues.length)return{cues:cues.sort((a,b)=>a.startMs-b.startMs),timed:true};
  if(type.split(';')[0]==='text/plain')return{cues:blocks.map(text=>({startMs:0,endMs:0,text:clean(text)})).filter(x=>x.text),timed:false};
  throw Error('Unsupported publisher transcript.');
}
export function wavInfo(bytes){
  if(bytes.toString('ascii',0,4)!=='RIFF'||bytes.toString('ascii',8,12)!=='WAVE')throw Error('Audio decode failed.');
  let at=12,rate=16000,channels=1,bits=16,data=null;
  while(at+8<=bytes.length){const name=bytes.toString('ascii',at,at+4),size=bytes.readUInt32LE(at+4);if(name==='fmt '){channels=bytes.readUInt16LE(at+10);rate=bytes.readUInt32LE(at+12);bits=bytes.readUInt16LE(at+22);}if(name==='data'){data=bytes.subarray(at+8,Math.min(bytes.length,at+8+size));break;}at+=8+size+(size%2);}
  if(!data||rate!==16000||channels!==1||bits!==16)throw Error('Unsupported decoded audio.');
  let energy=0;for(let i=0;i+1<data.length;i+=2){const value=data.readInt16LE(i)/32768;energy+=value*value;}
  return{durationMs:data.length/32,energy:energy/(data.length/2||1)};
}
export class SubtitleEngine{
  constructor(directory,resources,sourceFor=async track=>track.directAudioUrl,request=publicRequest){this.directory=directory;this.resources=resources;this.sourceFor=sourceFor;this.request=request;this.state={trackId:'',cues:[],working:false};this.position=0;this.serial=0;}
  snapshot(){return structuredClone(this.state);}
  start(track,position=0){if(this.state.trackId===track.isrc){this.position=position;return this.snapshot();}this.stop();const serial=this.serial;this.position=position;this.state={trackId:track.isrc,cues:[],working:true,timed:true,generated:false};this.controller=new AbortController();this.task=this.prepare(track,serial,this.controller.signal).catch(error=>{if(serial===this.serial&&!this.controller.signal.aborted)this.state={...this.state,working:false,error:'Could not generate subtitles for this audio. Playback can continue.'};});return this.snapshot();}
  stop(){this.serial++;this.controller?.abort();this.state={trackId:'',cues:[],working:false};}
  async publisher(track){
    let sources=track.transcripts||[];
    if(!sources.length&&track.feedUrl){const rows=await episodes({feedUrl:track.feedUrl,title:track.album||track.artist,artwork:track.albumArtUrl});sources=rows.find(x=>x.isrc===track.isrc||x.directAudioUrl===track.directAudioUrl)?.transcripts||[];}
    for(const source of sources){try{return parseTranscript(await readPublic(secureUrl(source.url).href,6e6),source.type||'text/vtt');}catch{}}
    return null;
  }
  async prepare(track,serial,signal){
    await mkdir(this.directory,{recursive:true});
    const key=createHash('sha256').update(track.isrc).digest('hex'),file=path.join(this.directory,key+'.json');
    let cached={cues:[],windows:[],language:'auto'};
    try{if((await stat(file)).size<=8e6)cached=JSON.parse(await readFile(file,'utf8'));}catch{}
    if(serial!==this.serial)return;
    this.state={...this.state,cues:cached.cues||[],generated:true};
    const published=await Promise.race([this.publisher(track).catch(()=>null),sleep(8000,null,{signal}).catch(()=>null)]);
    if(serial!==this.serial)return;
    if(published){this.state={trackId:track.isrc,...published,generated:false,working:false};return;}
    const source=await this.sourceFor(track),remote=!path.isAbsolute(source);
    if(remote)secureUrl(source);
    const exe=name=>path.join(this.resources,name+(process.platform==='win32'?'.exe':''));
    const model=path.join(this.resources,'ggml-tiny-q5_1.bin'),windows=new Set(cached.windows||[]),cues=cached.cues||[];
    let language=cached.language||'auto',endAt=track.durationMs||Infinity;
    while(serial===this.serial&&!signal.aborted){
      const at=Math.floor(this.position/WINDOW)*WINDOW,next=[at,at+WINDOW,at+WINDOW*2].find(x=>x<endAt&&!windows.has(x));
      if(next===undefined){this.state={...this.state,working:false};await sleep(750,null,{signal});continue;}
      this.state={...this.state,working:true};
      const temporary=path.join(this.directory,'.work-'+randomUUID());await mkdir(temporary);
      try{
        const wav=path.join(temporary,'audio.wav'),out=path.join(temporary,'words');
        const input=await decoderInput(source,signal,this.request);
        try{await run(exe('ffmpeg'),['-hide_banner','-loglevel','error','-nostdin','-protocol_whitelist',remote?'http,tcp':'file,crypto',...(remote?['-rw_timeout','15000000']:[]),'-ss',String(next/1000),'-i',input.url,'-t',String(WINDOW/1000),'-vn','-ac','1','-ar','16000','-c:a','pcm_s16le','-y',wav],{timeout:60000,maxBuffer:2e6,windowsHide:true,signal});}finally{await input.close();}
        if((await stat(wav)).size>1e6)throw Error('Unexpected decoded audio size.');
        const info=wavInfo(await readFile(wav));
        if(info.durationMs<WINDOW-1000)endAt=next+info.durationMs;
        if(info.durationMs>=500&&info.energy>.000004){
          await run(exe('whisper-cli'),['-m',model,'-f',wav,'-l',language,'-t','2','-ml','64','-sow','-oj','-of',out,'-np'],{timeout:120000,maxBuffer:4e6,windowsHide:true,signal});
          const result=JSON.parse(await readFile(out+'.json','utf8'));language=result.result?.language||language;
          const additions=(result.transcription||[]).map(x=>({startMs:next+Math.max(0,x.offsets.from),endMs:next+Math.min(info.durationMs,x.offsets.to),text:String(x.text||'').trim()})).filter(x=>x.text&&x.endMs>x.startMs);
          for(let i=cues.length-1;i>=0;i--)if(cues[i].startMs>=next&&cues[i].startMs<next+WINDOW)cues.splice(i,1);
          cues.push(...additions);cues.sort((a,b)=>a.startMs-b.startMs);
        }
        if(serial!==this.serial)return;
        windows.add(next);
        await writeFile(file+'.part',JSON.stringify({version:1,engine:'Whisper Tiny',cues:cues.slice(0,50000),windows:[...windows],language}),{mode:0o600});await rename(file+'.part',file);
        this.state={trackId:track.isrc,cues:[...cues],timed:true,generated:true,working:false};
      }finally{await rm(temporary,{recursive:true,force:true,maxRetries:5,retryDelay:200});}
    }
  }
}

const profanity=/(?<![\p{L}\p{N}])(?:motherfuck(?:er|ers|ing)?|fuck(?:s|ed|er|ers|in|ing)?|bullshit|horseshit|shit(?:s|ty|ting|head)?|bitch(?:es|y|ing)?|ass(?:es|hole|holes)?|bastard(?:s)?|cunt(?:s)?|dick(?:s|head)?|cock(?:s|sucker|suckers)?|puss(?:y|ies)|whore(?:s)?|slut(?:s)?|nigg(?:a|as|er|ers)|fag(?:s|got|gots)?|damn(?:ed)?|goddamn(?:ed)?|piss(?:ed|ing)?|tits?|twat(?:s)?)(?![\p{L}\p{N}])/giu;
export const cleanVersion=track=>track.isExplicit===false||/\b(clean(?: version| edit)?|censored|non[ -]?explicit|no swearing)\b/i.test(track.title);
export const cleanLyricText=text=>String(text).replace(profanity,'•••');
// Read JSON from LRCLIB. A 404 means "no match" and is not an error.
async function lrclib(read,pathAndQuery){
  try{return JSON.parse(await read('https://lrclib.net/api/'+pathAndQuery,3e6));}
  catch(error){if(/HTTP 404/.test(error.message))return null;throw error;}
}
export function pickLyrics(rows,track){
  const title=fold(plainTitle(track.title)),artists=String(track.artist).split(/,\s*/).map(fold),seconds=track.durationMs/1000;
  const score=row=>{if(!row||row.instrumental||!(row.syncedLyrics||row.plainLyrics))return -1;let points=0;const name=fold(plainTitle(row.trackName||row.name));if(name===title)points+=4;else if(name.includes(title)||title.includes(name))points+=2;else return -1;const artist=fold(row.artistName);if(artists.some(a=>a&&(artist.includes(a)||a.includes(artist))))points+=3;else return -1;const off=Math.abs((Number(row.duration)||0)-seconds);if(seconds&&off<=3)points+=3;else if(seconds&&off<=10)points+=1;else if(seconds&&off>20)return -1;if(row.syncedLyrics)points+=2;return points;};
  return (rows||[]).map(row=>({row,points:score(row)})).filter(x=>x.points>=0).sort((a,b)=>b.points-a.points)[0]?.row||null;
}
// Spotify joins several artists with commas and adds edition words to album
// names, while LRCLIB's exact lookup needs one artist and the canonical album.
// Try the exact lookup with the primary artist first, then a ranked search.
export async function lyrics(track,read=readPublic){
  const title=plainTitle(track.title),primary=String(track.artist).split(/,\s*/)[0],seconds=Math.round(track.durationMs/1000);
  const exact=new URLSearchParams({track_name:title,artist_name:primary,...(seconds?{duration:String(seconds)}:{})});
  let result=await lrclib(read,'get?'+exact);
  if(!result?.syncedLyrics&&!result?.plainLyrics){
    const found=await lrclib(read,'search?'+new URLSearchParams({track_name:title,artist_name:primary}));
    let best=pickLyrics(found,track);
    if(!best){const loose=await lrclib(read,'search?'+new URLSearchParams({q:`${title} ${primary}`}));best=pickLyrics(loose,track);}
    if(best)result=best;
  }
  if(!result?.syncedLyrics&&!result?.plainLyrics)return{trackId:track.isrc,cues:[],timed:false,clean:cleanVersion(track),working:false,error:'No lyrics found for this song.'};
  let cues=[],timed=false;
  if(result.syncedLyrics){timed=true;for(const line of result.syncedLyrics.split('\n')){const m=line.match(/\[(\d+):(\d+(?:\.\d+)?)\](.*)/);if(m)cues.push({startMs:(Number(m[1])*60+Number(m[2]))*1000,text:m[3].trim()});}cues=cues.map((cue,i)=>({...cue,endMs:cues[i+1]?.startMs||track.durationMs}));}
  else cues=String(result.plainLyrics||'').split('\n').filter(Boolean).map(text=>({startMs:0,endMs:0,text}));
  if(cleanVersion(track))cues=cues.map(cue=>({...cue,text:cleanLyricText(cue.text)}));
  return{trackId:track.isrc,cues,timed,clean:cleanVersion(track),working:false};
}
