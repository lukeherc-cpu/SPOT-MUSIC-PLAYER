// Finds the clean version of an explicit song: Spotify's own clean release first, then a clean
// YouTube upload. Same matching as the Android app's Replace with clean version.
export const cleanLabel=/\bclean\b|\bedited\b|radio edit|censored|clean version/i;
const dirty=/explicit|dirty|uncensored/i;
/** A title without version labels: "Song (feat. X) - Radio Edit" becomes "song". */
export const core=title=>String(title||'').toLowerCase().replace(/\(.*?\)|\[.*?\]/g,' ').replace(/\s+-\s+.*(edit|clean|version|remaster|explicit|mix).*$/,' ').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
export const tokens=value=>new Set(String(value||'').toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(x=>x.length>1||/\d/.test(x)));
const all=(set,items)=>[...items].every(x=>set.has(x));
const firstArtist=song=>String(song.artist||'').split(',')[0].trim();
export const baseTitle=title=>String(title).split(' (')[0].split(' - ')[0]||String(title);
const near=(a,b,limit)=>!(a>0)||!(b>0)||Math.abs(a-b)<=limit;

export function pickSpotifyClean(song,candidates){
  const artistWords=tokens(firstArtist(song)),key=core(song.title);
  return candidates.filter(t=>t.isrc!==song.isrc&&t.isExplicit===false&&core(t.title)===key&&all(tokens(t.artist),artistWords)&&near(t.durationMs,song.durationMs,15000))
    .sort((a,b)=>Math.abs(a.durationMs-song.durationMs)-Math.abs(b.durationMs-song.durationMs))[0]||null;
}
export function pickVideoClean(song,videos){
  const words=tokens(core(song.title)),artistWords=tokens(firstArtist(song));
  return videos.filter(v=>{const text=tokens(`${v.title} ${v.channel}`);return /^[A-Za-z0-9_-]{11}$/.test(v.videoId)&&cleanLabel.test(v.title)&&!dirty.test(v.title)&&all(text,words)&&(!artistWords.size||[...artistWords].some(x=>text.has(x)))&&near(v.durationMs,song.durationMs,25000);})
    .sort((a,b)=>Number(/- topic$/i.test(b.channel))-Number(/- topic$/i.test(a.channel))||(b.views||0)-(a.views||0))[0]||null;
}
export const videoTrack=(song,video)=>({isrc:`youtube:${video.videoId}`,title:`${baseTitle(song.title)} (Clean)`,artist:song.artist,album:song.album,durationMs:video.durationMs>0?video.durationMs:song.durationMs,albumArtUrl:song.albumArtUrl,source:'YOUTUBE',youtubeVideoId:video.videoId,isExplicit:false});
/** Returns {track} for a confident match, otherwise {choices} of YouTube uploads to pick from. */
export async function findClean(catalog,searchVideos,song){
  const artist=firstArtist(song);
  const spotify=q=>catalog.search(q).then(r=>r.tracks||[]).catch(()=>[]);
  const videos=q=>searchVideos(q).catch(()=>[]);
  const [s,v]=await Promise.all([
    Promise.all([`${song.title} ${artist}`,`${song.title} ${artist} clean`,`${core(song.title)} ${artist} edited`].map(spotify)).then(x=>x.flat()),
    Promise.all([`${artist} ${baseTitle(song.title)} clean`,`${artist} ${baseTitle(song.title)} radio edit`].map(videos)).then(x=>x.flat()),
  ]);
  const unique=[...new Map(v.map(x=>[x.videoId,x])).values()];
  const clean=pickSpotifyClean(song,s),video=pickVideoClean(song,unique);
  if(clean)return{track:{...clean,isExplicit:false,...(video?{youtubeVideoId:video.videoId}:{})}};
  if(video)return{track:videoTrack(song,video)};
  const more=[...new Map([...unique,...(await videos(`${baseTitle(song.title)} clean`))].map(x=>[x.videoId,x])).values()];
  const base=tokens(baseTitle(song.title));
  return{choices:more.filter(x=>/^[A-Za-z0-9_-]{11}$/.test(x.videoId)).sort((a,b)=>Number(cleanLabel.test(b.title))-Number(cleanLabel.test(a.title))||Number(all(tokens(b.title),base))-Number(all(tokens(a.title),base))||(b.views||0)-(a.views||0)).slice(0,15)};
}
