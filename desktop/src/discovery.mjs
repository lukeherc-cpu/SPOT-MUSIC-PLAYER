export const categories=[['Music','#e0148c','discover new music'],['Podcasts','#006450','popular'],['New releases','#e66a20','new music friday'],['Charts','#8d67ab',''],['Indie','#608108','indie','indie'],['Alternative','#bc5900','alternative','alternative'],['Pop','#8d67ab','pop','pop'],['Hip-Hop','#ba5d07','hip hop','hip-hop'],['Ambient','#477d95','ambient','ambient'],['Folk & Acoustic','#b49a6d','folk acoustic','folk'],['R&B','#dc148c','r&b','r&b'],['Rock','#e91429','rock','rock'],['Country','#d84000','country','country'],['Latin','#e1118c','latin','latin'],['Electronic','#0d73ec','electronic','electronic'],['Jazz','#8c67ab','jazz','jazz'],['Classical','#7d4b32','classical','classical'],['K-Pop','#e8115b','k-pop','k-pop'],['Metal','#777777','metal','metal'],['Chill','#477d95','chill',''],['Workout','#e8115b','workout',''],['Focus','#503750','focus',''],['Sleep','#1e3264','sleep',''],['Party','#af2896','party',''],['Feel good','#e1118c','feel good',''],['Comedy podcasts','#e91429','comedy','','podcast'],['True crime podcasts','#7d4b32','true crime','','podcast'],['News podcasts','#509bf5','news','','podcast'],['Sports podcasts','#148a08','sports','','podcast'],['Business podcasts','#27856a','business','','podcast'],['Tech podcasts','#8d67ab','technology','','podcast']];
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
  // Personal mixes: one per favorite artist, plus mood mixes built around the listener's top artist.
  const artistMixes=seeds.slice(0,3).map((seed,index)=>{
    const lower=seed.toLowerCase(),tracks=shuffle(distinct([...music.filter(x=>x.artist.toLowerCase().includes(lower)),...results[index].tracks.filter(x=>x.artist.toLowerCase().includes(lower))]),entropy+10+index).slice(0,40);
    return {id:`personal-mix-artist-${index}`,title:`${seed} Mix`,subtitle:`${seed} and songs like it`,artwork:tracks.find(x=>x.albumArtUrl)?.albumArtUrl,tracks};
  }).filter(x=>x.tracks.length>=3);
  const moods=[['Chill Mix','chill'],['Upbeat Mix','upbeat'],['Late Night Mix','late night']];
  const moodResults=await Promise.all(moods.map(async([,word],i)=>{try{return (await catalog.search(`${word} ${seeds[i%seeds.length]}`)).tracks||[];}catch{return [];}}));
  const moodMixes=moods.map(([title],i)=>{const tracks=shuffle(distinct(moodResults[i].filter(x=>x.source!=='PODCAST')),entropy+20+i).slice(0,40);return {id:`personal-mix-mood-${i}`,title,subtitle:`${title.replace(' Mix','')} picks with ${seeds[i%seeds.length]}`,artwork:tracks.find(x=>x.albumArtUrl)?.albumArtUrl,tracks};}).filter(x=>x.tracks.length>=3);
  return {mixes,personal:[...artistMixes,...moodMixes],rails:[{title:'Picked for you',cards:picked},...seeds.slice(0,2).map((seed,i)=>({title:`More like ${seed}`,cards:shuffle([...results[i].albums,...results[i].playlists],entropy+i).slice(0,12)}))].filter(x=>x.cards.length),songs:music.slice(0,10)};
}
export async function browse(catalog,name){
  const category=categories.find(x=>x[0]===name);if(!category)throw Error('Unknown browse category.');
  if(name==='Charts'){const chart=await catalog.collection(US.uri);return{title:name,subtitle:'Top charts in the US and around the world',rails:[{title:'Top charts in the US',cards:[{...US,artwork:chart.artwork||US.artwork}]},{title:'Around the world',cards:[GLOBAL]}],tracks:chart.tracks.slice(0,10)};}
  const year=new Date().getFullYear(), genre=category[3];
  const primary=await catalog.search(category[2]), releases=await catalog.search(genre?`genre:${genre} year:${year}`:['Music','New releases'].includes(name)?`year:${year}`:`${category[2]} ${year}`);
  // These editorial playlists are known by id only, so fetch their covers up front instead of on first open.
  const featured=await Promise.all((name==='Music'?[fresh,hits]:name==='New releases'?[fresh]:name==='Pop'?[hits]:name==='Hip-Hop'?[rap]:[]).map(async c=>c.artwork?c:{...c,artwork:await catalog.cover(c.uri).catch(()=>null)}));
  return {title:name,subtitle:genre?`Explore ${name.toLowerCase()} playlists, albums and songs`:['Music','New releases'].includes(name)?'Discover new music':`Playlists and songs for ${name.toLowerCase()}`,rails:[{title:'Start here',cards:featured},{title:genre?`${name} playlists`:'Discover something new',cards:primary.playlists},{title:genre?`New in ${name}`:'New albums and singles',cards:releases.albums},{title:'Albums to explore',cards:primary.albums}].filter(x=>x.cards.length),tracks:genre?(await catalog.search(`genre:${genre}`)).tracks:primary.tracks};
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

// Radio: the seed artist's songs plus top songs from up to four related artists, interleaved.
export async function radio(catalog,seed,exclude=[]){
  const name=String(seed.artist||'').split(',')[0].trim();if(!name)throw Error('Choose a song or artist for radio.');
  const lower=name.toLowerCase(),skip=new Set(exclude);if(seed.isrc)skip.add(seed.isrc);
  const found=await catalog.search(name);
  const artist=/^spotify:artist:/.test(seed.uri||'')?{uri:seed.uri}:(found.artists||[]).find(a=>a.title.toLowerCase()===lower)||(found.artists||[])[0];
  const own=[...(found.tracks||[]).filter(t=>t.artist.toLowerCase().includes(lower))];let related=[];
  if(artist){try{const page=await catalog.collection(artist.uri);own.unshift(...page.tracks);related=(page.artist?.related||[]).slice(0,4);}catch{}}
  const others=await Promise.all(related.map(r=>catalog.collection(r.uri).then(p=>p.tracks.slice(0,8)).catch(()=>[])));
  const lanes=[shuffle(distinct(own),hash(name+Date.now())),...others.map((x,i)=>shuffle(x,hash(name+i+Date.now())))].map(l=>l.filter(t=>t.source!=='PODCAST'&&!skip.has(t.isrc)));
  const out=[];for(let i=0;out.length<50&&lanes.some(l=>i<l.length);i++)for(const lane of lanes)if(lane[i])out.push(lane[i]);
  const tracks=distinct(out).slice(0,50);if(!tracks.length)throw Error(`Couldn't build a radio for ${name} right now.`);
  return {title:seed.title?`${seed.title} Radio`:`${name} Radio`,subtitle:`Based on ${seed.title?`${seed.title} by `:''}${name}${related.length?` with ${related.map(r=>r.title).slice(0,3).join(', ')} and more`:''}`,artwork:seed.albumArtUrl||seed.artwork||tracks[0].albumArtUrl,radio:true,tracks};
}
