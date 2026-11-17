export const categories=[['Music','#e0148c','discover new music'],['Podcasts','#006450','popular'],['New releases','#e66a20','new music friday'],['Charts','#8d67ab',''],['Indie','#608108','indie','indie'],['Alternative','#bc5900','alternative','alternative'],['Pop','#8d67ab','pop','pop'],['Hip-Hop','#ba5d07','hip hop','hip-hop'],['Ambient','#477d95','ambient','ambient'],['Folk & Acoustic','#b49a6d','folk acoustic','folk']];
const card=(id,title,subtitle,artwork=null)=>({uri:`spotify:playlist:${id}`,title,subtitle,artwork,type:'playlist'});
export const US=card('37i9dQZEVXbLRQDuF5jeBp','Top 50 - USA','Most played in the United States','https://charts-images.scdn.co/assets/locale_en/regional/daily/region_us_default.jpg');
export const GLOBAL=card('37i9dQZEVXbMDoHDwVN2tF','Top 50 - Global','Most played worldwide','https://charts-images.scdn.co/assets/locale_en/regional/daily/region_global_default.jpg');
const fresh=card('37i9dQZF1DX4JAvHpjipBk','New Music Friday','New music, updated by Spotify');
const hits=card('37i9dQZF1DXcBWIGoYBM5M',"Today's Top Hits","Today's biggest hits");
const rap=card('37i9dQZF1DX0XUsuxWHRQd','RapCaviar','New and essential hip-hop');
export function seedArtists(history,favorites){
  const counts=new Map();
  function add(track,weight){const name=track.artist?.split(',')[0].trim();if(track.source==='PODCAST'||!name||name.toLowerCase()==='unknown artist')return;const key=name.toLowerCase();counts.set(key,{name,weight:(counts.get(key)?.weight||0)+weight});}
  history.slice(0,30).forEach((entry,index)=>add(entry.track||entry,Math.max(1,40-index)));
  favorites.slice(0,50).forEach(t=>add(t,8));
  const values=[...counts.values()].sort((a,b)=>b.weight-a.weight).slice(0,6).map(x=>x.name);
  return values.length?values:['indie pop','hip hop','chill'];
}
function hash(raw){let n=2166136261;for(const c of raw)n=Math.imul(n^c.charCodeAt(0),16777619);return n>>>0;}
function shuffle(items,seed){const out=[...items];let state=seed||1;for(let i=out.length-1;i>0;i--){state=(Math.imul(state,1664525)+1013904223)>>>0;const j=state%(i+1);[out[i],out[j]]=[out[j],out[i]];}return out;}
const distinct=(items,key='isrc')=>[...new Map(items.map(x=>[x[key],x])).values()];
export async function recommendations(catalog,library,rotation=Math.floor(Date.now()/1800000)){
  const seeds=seedArtists(library.history,library.favorites),music=distinct([...library.history.map(x=>x.track),...library.favorites].filter(x=>x.source!=='PODCAST'));
  const results=await Promise.all(seeds.map(async seed=>{try{return await catalog.search(seed);}catch{return {tracks:[],albums:[],playlists:[]};}}));
  const entropy=hash(String(rotation)+music.map(x=>x.isrc).join(','));
  const mixes=seeds.slice(0,3).map((seed,index)=>{
    const indices=[index,(index+3)%seeds.length].filter((x,i,a)=>a.indexOf(x)===i);
    const names=indices.map(i=>seeds[i]);
    const tracks=shuffle(distinct([...indices.flatMap(i=>results[i].tracks),...music.filter(x=>names.some(n=>x.artist.toLowerCase().includes(n.toLowerCase())))]),entropy+index).slice(0,40);
    return {id:`daily-mix-${index+1}`,title:`Daily Mix ${index+1}`,subtitle:names.join(', ')+' and more',artwork:tracks.find(x=>x.albumArtUrl)?.albumArtUrl,tracks};
  }).filter(x=>x.tracks.length);
  const picked=shuffle(distinct(results.flatMap(x=>[...x.playlists,...x.albums]),'uri'),entropy).slice(0,12);
  return {mixes,rails:[{title:'Picked for you',cards:picked},...seeds.slice(0,2).map((seed,i)=>({title:`More like ${seed}`,cards:shuffle([...results[i].albums,...results[i].playlists],entropy+i).slice(0,12)}))].filter(x=>x.cards.length),songs:music.slice(0,10)};
}
export async function browse(catalog,name){
  const category=categories.find(x=>x[0]===name);if(!category)throw Error('Unknown browse category.');
  if(name==='Charts'){const chart=await catalog.collection(US.uri);return{title:name,subtitle:'Top charts in the US and around the world',rails:[{title:'Top charts in the US',cards:[{...US,artwork:chart.artwork||US.artwork}]},{title:'Around the world',cards:[GLOBAL]}],tracks:chart.tracks.slice(0,10)};}
  const year=new Date().getFullYear(), genre=category[3];
  const primary=await catalog.search(category[2]), releases=await catalog.search(genre?`genre:${genre} year:${year}`:`year:${year}`);
  const featured=name==='Music'?[fresh,hits]:name==='New releases'?[fresh]:name==='Pop'?[hits]:name==='Hip-Hop'?[rap]:[];
  return {title:name,subtitle:genre?`Explore ${name.toLowerCase()} playlists, albums and songs`:'Discover new music',rails:[{title:'Start here',cards:featured},{title:genre?`${name} playlists`:'Discover something new',cards:primary.playlists},{title:genre?`New in ${name}`:'New albums and singles',cards:releases.albums},{title:'Albums to explore',cards:primary.albums}].filter(x=>x.cards.length),tracks:genre?(await catalog.search(`genre:${genre}`)).tracks:primary.tracks};
}
export async function albumForTrack(catalog,song){
  const linked=t=>/^spotify:album:[A-Za-z0-9]{22}$/.test(t.albumUri)?t.albumUri:null;
  if(linked(song))return catalog.collection(song.albumUri);
  const norm=x=>String(x||'').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
  let track=song;
  if(!track.album){track=(await catalog.search(`${song.title} ${song.artist}`)).tracks.find(t=>norm(t.title)===norm(song.title)&&norm(t.artist)===norm(song.artist));if(!track)throw Error("Couldn't find this song's album on Spotify.");}
  if(linked(track))return catalog.collection(track.albumUri);
  const album=(await catalog.search(`${track.album} ${track.artist}`)).albums.find(x=>norm(x.title)===norm(track.album)&&norm(x.subtitle).includes(norm(track.artist.split(',')[0])));
  if(!album)throw Error("Couldn't find this song's album on Spotify.");return catalog.collection(album.uri);
}
