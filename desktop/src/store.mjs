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
const empty=()=>({version:1,favorites:[],playlists:[],shows:[],history:[],positions:{},saves:[],hidden:[],seen:{}});
export class Store {
  constructor(directory){this.directory=directory;this.file=path.join(directory,'library.json');this.state=empty();this.tail=Promise.resolve();}
  async load(){await mkdir(this.directory,{recursive:true});try{const data=JSON.parse(await readFile(this.file,'utf8'));if(data.version!==1)throw Error('Unsupported library version.');this.state={...empty(),...data};}catch(e){if(e.code!=='ENOENT')throw e;}return this.snapshot();}
  snapshot(){return structuredClone(this.state);}
  change(mutator){const result=this.tail.then(async()=>{const next=this.snapshot();await mutator(next);await writeFile(this.file+'.tmp',JSON.stringify(next),{mode:0o600});await rename(this.file+'.tmp',this.file);this.state=next;return this.snapshot();});this.tail=result.catch(()=>{});return result;}
  favorite(raw){const t=cleanTrack(raw);return this.change(s=>{const at=s.favorites.findIndex(x=>x.isrc===t.isrc);if(at>=0)s.favorites.splice(at,1);else s.favorites.unshift(t);});}
  toggle(list,raw){const t=cleanTrack(raw);return this.change(s=>{s[list]=Array.isArray(s[list])?s[list]:[];const at=s[list].findIndex(x=>x.isrc===t.isrc);if(at>=0)s[list].splice(at,1);else s[list].unshift(t);if(s[list].length>2000)s[list].length=2000;});}
  saveLater(raw){return this.toggle('saves',raw);}
  hide(raw){return this.toggle('hidden',raw);}
  importCollection(collection){
    const tracks=[...new Map(collection.tracks.map(cleanTrack).map(t=>[t.isrc,t])).values()];
    if(!tracks.length)throw Error('No available songs to import.');
    return this.change(s=>s.playlists.unshift({id:randomUUID(),title:String(collection.title).slice(0,300),artwork:collection.artwork||null,tracks,createdAt:Date.now()}));
  }
  /** Adds another version of a song (such as its clean version) right after it in Liked Songs, Bookmarked and every playlist. Both stay. */
  addVersion(oldIsrc,raw){
    const next=cleanTrack(raw);if(typeof oldIsrc!=='string'||!oldIsrc)throw Error('Invalid song.');
    return this.change(s=>{
      const add=list=>{if(!Array.isArray(list)||!list.some(t=>t.isrc===oldIsrc))return list;const rest=list.filter(t=>t.isrc!==next.isrc),at=rest.findIndex(t=>t.isrc===oldIsrc);return [...rest.slice(0,at+1),next,...rest.slice(at+1)];};
      s.favorites=add(s.favorites);s.saves=add(s.saves);for(const p of s.playlists)p.tracks=add(p.tracks);
    });
  }
  markSeen(feedUrl){if(typeof feedUrl!=='string'||!feedUrl.startsWith('https://'))throw Error('Invalid podcast.');return this.change(s=>{s.seen={...(s.seen||{}),[feedUrl]:Date.now()};const keys=Object.keys(s.seen);if(keys.length>500)delete s.seen[keys[0]];});}
  record(raw){const track=cleanTrack(raw);return this.change(s=>{const previous=s.history.find(x=>x.track.isrc===track.isrc);s.history=[{track,playedAt:Date.now(),plays:(previous?.plays||0)+1},...s.history.filter(x=>x.track.isrc!==track.isrc)].slice(0,100);});}
  follow(show){if(!show||typeof show.feedUrl!=='string'||!show.feedUrl.startsWith('https://'))throw Error('Invalid podcast.');return this.change(s=>{const at=s.shows.findIndex(x=>x.feedUrl===show.feedUrl);if(at>=0)s.shows.splice(at,1);else s.shows.unshift({id:String(show.id||show.feedUrl),feedUrl:show.feedUrl,title:String(show.title||'Podcast').slice(0,300),author:String(show.author||'').slice(0,200),artwork:show.artwork||null});});}
}
