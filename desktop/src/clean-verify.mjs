// Checks that a "clean version" really is clean by listening to it. The explicit song's synced
// lyrics say where its swear words are; those moments are transcribed with the bundled offline
// Whisper model in both the explicit recording (the control) and the candidate. A candidate is
// verified only when the control audibly swears there, the candidate does not, and the candidate
// still has vocals (so an instrumental or karaoke upload can't pass).
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp,readFile,readdir,rm,stat} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const exec=promisify(execFile);
// Words a clean edit removes. "damn" and "hell" are left out: many official clean edits keep them.
const SWEARS=/(?<![\p{L}\p{N}])(?:motherf\w*|f+u+c+k\w*|fuk\w*|bullshit|horseshit|shit\w*|bitch\w*|ass|asses|asshole\w*|bastard\w*|cunt\w*|dick|dicks|dickhead\w*|cock|cocks|cocksucker\w*|puss(?:y|ies)|whore\w*|slut\w*|nigg\w*|fag|fags|faggot\w*|goddamn\w*|tits?|twat\w*|pussies)(?![\p{L}\p{N}])/giu;
export const swears=text=>[...String(text||'').matchAll(SWEARS)].map(m=>m[0].toLowerCase());

/** Where the explicit song swears, from synced lyric lines: up to [max] padded windows in ms. */
export function swearWindows(cues,durationMs,max=8){
  const lines=(cues||[]).filter(c=>c.endMs>c.startMs&&swears(c.text).length);
  let windows=lines.map(c=>({startMs:Math.max(0,c.startMs-1500),endMs:Math.min(c.startMs+Math.min(c.endMs-c.startMs,9000)+1500,durationMs||Infinity)}));
  windows=windows.sort((a,b)=>a.startMs-b.startMs).reduce((all,w)=>{const last=all.at(-1);if(last&&w.startMs<=last.endMs&&w.endMs-last.startMs<=14000)last.endMs=Math.max(last.endMs,w.endMs);else all.push({...w});return all;},[]);
  if(windows.length>max){const step=windows.length/max;windows=Array.from({length:max},(_,i)=>windows[Math.floor(i*step)]);}
  return windows;
}
/** Without synced lyrics: evenly spread windows over the body of the song. */
export function spreadWindows(durationMs,count=6,length=15000){
  const d=durationMs>0?durationMs:180000;
  return Array.from({length:count},(_,i)=>{const startMs=Math.round(d*(.08+.84*i/count));return{startMs,endMs:Math.min(d,startMs+length)};});
}
/** The verdict from per-window transcripts of the control (explicit) and the candidate. */
export function judge(control,candidate){
  const heard=candidate.flatMap(swears),informative=control.filter(t=>swears(t).length).length;
  const words=list=>list.join(' ').split(/\s+/).filter(w=>/[\p{L}]/u.test(w)).length;
  const vocals=words(candidate)>=Math.max(3,words(control)*.3);
  if(heard.length)return{verdict:'explicit',heard:[...new Set(heard)]};
  if(!vocals)return{verdict:'no-vocals'};
  if(!informative)return{verdict:'unverified'};
  return{verdict:'clean',checked:informative};
}

export class CleanVerifier{
  /** ytdlp: yt-dlp binary; runtime: JS runtime for yt-dlp; speech: folder with ffmpeg, whisper-cli and the model. */
  constructor({ytdlp,runtime,speech,lyricsFor,run=exec}){this.ytdlp=ytdlp;this.runtime=runtime;this.speech=speech;this.lyricsFor=lyricsFor;this.run=run;this.controls=new Map();}
  exe(name){return path.join(this.speech,name+(process.platform==='win32'?'.exe':''));}
  async download(target,dir){
    const out=path.join(dir,'a.%(ext)s');
    await this.run(this.ytdlp,['--no-playlist','--no-warnings','--socket-timeout','15','--js-runtimes',`node:${this.runtime}`,'--extractor-args',`youtube:player_client=${process.env.CADENCE_YT_CLIENTS||'visionos'}`,
      '-f','bestaudio[ext=m4a]/bestaudio/best','--max-filesize','40M','-o',out,'--',target],{timeout:120000,maxBuffer:8e6,windowsHide:true,env:{...process.env,ELECTRON_RUN_AS_NODE:'1'}});
    const file=(await readdir(dir)).find(x=>x.startsWith('a.')&&!x.endsWith('.part'));
    if(!file)throw Error('Audio download failed.');
    return path.join(dir,file);
  }
  async transcribe(file,windows,dir){
    const texts=[];
    for(const [i,w] of windows.entries()){
      const wav=path.join(dir,`w${i}.wav`),out=path.join(dir,`w${i}`);
      try{
        await this.run(this.exe('ffmpeg'),['-hide_banner','-loglevel','error','-nostdin','-ss',String(w.startMs/1000),'-i',file,'-t',String((w.endMs-w.startMs)/1000),'-vn','-ac','1','-ar','16000','-c:a','pcm_s16le','-y',wav],{timeout:60000,maxBuffer:2e6,windowsHide:true});
        if((await stat(wav)).size<16000){texts.push('');continue;}
        await this.run(this.exe('whisper-cli'),['-m',path.join(this.speech,'ggml-tiny-q5_1.bin'),'-f',wav,'-l','en','-t','4','-oj','-of',out,'-np'],{timeout:120000,maxBuffer:4e6,windowsHide:true});
        const result=JSON.parse(await readFile(out+'.json','utf8'));
        texts.push((result.transcription||[]).map(x=>String(x.text||'').trim()).join(' '));
      }catch{texts.push('');}
    }
    return texts;
  }
  /** Control windows and transcripts for the explicit song, computed once per song. */
  async control(song){
    if(this.controls.has(song.isrc))return this.controls.get(song.isrc);
    const task=(async()=>{
      let windows=[];
      try{const l=await this.lyricsFor(song);if(l?.timed)windows=swearWindows(l.cues,song.durationMs);}catch{}
      const fromLyrics=windows.length>0;if(!fromLyrics)windows=spreadWindows(song.durationMs);
      const dir=await mkdtemp(path.join(os.tmpdir(),'cadence-clean-'));
      try{
        const target=song.youtubeVideoId?`https://www.youtube.com/watch?v=${song.youtubeVideoId}`:`ytsearch1:${song.artist} ${song.title} official audio`;
        const texts=await this.transcribe(await this.download(target,dir),windows,dir);
        return{windows,texts,fromLyrics};
      }finally{await rm(dir,{recursive:true,force:true});}
    })();
    this.controls.set(song.isrc,task);task.catch(()=>this.controls.delete(song.isrc));
    return task;
  }
  /** Listens to a candidate upload. Returns {verdict:'clean'|'explicit'|'unverified'|'no-vocals'|'error', heard?}. */
  async check(song,videoId,durationMs=0){
    if(!/^[A-Za-z0-9_-]{11}$/.test(videoId||''))return{verdict:'error'};
    try{
      const control=await this.control(song);
      const scale=durationMs>0&&song.durationMs>0&&Math.abs(durationMs-song.durationMs)<30000?durationMs/song.durationMs:1;
      const windows=control.windows.map(w=>({startMs:Math.round(w.startMs*scale),endMs:Math.round(w.endMs*scale)}));
      const dir=await mkdtemp(path.join(os.tmpdir(),'cadence-clean-'));
      try{
        const texts=await this.transcribe(await this.download(`https://www.youtube.com/watch?v=${videoId}`,dir),windows,dir);
        return{...judge(control.texts,texts),fromLyrics:control.fromLyrics,control:control.texts,candidate:texts};
      }finally{await rm(dir,{recursive:true,force:true});}
    }catch(error){return{verdict:'error',message:String(error.stderr||error.message||error).replace(/https?:\/\/\S+/g,'[url]').slice(-400)};}
  }
}
