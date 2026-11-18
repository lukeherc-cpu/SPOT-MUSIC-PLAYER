import { mkdir,readFile,writeFile,rename } from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export function cleanTrack(t) {
  if(!t||typeof t.isrc!=='string'||!t.isrc||t.isrc.length>160||typeof t.title!=='string'||typeof t.artist!=='string')throw Error('Invalid song.');
  const https=x=>typeof x==='string'&&x.startsWith('https://')?x.slice(0,4000):null;
  return {isrc:t.isrc,title:t.title.slice(0,300),artist:t.artist.slice(0,250),album:typeof t.album==='string'?t.album.slice(0,300):null,durationMs:Math.max(0,Math.min(86400000,Number(t.durationMs)||0)),albumArtUrl:https(t.albumArtUrl),source:t.source==='PODCAST'?'PODCAST':t.source==='YOUTUBE'?'YOUTUBE':'SPOTIFY',youtubeVideoId:/^[A-Za-z0-9_-]{11}$/.test(t.youtubeVideoId)?t.youtubeVideoId:null,albumUri:/^spotify:album:[A-Za-z0-9]{22}$/.test(t.albumUri)?t.albumUri:null,isExplicit:typeof t.isExplicit==='boolean'?t.isExplicit:null,
    transcripts:Array.isArray(t.transcripts)?t.transcripts.slice(0,10).filter(x=>https(x.url)&&typeof x.type==='string').map(x=>({url:https(x.url),type:x.type.slice(0,100),language:String(x.language||'').slice(0,30)})):[],
    ...(t.source==='PODCAST'?{directAudioUrl:https(t.directAudioUrl),feedUrl:https(t.feedUrl),description:String(t.description||'').slice(0,4000),published:String(t.published||'').slice(0,100)}:{})};
}
const empty=()=>({version:1,favorites:[],playlists:[],shows:[],history:[],positions:{}});
export class Store {
  constructor(directory){this.directory=directory;this.file=path.join(directory,'library.json');this.state=empty();this.tail=Promise.resolve();}
  async load(){await mkdir(this.directory,{recursive:true});try{const data=JSON.parse(await readFile(this.file,'utf8'));if(data.version!==1)throw Error('Unsupported library version.');this.state={...empty(),...data};}catch(e){if(e.code!=='ENOENT')throw e;}return this.snapshot();}
  snapshot(){return structuredClone(this.state);}
  change(mutator){const result=this.tail.then(async()=>{const next=this.snapshot();await mutator(next);await writeFile(this.file+'.tmp',JSON.stringify(next),{mode:0o600});await rename(this.file+'.tmp',this.file);this.state=next;return this.snapshot();});this.tail=result.catch(()=>{});return result;}
  favorite(raw){const t=cleanTrack(raw);return this.change(s=>{const at=s.favorites.findIndex(x=>x.isrc===t.isrc);if(at>=0)s.favorites.splice(at,1);else s.favorites.unshift(t);});}
  importCollection(collection){
    const tracks=[...new Map(collection.tracks.map(cleanTrack).map(t=>[t.isrc,t])).values()];
    if(!tracks.length)throw Error('No available songs to import.');
    return this.change(s=>s.playlists.unshift({id:randomUUID(),title:String(collection.title).slice(0,300),artwork:collection.artwork||null,tracks,createdAt:Date.now()}));
  }
  record(raw){const track=cleanTrack(raw);return this.change(s=>{const previous=s.history.find(x=>x.track.isrc===track.isrc);s.history=[{track,playedAt:Date.now(),plays:(previous?.plays||0)+1},...s.history.filter(x=>x.track.isrc!==track.isrc)].slice(0,100);});}
  follow(show){if(!show||typeof show.feedUrl!=='string'||!show.feedUrl.startsWith('https://'))throw Error('Invalid podcast.');return this.change(s=>{const at=s.shows.findIndex(x=>x.feedUrl===show.feedUrl);if(at>=0)s.shows.splice(at,1);else s.shows.unshift({id:String(show.id||show.feedUrl),feedUrl:show.feedUrl,title:String(show.title||'Podcast').slice(0,300),author:String(show.author||'').slice(0,200),artwork:show.artwork||null});});}
}
