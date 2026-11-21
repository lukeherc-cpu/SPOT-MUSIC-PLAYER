import Hls, {FetchLoader} from './hls.mjs';
import {skeleton,wordStage,expandedWords,wordsBody,lyricsPopup,textSize} from './words.js';
import {prefs,savePrefs,applyTheme} from './prefs.js';
import {artColor} from './color.js';
const api=window.spot;
const state={route:{type:'home'},history:[],library:{favorites:[],playlists:[],shows:[],history:[],positions:{},saves:[],hidden:[]},home:{mixes:[],rails:[],songs:[]},queue:[],index:0,now:null,playing:false,opening:false,full:false,menu:null,modal:null,query:'',filter:'All',homeFilter:'All',libraryFilter:'All',librarySort:'recent',libraryQuery:'',shuffle:false,repeat:false,words:false,expandedWords:false,lyricsPopup:false,subtitle:null,request:0,playRequest:0,selected:new Set(),pageQuery:'',artistTab:'overview',releases:new Map(),openReleases:new Set(),discover:{category:'For you',cache:new Map(),loading:false},preview:null,sleep:null,accent:null,sideFilter:'',expandedRows:new Set(),ctx:null,anchor:null,account:{signedIn:false,checked:false,loading:false,name:null,playlists:[],mixes:[],picked:new Set(),error:null,progress:null},newEpisodes:[],newEpisodesAt:0,cleaning:null};
const content=document.querySelector('#content'),audio=document.querySelector('#audio'),refs=new Map(),queues=new Map();let reference=0,searchTimer,toastTimer,seeking=false,recorded=false,retried=0,lastPositionSave=0,recovery=null,hls=null,previewTimer=null;
const paths={home:'M3 10l9-7 9 7v11h-6v-7H9v7H3z',search:'M21 21l-5-5M18 10a8 8 0 11-16 0 8 8 0 0116 0',library:'M4 3v18M9 3v18M14 4l5-1 3 17-5 1z',plus:'M12 4v16M4 12h16',back:'M15 5l-7 7 7 7',play:'M8 4l13 8-13 8z',pause:'M8 4v16M16 4v16',previous:'M5 5v14M19 4L7 12l12 8z',next:'M19 5v14M5 4l12 8-12 8z',more:'M12 5h.01M12 12h.01M12 19h.01',check:'M5 12l4 4L20 5',download:'M12 3v12M7 10l5 5 5-5M4 18v3h16v-3',album:'M22 12a10 10 0 11-20 0 10 10 0 0120 0M15 12a3 3 0 11-6 0 3 3 0 016 0',artist:'M16 7a4 4 0 11-8 0 4 4 0 018 0M4 21v-3a8 8 0 0116 0v3',queue:'M3 5h17M3 10h17M3 15h10M17 15l5 4-5 4z',volume:'M3 9h4l5-5v16l-5-5H3zM16 8a6 6 0 010 8M19 4a11 11 0 010 16',refresh:'M20 7v5h-5M4 17v-5h5M5 7a8 8 0 0113-2l2 2M4 17l2 2a8 8 0 0013-2',close:'M5 5l14 14M19 5L5 19',shuffle:'M3 6h3l12 12h3M17 14l4 4-4 4M3 18h3l12-12h3M17 2l4 4-4 4',repeat:'M4 8h14l-3-3M20 16H6l3 3M18 8l3-3M6 16l-3 3',heart:'M12 20s-8-4.6-8-10.2A4.6 4.6 0 0112 7a4.6 4.6 0 018 2.8C20 15.4 12 20 12 20z',addlist:'M3 6h12M3 11h12M3 16h7M18 13v8M14 17h8',bookmark:'M6 3h12v18l-6-4-6 4z',compass:'M22 12a10 10 0 11-20 0 10 10 0 0120 0M16 8l-2.5 5.5L8 16l2.5-5.5z',user:'M16 7a4 4 0 11-8 0 4 4 0 018 0M4 21v-2a6 6 0 016-6h4a6 6 0 016 6v2',lyrics:'M4 5h16M4 10h10M4 15h16M4 20h8',timer:'M12 8v5l3 2M9 2h6M20 13a8 8 0 11-16 0 8 8 0 0116 0',hide:'M3 3l18 18M10.6 6.1A9.8 9.8 0 0112 6c6 0 10 6 10 6a17 17 0 01-3.3 3.8M6.6 6.6C3.9 8.4 2 12 2 12s4 6 10 6a9.6 9.6 0 004.3-1',info:'M22 12a10 10 0 11-20 0 10 10 0 0120 0M12 11v6M12 7h.01',pin:'M9 3h6l-1 6 4 4H6l4-4zM12 13v8',up:'M6 15l6-6 6 6',down:'M6 9l6 6 6-6',grid:'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',list:'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',expand:'M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5',copy:'M9 9h11v11H9zM5 15H4V4h11v1',sun:'M12 17a5 5 0 100-10 5 5 0 000 10M12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4',mic:'M12 2a3 3 0 00-3 3v7a3 3 0 006 0V5a3 3 0 00-3-3M19 11a7 7 0 01-14 0M12 18v4',note:'M9 18V5l12-2v13M9 18a3 3 0 11-6 0 3 3 0 016 0M21 16a3 3 0 11-6 0 3 3 0 016 0',stack:'M12 3l9 5-9 5-9-5zM3 13l9 5 9-5',settings:'M12 15a3 3 0 100-6 3 3 0 000 6M19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 01-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 010-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.8.3H9a1.7 1.7 0 001-1.5V3a2 2 0 014 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8V9a1.7 1.7 0 001.5 1H21a2 2 0 010 4h-.1a1.7 1.7 0 00-1.5 1z'};
const h=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=(name,fill=false)=>`<svg viewBox="0 0 24 24" fill="${fill?'currentColor':'none'}" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name]||paths.album}"/></svg>`;
const button=(action,label,name,extra='',data='')=>`<button class="icon ${extra}" data-action="${action}" aria-label="${h(label)}" title="${h(label)}" ${data}>${icon(name)}</button>`;
// Item kinds get their own shape and badge so songs, albums, playlists, podcasts and artists read differently even without artwork.
const KIND={song:['Song','note'],album:['Album','album'],playlist:['Playlist','stack'],podcast:['Podcast','mic'],episode:['Episode','mic'],artist:['Artist','artist'],mix:['Mix','shuffle'],smart:['Smart playlist','stack']};
function kindOf(item){if(!item)return'playlist';if(item.kind)return item.kind;if(item.isrc)return item.source==='PODCAST'?'episode':'song';if(item.feedUrl||item.type==='show')return'podcast';const uri=String(item.uri||'');if(item.type==='artist'||uri.startsWith('spotify:artist:'))return'artist';if(item.type==='album'||uri.startsWith('spotify:album:'))return'album';if(String(item.id||'').startsWith('daily-mix'))return'mix';return'playlist';}
const cover=(url,extra='',label='',kind='',badge=false)=>`<div aria-hidden="true" class="cover ${kind?`k-${kind}`:''} ${kind==='artist'?'round':''} ${extra} ${!url&&!extra.includes('liked')&&!extra.includes('saves')?'empty':''}">${extra.includes('liked')?'♥':extra.includes('saves')?icon('bookmark'):url&&/^https:\/\//.test(url)?`<img src="spot-art://image/?url=${h(encodeURIComponent(url))}" alt="" loading="lazy">`:''}${label?`<span class="mix-label">${h(label)}</span>`:''}${badge&&KIND[kind]?`<i class="kind-badge">${icon(KIND[kind][1])}</i>`:''}</div>`;
const time=seconds=>{const n=Math.max(0,Number.isFinite(seconds)?Math.floor(seconds):0);return `${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;};
const ref=value=>{const id=String(++reference);refs.set(id,value);return id;};
const saved=track=>state.library.favorites.some(x=>x.isrc===track?.isrc);
const later=track=>(state.library.saves||[]).some(x=>x.isrc===track?.isrc);
const hidden=track=>(state.library.hidden||[]).some(x=>x.isrc===track?.isrc);
const primaryArtist=track=>String(track?.artist||'').split(',')[0].trim();
async function call(name,...args){const response=await api[name](...args);if(!response.ok)throw Error(response.error);return response.value;}
function toast(message){const el=document.querySelector('#toast');el.textContent=message;el.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('visible'),5000);}
function error(error){toast(error.message||String(error));}

/* ---------- Shared pieces ---------- */
const browseGroups=[['Charts and new music',['Charts','New releases','Music']],['Genres',['Pop','Hip-Hop','R&B','Rock','Indie','Alternative','Country','Latin','Electronic','Jazz','Classical','K-Pop','Metal','Folk & Acoustic','Ambient']],['Moods and moments',['Chill','Workout','Focus','Sleep','Party','Feel good']],['Podcasts',['Podcasts','Comedy podcasts','True crime podcasts','News podcasts','Sports podcasts','Business podcasts','Tech podcasts']]];
function categories(){const arts=(state.home.rails||[]).flatMap(x=>x.cards).map(x=>x.artwork).filter(Boolean);let i=0;return browseGroups.map(([group,names])=>`<section class="browse-group"><h2>${h(group)}</h2><div class="browse-grid">${names.map(name=>`<button class="category" aria-label="${h(name)}" data-action="browse" data-category="${h(name)}"><strong>${h(name)}</strong>${cover(arts[i++%Math.max(1,arts.length)])}</button>`).join('')}</div></section>`).join('');}
function cardAction(item,mix){return mix?'mix':item.feedUrl?'show':item.smart?'smart':item.tracks&&item.id?'local':'card';}
const newEpisodeFor=feedUrl=>state.newEpisodes.find(x=>x.show.feedUrl===feedUrl);
const newDot=item=>item?.feedUrl&&newEpisodeFor(item.feedUrl)?'<i class="new-dot" title="New episode" aria-label="New episode"></i>':'';
function cardList(items,mix=false){return `<div class="rail">${items.map(item=>{const kind=mix?'mix':kindOf(item);return `<button class="card" aria-label="${h(item.title)}${newEpisodeFor(item.feedUrl)?', new episode':''}" data-action="${cardAction(item,mix)}" data-ref="${ref(item)}">${newDot(item)}${cover(item.artwork||item.tracks?.[0]?.albumArtUrl,'',mix?item.title:'',kind,!mix)}<div class="title">${h(mix?'Made for you':item.title)}</div><p><span class="kind-label">${h(KIND[kind]?.[0]||'')}</span>${item.subtitle||item.author?` · ${h(item.subtitle||item.author)}`:''}</p></button>`;}).join('')}</div>`;}
function cards(title,items,mix=false){if(!items?.length)return'';return `<section>${title?`<h2>${h(title)}</h2>`:''}${cardList(items,mix)}</section>`;}
function songCards(tracks,from){if(!tracks?.length)return'';const queue=String(++reference);queues.set(queue,{tracks,from});return `<div class="rail">${tracks.slice(0,30).map((t,i)=>`<button class="card" aria-label="Play ${h(t.title)}" data-action="play" data-queue="${queue}" data-index="${i}">${cover(t.albumArtUrl,'','','song',true)}<div class="title">${h(t.title)}</div><p><span class="kind-label">Song</span> · ${h(t.artist)}</p></button>`).join('')}</div>`;}
const explicit=t=>t?.isExplicit===true?' <abbr class="explicit" title="Explicit">E</abbr>':'';
// Like and bookmark buttons carry the song id so the toggle animation can find every copy on screen.
function heart(t,action='favorite',extra=''){const on=saved(t);return `<button class="icon like ${on?'saved':''} ${extra}" data-action="${action}" ${action==='favorite'?`data-ref="${ref(t)}"`:''} data-like="${h(t.isrc)}" aria-pressed="${on}" aria-label="${on?'Remove from':'Add to'} Liked Songs" title="${on?'Remove from':'Add to'} Liked Songs">${icon('heart',on)}</button>`;}
function mark(t){const on=later(t);return `<button class="icon mark ${on?'saved':''}" data-action="save-later" data-ref="${ref(t)}" data-mark="${h(t.isrc)}" aria-pressed="${on}" aria-label="${on?'Remove from Bookmarked':'Bookmark for later'}" title="${on?'Bookmarked for later':'Bookmark for later'}">${icon('bookmark',on)}</button>`;}
// Ctrl or Cmd click picks songs, Shift click picks a range, and right-click opens the options.
function rows(tracks,{compact=false,from=''}={}){
  if(!tracks?.length)return'';const queue=String(++reference);queues.set(queue,{tracks,from});const q=state.pageQuery.trim().toLowerCase();
  return tracks.map((track,index)=>{if(q&&!`${track.title} ${track.artist} ${track.album||''}`.toLowerCase().includes(q))return'';const playing=state.now?.isrc===track.isrc,checked=state.selected.has(track.isrc),id=ref(track);return `<div class="song ${compact?'compact':''} ${playing?'playing':''} ${checked?'checked':''} ${hidden(track)?'is-hidden':''}" data-track="${id}" data-prefetch="${id}" aria-selected="${checked}"><button class="number" data-action="play" data-queue="${queue}" data-index="${index}" aria-label="Play ${h(track.title)}">${playing&&state.playing?'♫':index+1}</button><button data-action="play" data-queue="${queue}" data-index="${index}" tabindex="-1" aria-hidden="true">${cover(track.albumArtUrl)}</button><button data-action="play" data-queue="${queue}" data-index="${index}" tabindex="-1"><span class="song-title">${h(track.title)}${hidden(track)?' <em class="tag">Hidden</em>':''}</span><small>${explicit(track).trim()}${track.isExplicit===true?' ':''}${h(track.artist)}</small></button>${compact?'':`<button class="album" data-action="album" data-ref="${id}">${h(track.album||'')}</button>`}${heart(track)}${compact?'':`<button class="icon" data-action="add-to-playlist" data-ref="${id}" aria-label="Add ${h(track.title)} to a playlist" title="Add to playlist">${icon('addlist')}</button><span class="duration">${time(track.durationMs/1000)}</span><button class="icon" data-action="menu" data-ref="${id}" aria-label="Options for ${h(track.title)}">${icon('more')}</button>`}</div>`;}).join('');
}
function findInPage(label){return `<label class="find">${icon('search')}<input id="page-find" value="${h(state.pageQuery)}" placeholder="Find in ${h(label)}" aria-label="Find in ${h(label)}"></label>`;}
function selectedTracks(){return [...new Map([...queues.values()].flatMap(q=>q.tracks).filter(t=>state.selected.has(t.isrc)).map(t=>[t.isrc,t])).values()];}
function bulkBar(){const n=state.selected.size;if(!n)return'';return `<div class="bulk" role="status"><strong>${n} selected</strong><span class="muted">Right-click a selected song for options.</span><button class="pill ghost" data-action="bulk-clear">Clear selection</button></div>`;}
function segmented(name,options,current,action){return `<div class="segmented" role="tablist" aria-label="${h(name)}">${options.map(x=>`<button role="tab" aria-selected="${current===x}" class="${current===x?'on':''}" data-action="${action}" data-value="${h(x)}">${h(x)}</button>`).join('')}</div>`;}

/* ---------- Library model ---------- */
function smartTracks(s){const needle=String(s.artist||'').toLowerCase();return state.library.favorites.filter(t=>t.artist.toLowerCase().includes(needle));}
function libraryArtists(){const map=new Map();for(const t of [...state.library.favorites,...state.library.history.map(x=>x.track)]){if(t.source==='PODCAST')continue;const name=primaryArtist(t);if(!name)continue;const e=map.get(name.toLowerCase())||{kind:'artist',title:name,tracks:[],artwork:t.albumArtUrl,plays:0};if(!e.tracks.some(x=>x.isrc===t.isrc))e.tracks.push(t);map.set(name.toLowerCase(),e);}for(const x of state.library.history){const e=map.get(primaryArtist(x.track).toLowerCase());if(e)e.plays+=x.plays||1;}return [...map.values()].sort((a,b)=>b.plays-a.plays||b.tracks.length-a.tracks.length);}
function lastPlayed(match){let at=0;for(const x of state.library.history)if(match(x.track))at=Math.max(at,x.playedAt||0);return at;}
// Every library entry gets a stable key so it can be pinned, dragged into a custom order or removed.
function libraryItems(){
  const me=prefs.displayName||'You',fav=new Set(state.library.favorites.map(t=>t.isrc)),marks=state.library.saves||[],marked=new Set(marks.map(t=>t.isrc));
  const liked={key:'liked',kind:'playlist',title:'Liked Songs',action:'liked',liked:true,count:state.library.favorites.length,owner:me,recent:lastPlayed(t=>fav.has(t.isrc))||1};
  const bookmarked={key:'saves',kind:'playlist',title:'Bookmarked for later',action:'saves',saves:true,count:marks.length,owner:me,recent:lastPlayed(t=>marked.has(t.isrc))};
  const smart=(prefs.smart||[]).map(s=>{const tracks=smartTracks(s);return{...s,key:`smart:${s.id}`,kind:'smart',smart:true,action:'smart',count:tracks.length,artwork:tracks[0]?.albumArtUrl,owner:me,recent:Number(s.id)||0};});
  const lists=state.library.playlists.map(p=>{const ids=new Set(p.tracks.map(t=>t.isrc));return{...p,key:`playlist:${p.id}`,kind:'playlist',action:'local',count:p.tracks.length,artwork:p.artwork||p.tracks[0]?.albumArtUrl,owner:me,recent:Math.max(p.createdAt||0,lastPlayed(t=>ids.has(t.isrc)))};});
  const shows=state.library.shows.map((s,i)=>({...s,key:`show:${s.feedUrl}`,kind:'podcast',action:'show',recent:lastPlayed(t=>t.feedUrl===s.feedUrl)||-i}));
  const removed=prefs.sideRemoved||[];
  const artists=libraryArtists().filter(a=>!removed.includes(a.title.toLowerCase())).map(a=>({...a,key:`artist:${a.title.toLowerCase()}`,action:'library-artist',count:a.tracks.length,recent:lastPlayed(t=>primaryArtist(t).toLowerCase()===a.title.toLowerCase())}));
  return{playlists:[liked,bookmarked,...smart,...lists],podcasts:shows,artists};
}
// Pinned first (in pin order), then anything not yet placed by hand (newest first), then the dragged order.
function arrange(items){
  const pinned=prefs.sidePinned||[],order=prefs.sideOrder||[];
  const pins=pinned.map(k=>items.find(x=>x.key===k)).filter(Boolean),rest=items.filter(x=>!pinned.includes(x.key));
  const loose=rest.filter(x=>!order.includes(x.key)).sort((a,b)=>(b.recent||0)-(a.recent||0)),placed=rest.filter(x=>order.includes(x.key)).sort((a,b)=>order.indexOf(a.key)-order.indexOf(b.key));
  return[...pins,...loose,...placed];
}
// With no filter the sidebar mixes playlists and podcasts with the four most recent artists.
function sidebarItems(filter=state.sideFilter){
  const all=libraryItems();
  if(filter)return arrange(all[filter]||[]);
  const artists=[...all.artists].sort((a,b)=>(b.recent||0)-(a.recent||0)).slice(0,4);
  return arrange([...all.playlists,...all.podcasts,...artists]);
}
function itemSubtitle(x){if(x.kind==='song')return `Song · ${x.artist}`;if(x.kind==='podcast')return `Podcast · ${x.author||'Podcast'}`;if(x.kind==='artist')return 'Artist';if(x.kind==='smart')return `Smart playlist · ${x.count} ${x.count===1?'song':'songs'}`;if(x.saves)return `${x.count} bookmarked`;if(x.liked)return `Playlist · ${x.count} ${x.count===1?'song':'songs'}`;return `Playlist · ${x.owner||'You'}`;}
function libraryItem(x,cls='side-list'){const pinned=(prefs.sidePinned||[]).includes(x.key);return `<button class="${cls}" data-action="${x.action}" data-ref="${ref(x)}" ${x.key?`data-key="${h(x.key)}" draggable="true"`:''}>${cover(x.artwork,x.liked?'liked':x.saves?'saves':'','',x.kind,cls!=='side-list')}<div><span>${h(x.title)}</span><small>${pinned?`<i class="pin" aria-label="Pinned">${icon('pin')}</i>`:''}${h(itemSubtitle(x))}</small></div></button>`;}

/* ---------- Frame: sidebar, tabs ---------- */
const SIDE_FILTERS=[['playlists','Playlists'],['podcasts','Podcasts'],['artists','Artists']];
function renderSidebar(){
  const filter=state.sideFilter,items=sidebarItems();
  document.querySelector('#sidebar').innerHTML=`<div class="brand"><span class="mark"><i></i><i></i><i></i></span>Cadence</div><div class="side-head"><strong>Your Library</strong><button class="pill create-pill" data-action="create" aria-label="Create playlist">${icon('plus')}Create</button>${button('library-page','Open Your Library as a full page','expand')}</div><div class="chips" role="group" aria-label="Filter Your Library">${filter?`<button class="chip clear" data-action="side-filter" data-value="" aria-label="Show everything">${icon('close')}</button>`:''}${SIDE_FILTERS.filter(([k])=>!filter||k===filter).map(([k,t])=>`<button class="chip ${filter===k?'on':''}" data-action="side-filter" data-value="${k}" aria-pressed="${filter===k}">${t}</button>`).join('')}</div><div class="side-items" data-drag-list="side" aria-label="Your Library items">${items.length?items.map(x=>libraryItem(x)).join(''):`<p class="side-empty">${filter==='podcasts'?'Follow podcasts to see them here.':filter==='artists'?'Artists you play show up here.':'Nothing here yet.'}</p>`}</div><p class="side-hint muted">Drag to reorder. Right-click to pin or remove.</p><button class="nav" data-action="nav" data-page="import">${icon('download')}Import from Spotify</button>`;
}
const TABS=[['home','Home','home'],['search','Search','search'],['library','Your Library','library']];
function activeTab(){const t=state.route.type;return ['home','search','library','profile'].includes(t)?t:state.route.tab||'';}
function renderToolbar(){
  const tab=activeTab(),searching=state.route.type==='search';
  document.querySelector('#toolbar').innerHTML=`<button class="icon circle" data-action="back" aria-label="Back" ${state.history.length?'':'disabled'}>${icon('back')}</button><nav class="tabs" aria-label="Main">${TABS.map(([page,title,name])=>page==='search'&&searching?`<label class="tab search-tab open">${icon('search')}<input id="search-input" value="${h(state.query)}" placeholder="Songs, albums, artists, podcasts" aria-label="Search music and podcasts"><button class="icon clear small" data-action="clear" aria-label="Clear search">${icon('close')}</button></label>`:`<button class="tab ${tab===page?'active':''} ${page==='search'?'search-tab':''}" data-action="nav" data-page="${page}" aria-label="${title}" ${tab===page?'aria-current="page"':''}>${icon(name)}<span>${title}</span></button>`).join('')}</nav><button class="profile ${tab==='profile'?'active':''}" data-action="nav" data-page="profile" aria-label="Profile and settings" title="Profile and settings">${avatar()}</button>`;
}

/* ---------- Home ---------- */
function homeSections(){
  const data=state.route.data||state.home,list=[];
  const shortcuts=[{title:'Liked Songs',action:'liked',kind:'playlist'},...state.library.playlists.slice(0,4).map(x=>({...x,action:'local',kind:'playlist'})),...state.library.shows.slice(0,2).map(x=>({...x,action:'show',kind:'podcast'}))].slice(0,6);
  list.push({id:'shortcuts',title:'Jump back in',type:'mixed',fixed:true,html:`<div class="shortcuts">${shortcuts.map(x=>`<button class="shortcut" data-action="${x.action}" data-ref="${ref(x)}">${cover(x.artwork||x.tracks?.[0]?.albumArtUrl,x.action==='liked'?'liked':'','',x.kind)}<span>${h(x.title)}</span></button>`).join('')}</div>`});
  list.push({id:'mixes',title:'Daily Mixes',type:'music',html:data.mixes?.length?cardList(data.mixes,true):`<p>${state.route.loading?skeleton('rail'):'Listen to music to shape your mixes.'}</p>`});
  if(data.personal?.length)list.push({id:'personal',title:'Your mixes',type:'music',html:cardList(data.personal,true)});
  if(state.newEpisodes.length)list.push({id:'new-episodes',title:'New episodes',type:'podcast',html:`<div class="rail">${state.newEpisodes.map(x=>`<button class="card" aria-label="Play ${h(x.episode.title)} from ${h(x.show.title)}, new episode" data-action="new-episode" data-ref="${ref(x)}"><i class="new-dot" aria-hidden="true"></i>${cover(x.episode.albumArtUrl||x.show.artwork,'','','episode',true)}<div class="title">${h(x.episode.title)}</div><p><span class="kind-label">${h(x.show.title)}</span> · ${h(new Date(x.publishedAt).toLocaleDateString(undefined,{month:'short',day:'numeric'}))}</p></button>`).join('')}</div>`});
  list.push({id:'discover',title:'Discover',type:'music',html:discoverBlock()});
  if(state.library.shows.length)list.push({id:'shows',title:'Your shows',type:'podcast',html:cardList(state.library.shows)});
  if(prefs.dynamicRows)for(const r of data.rails||[])if(r.cards?.length)list.push({id:`rail:${r.title}`,title:r.title,type:'music',dynamic:true,html:cardList(r.cards)});
  for(const extra of prefs.homeExtra||[]){const row=extraRow(extra);if(row)list.push(row);}
  if(state.library.history.length)list.push({id:'recent',title:'Recently played',type:'mixed',html:rows(state.library.history.slice(0,5).map(x=>x.track),{from:'Recently played'})});
  return list;
}
function extraRow(id){
  if(id==='liked')return{id,title:'Liked Songs',type:'music',added:true,html:songCards(state.library.favorites,'Liked Songs')};
  if(id==='saves')return{id,title:'Bookmarked for later',type:'mixed',added:true,html:songCards(state.library.saves||[],'Bookmarked for later')};
  if(id.startsWith('playlist:')){const p=state.library.playlists.find(x=>x.id===id.slice(9));return p&&{id,title:p.title,type:'music',added:true,html:songCards(p.tracks,p.title)};}
  if(id.startsWith('smart:')){const s=(prefs.smart||[]).find(x=>x.id===id.slice(6));return s&&{id,title:s.title,type:'music',added:true,html:songCards(smartTracks(s),s.title)};}
  return null;
}
function orderedHome(all){
  const byId=new Map(all.map(x=>[x.id,x])),pinned=(prefs.homePinned||[]).filter(id=>byId.has(id)),order=(prefs.homeOrder||[]).filter(id=>byId.has(id)&&!pinned.includes(id));
  const rest=all.map(x=>x.id).filter(id=>!pinned.includes(id)&&!order.includes(id));
  const fixed=rest.filter(id=>byId.get(id).fixed);
  return [...fixed,...pinned,...order,...rest.filter(id=>!byId.get(id).fixed)].map(id=>byId.get(id));
}
function homeView(){
  const filter=state.homeFilter,all=homeSections(),visible=orderedHome(all).filter(s=>!(prefs.homeHidden||[]).includes(s.id)).filter(s=>filter==='All'||s.type==='mixed'||(filter==='Music'?s.type==='music':s.type==='podcast'));
  return `<div class="home-top">${segmented('Show on Home',['All','Music','Podcasts'],filter,'home-filter')}<button class="pill" data-action="customize-home">${icon('settings')}Customize Home</button></div>${visible.map(s=>{const open=state.expandedRows.has(s.id),expandable=/class="(rail|clips)"/.test(s.html);const body=open?s.html.replace(/class="(rail|clips)"/,'class="$1 expanded"'):s.html;return `<section class="home-row" data-row="${h(s.id)}"><div class="section-title" ${s.fixed?'':`data-row-head="${h(s.id)}" draggable="true" title="Drag to reorder. Right-click for options."`}><h2>${h(s.title)}${(prefs.homePinned||[]).includes(s.id)?` <i class="pin" aria-label="Pinned">${icon('pin')}</i>`:''}</h2>${expandable?`<button class="show-all" data-action="row-expand" data-row="${h(s.id)}" aria-expanded="${open}">${open?'Show less':'Show all'}</button>`:''}</div>${body}</section>`;}).join('')}${(prefs.homeHidden||[]).length?`<p class="muted hidden-note">${(prefs.homeHidden||[]).length} hidden ${(prefs.homeHidden||[]).length===1?'row':'rows'}. <button class="link" data-action="customize-home">Show them again</button></p>`:''}`;
}
function moveRow(id,direction){
  const visible=orderedHome(homeSections()).filter(s=>!s.fixed&&!(prefs.homeHidden||[]).includes(s.id)).map(s=>s.id);
  const pinned=prefs.homePinned||[];
  if(pinned.includes(id)){const at=pinned.indexOf(id),to=at+direction;if(to>=0&&to<pinned.length){[pinned[at],pinned[to]]=[pinned[to],pinned[at]];}prefs.homePinned=pinned;}
  else{const unpinned=visible.filter(x=>!pinned.includes(x)),at=unpinned.indexOf(id),to=at+direction;if(to>=0&&to<unpinned.length)[unpinned[at],unpinned[to]]=[unpinned[to],unpinned[at]];prefs.homeOrder=unpinned;}
  savePrefs();
}

/* ---------- Search, browse, discover ---------- */
function topCard(top){
  const x=top.item,kind=top.kind;
  if(kind==='song'){const queue=String(++reference);queues.set(queue,{tracks:[x],from:`Search: ${state.query}`});return `<button class="top-card" data-action="play" data-queue="${queue}" data-index="0" data-prefetch="${ref(x)}">${cover(x.albumArtUrl,'','','song')}<strong>${h(x.title)}${explicit(x)}</strong><small><span class="kind-chip">Song</span>${h(x.artist)}</small></button>`;}
  const action=kind==='podcast'?'show':'card';
  return `<button class="top-card ${kind==='artist'?'round':''}" data-action="${action}" data-ref="${ref(x)}">${cover(x.artwork,'','',kind)}<strong>${h(x.title)}</strong><small><span class="kind-chip">${h(KIND[kind]?.[0]||'')}</span>${h(x.author||(x.subtitle&&x.subtitle!==kind?x.subtitle:''))}</small></button>`;
}
function searchView(){
  if(!state.query.trim())return `<h1>Search</h1><h2 class="eyebrow">Browse all</h2>${categories()}`;
  const data=state.route.data||{},filter=state.filter,top=data.top,podcastFirst=top?.kind==='podcast';
  const songs=data.tracks||[],playlistFirst=top?.kind==='playlist',topCount=state.library.versions?.[songs[4]?.isrc]?.isrc===songs[5]?.isrc&&songs[5]?6:5;
  const playlists=['All','Playlists'].includes(filter)?cards('Playlists',data.playlists):'';
  const podcasts=['All','Podcasts'].includes(filter)?cards('Podcasts',(data.shows||[]).slice(0,filter==='All'?6:30)):'';
  const head=filter==='All'&&(top||songs.length)?`<div class="search-top">${top?`<section class="top-result"><h2>Top result</h2>${topCard(top)}</section>`:''}<section class="top-songs"><h2>Songs</h2>${rows(songs.slice(0,topCount),{compact:true,from:`Search: ${state.query}`})}</section></div>`:'';
  return `${segmented('Result type',['All','Songs','Albums','Artists','Playlists','Podcasts'],filter,'filter')}${head}${podcastFirst?podcasts:''}${playlistFirst?playlists:''}${filter==='Songs'?`<h2>Songs</h2>${rows(songs.slice(0,100),{from:`Search: ${state.query}`})}`:''}${filter==='All'&&songs.length>topCount?`<h2>More songs</h2>${rows(songs.slice(topCount,12),{from:`Search: ${state.query}`})}`:''}${data.notice&&['All','Songs'].includes(filter)?`<p>${h(data.notice)}</p>`:''}${['All','Artists'].includes(filter)?cards('Artists',data.artists):''}${['All','Albums'].includes(filter)?cards('Albums',data.albums):''}${playlistFirst?'':playlists}${podcastFirst?'':podcasts}${data.podcastNotice&&['All','Podcasts'].includes(filter)?`<p>${h(data.podcastNotice)}</p>`:''}`;
}
function browseView(){const d=state.route.data||{};return `<h1>${h(state.route.title)}</h1><p>${h(d.subtitle||'Discover playlists, albums and new favorites')}</p>${state.route.title==='Music'?cards('Daily Mixes',state.home.mixes,true):''}${(d.rails||[]).map(x=>cards(x.title,x.cards)).join('')}${d.tracks?.length?`<h2>${state.route.title==='Charts'?'Leading the US chart':'Songs to discover'}</h2>${rows(d.tracks,{from:state.route.title})}`:''}${cards('Podcasts',d.shows)}`;}
const discoverCategories=['For you','Pop','Indie','Hip-Hop','Alternative','Ambient','Folk & Acoustic','New releases'];
function discoverTracks(){const c=state.discover.category;if(c==='For you'){const seen=new Set();return [...(state.home.mixes||[]).flatMap(m=>m.tracks||[])].filter(t=>!saved(t)&&!hidden(t)&&!seen.has(t.isrc)&&seen.add(t.isrc)).slice(0,40);}return (state.discover.cache.get(c)?.tracks||[]).filter(t=>!hidden(t));}
function discoverBlock(){
  const c=state.discover.category,tracks=discoverTracks(),queue=String(++reference);queues.set(queue,{tracks,from:`Discover · ${c}`});
  return `<div class="discover-head"><p class="muted">Short previews of new music. Bookmark what catches your ear and listen properly later.</p><button class="pill" data-action="saves">${icon('bookmark')}Bookmarked · ${(state.library.saves||[]).length}</button></div><div class="genre-strip" role="tablist" aria-label="Discover categories">${discoverCategories.map(x=>`<button role="tab" aria-selected="${x===c}" class="genre ${x===c?'on':''}" data-action="discover-category" data-value="${h(x)}">${h(x)}</button>`).join('')}</div>${state.discover.loading?skeleton('rail'):tracks.length?`<div class="clips">${tracks.map((t,i)=>{const previewing=state.preview===t.isrc&&state.now?.isrc===t.isrc;return `<article class="clip ${previewing?'previewing':''}" data-prefetch="${ref(t)}" aria-label="${h(t.title)} by ${h(t.artist)}">${cover(t.albumArtUrl,'','','song')}<div class="clip-body"><strong>${h(t.title)}${explicit(t)}</strong><small>${h(t.artist)}</small><div class="clip-actions"><button class="play-big" data-action="preview" data-queue="${queue}" data-index="${i}" aria-label="${previewing&&state.playing?'Pause preview of':'Preview'} ${h(t.title)}">${icon(previewing&&state.playing?'pause':'play')}</button>${heart(t,'favorite')}${mark(t)}<button class="icon" data-action="add-to-playlist" data-ref="${ref(t)}" aria-label="Add ${h(t.title)} to a playlist">${icon('addlist')}</button><button class="icon" data-action="play" data-queue="${queue}" data-index="${i}" aria-label="Play full song ${h(t.title)}" title="Play full song">${icon('expand')}</button></div></div></article>`;}).join('')}</div>`:`<p class="empty-state">${c==='For you'?'Listen to a few songs and your previews appear here.':'Nothing to preview here right now. Try another category.'}</p>`}`;
}
async function loadDiscover(){
  const c=state.discover.category;
  if(c!=='For you'&&!state.discover.cache.has(c)){state.discover.loading=true;if(state.route.type==='home')render();try{state.discover.cache.set(c,await call('browse',c));}catch(e){error(e);}finally{state.discover.loading=false;if(state.route.type==='home')render();}}
  // Resolve the first previews in the background so pressing play starts right away.
  const first=discoverTracks().slice(0,2);if(first.length)void call('prefetch',first).catch(()=>{});
}

/* ---------- Collections: album, playlist, artist, podcast ---------- */
function pageKind(r,d){if(r.type==='show'||d.show)return'podcast';if(d.liked||d.saves||d.smart||d.local)return'playlist';if(d.madeForYou)return'mix';const uri=String(d.uri||r.uri||'');if(r.type==='album'||uri.startsWith('spotify:album:'))return'album';if(uri.startsWith('spotify:artist:')||r.type==='library-artist')return'artist';return'playlist';}
function totalTime(tracks){const s=tracks.reduce((n,t)=>n+(t.durationMs||0),0)/1000;const hr=Math.floor(s/3600),m=Math.round(s%3600/60);return hr?`${hr} hr ${m} min`:`${m} min`;}
function contributors(tracks){return [...new Set(tracks.flatMap(t=>String(t.artist||'').split(',').map(x=>x.trim())).filter(Boolean))].slice(0,12);}
function collectionView(){
  const r=state.route,d=r.data||{},tracks=d.tracks||[],kind=pageKind(r,d);
  if(kind==='artist')return artistView(d);
  const isShow=kind==='podcast',label=isShow?'Podcast':kind==='album'?'Album':kind==='mix'?'Made for you':d.radio?'Radio':d.smart?'Smart playlist':'Playlist';
  const people=kind==='album'?contributors(tracks):[];
  const head=`<div class="collection-head ${kind==='album'?'album-head':''}">${cover(d.artwork,d.liked?'liked':d.saves?'saves':'','',kind==='mix'?'mix':kind)}<div class="collection-info"><span class="collection-type">${h(label)}</span><h1 title="${h(d.title||r.title)}">${h(d.title||r.title)}</h1>${d.description?`<p class="description">${h(d.description)}</p>`:''}<div class="meta">${h(d.subtitle&&d.subtitle!==label?d.subtitle:'')}${tracks.length?`${d.subtitle&&d.subtitle!==label?' · ':''}${tracks.length} ${isShow?(tracks.length===1?'episode':'episodes'):(tracks.length===1?'song':'songs')}${isShow?'':`, ${totalTime(tracks)}`}`:''}${d.skipped?` · ${d.skipped} unavailable items skipped`:''}</div>${people.length?`<div class="contributors"><span class="muted">Artists on this album</span>${people.map(p=>`<button class="person" data-action="search-for" data-value="${h(p)}">${icon('artist')}${h(p)}</button>`).join('')}</div>`:''}</div></div>`;
  const here=collectionPlaying(),pause=here&&(state.playing||state.opening);
  const actions=`<div class="collection-actions"><button class="play-big" data-action="play-collection" aria-label="${pause?'Pause':'Play collection'}" ${tracks.length?'':'disabled'}>${icon(pause?'pause':'play')}</button>${d.uri&&/^spotify:(playlist|album):/.test(d.uri)?`<button class="secondary" data-action="import-collection">Add to Your Library</button>`:''}${isShow?`<button class="secondary" data-action="follow-current">${state.library.shows.some(x=>x.feedUrl===d.show.feedUrl)?'Following':'Follow'}</button>`:''}${d.smartDraft?`<button class="secondary" data-action="save-smart">Save as smart playlist</button>`:''}${!isShow&&tracks.length?findInPage(label.toLowerCase()):''}</div>`;
  const body=isShow?`<h2>All episodes</h2>${tracks.map((t,index)=>`<article class="episode"><div class="episode-title">${cover(t.albumArtUrl,'','','episode')}<h3>${h(t.title)}</h3></div><p>${h(t.description||'').slice(0,700)}</p><div class="episode-actions"><span>${h(t.published?.slice(0,25)||'')} · ${time(t.durationMs/1000)}</span><button class="icon ${later(t)?'saved':''}" data-action="save-later" data-ref="${ref(t)}" aria-label="${later(t)?'Remove episode from Saves':'Save episode for later'}">${icon('bookmark',later(t))}</button><button class="play-big" data-action="episode" data-index="${index}" aria-label="Play ${h(t.title)}">${icon('play')}</button></div></article>`).join('')}`:`${bulkBar()}<div class="track-list">${rows(tracks,{from:d.title||r.title})}</div>`;
  return head+actions+body+(d.albums?.length?cards('Albums and singles',d.albums):'');
}
// True when what is playing came from the collection on screen, so its big button pauses instead of restarting.
function collectionPlaying(){const d=state.route.data;if(!d||!state.now)return false;const title=d.title||state.route.title;return state.now._from===title&&(d.tracks||[]).some(t=>t.isrc===state.now.isrc);}
function syncCollectionButton(){const b=document.querySelector('#content .play-big[data-action="play-collection"]');if(!b)return;const pause=collectionPlaying()&&(state.playing||state.opening);b.setAttribute('aria-label',pause?'Pause':'Play collection');b.innerHTML=icon(pause?'pause':'play');}
function artistView(d){
  const tracks=d.tracks||[],name=d.title||state.route.title,needle=name.toLowerCase(),tab=state.artistTab;
  const yours=[...state.library.history].filter(x=>x.track.artist.toLowerCase().includes(needle)).sort((a,b)=>(b.plays||1)-(a.plays||1)).map(x=>x.track).slice(0,5);
  const albums=d.albums||[];
  const tabs=`<div class="page-tabs" role="tablist" aria-label="Artist sections">${[['overview','Overview'],['releases',`Releases${albums.length?` (${albums.length})`:''}`],['about','About']].map(([id,title])=>`<button role="tab" aria-selected="${tab===id}" class="${tab===id?'on':''}" data-action="artist-tab" data-value="${id}">${h(title)}</button>`).join('')}</div>`;
  let body='';
  if(tab==='overview')body=`<div class="artist-overview"><div>${yours.length?`<h2>Your most played</h2>${rows(yours,{from:`${name} · your most played`})}`:''}${tracks.length?`<h2>Popular</h2>${bulkBar()}${rows(tracks.slice(0,10),{from:`${name} · popular`})}`:''}</div><aside class="artist-side">${d.artist?.latest?`<h3>Latest release</h3><button class="mini-release" data-action="card" data-ref="${ref(d.artist.latest)}">${cover(d.artist.latest.artwork,'','','album')}<span>${h(d.artist.latest.title)}<small>${h(d.artist.latest.subtitle||'Album')}</small></span></button>`:''}${albums.length?`<h3>Releases</h3>${albums.slice(0,3).map(a=>`<button class="mini-release" data-action="card" data-ref="${ref(a)}">${cover(a.artwork,'','','album')}<span>${h(a.title)}<small>Album</small></span></button>`).join('')}`:''}<h3>About</h3><p>${h((d.description||'No biography available yet.').slice(0,240))}${(d.description||'').length>240?'…':''}</p><button class="link" data-action="artist-tab" data-value="about">Read more</button></aside></div>${cards('Fans also like',d.artist?.related)}`;
  else if(tab==='releases')body=`<div class="list-tools">${segmented('Release view',['List','Grid'],prefs.releasesView==='grid'?'Grid':'List','releases-view')}</div>${!albums.length?'<p class="empty-state">No releases found.</p>':prefs.releasesView==='grid'?cardList(albums):albums.map(a=>{const open=state.openReleases.has(a.uri),loaded=state.releases.get(a.uri);return `<section class="release ${open?'open':''}"><button class="release-head" data-action="toggle-release" data-ref="${ref(a)}" aria-expanded="${open}">${icon(open?'down':'next')}${cover(a.artwork,'','','album')}<span><strong>${h(a.title)}</strong><small>Album${loaded?.tracks?` · ${loaded.tracks.length} songs`:''}</small></span></button>${open?`<div class="release-body">${!loaded||loaded.loading?skeleton('words'):loaded.error?`<p>${h(loaded.error)}</p>`:`${rows(loaded.tracks,{from:a.title})}<button class="link" data-action="card" data-ref="${ref(a)}">Open album page</button>`}</div>`:''}</section>`;}).join('')}`;
  else body=`<div class="about"><p>${h(d.description||'No biography available yet.')}</p><dl><dt>Popular songs</dt><dd>${tracks.length}</dd><dt>Releases</dt><dd>${albums.length}</dd>${d.artist?.monthlyListeners!=null?`<dt>Monthly listeners</dt><dd>${d.artist.monthlyListeners.toLocaleString()}</dd>`:''}${d.artist?.followers!=null?`<dt>Followers</dt><dd>${d.artist.followers.toLocaleString()}</dd>`:''}<dt>In your library</dt><dd>${state.library.favorites.filter(t=>t.artist.toLowerCase().includes(needle)).length} liked songs</dd></dl></div>`;
  const info=d.artist||{},stats=[info.monthlyListeners!=null?`${info.monthlyListeners.toLocaleString()} monthly listeners`:'',info.followers!=null?`${info.followers.toLocaleString()} followers`:''].filter(Boolean).join(' · ');
  return `<div class="collection-head">${cover(d.artwork,'','','artist')}<div class="collection-info"><span class="collection-type">${info.verified?`${icon('check')} Verified artist`:'Artist'}</span><h1>${h(name)}</h1>${stats?`<div class="meta">${h(stats)}</div>`:''}</div></div><div class="collection-actions"><button class="play-big" data-action="play-artist" aria-label="Play ${h(name)}" ${tracks.length||yours.length?'':'disabled'}>${icon('play')}</button><button class="secondary" data-action="artist-radio">Artist radio</button>${findInPage('this artist')}</div>${tabs}${body}`;
}

/* ---------- Library page, profile, import ---------- */
// Suggestions that fill the empty end of the library grid. They come from Home's recommendation rows.
function recommendations(){
  const have=new Set([...state.library.playlists.map(p=>p.title.toLowerCase()),...state.library.shows.map(x=>x.title.toLowerCase())]),skip=new Set(prefs.recHidden||[]);
  const seen=new Set();return [...(state.home.mixes||[]).map(m=>({...m,mix:true})),...(state.home.rails||[]).flatMap(r=>r.cards||[])].filter(x=>{const id=x.uri||x.id;if(!id||seen.has(id)||skip.has(id)||have.has(String(x.title).toLowerCase()))return false;seen.add(id);return true;}).slice(0,24);
}
function recTile(x){return `<button class="library-tile rec" data-action="${x.mix?'mix':cardAction(x)}" data-ref="${ref(x)}" data-rec="${h(x.uri||x.id)}">${cover(x.artwork||x.tracks?.[0]?.albumArtUrl,'',x.mix?x.title:'',x.mix?'mix':kindOf(x),!x.mix)}<div><span>${h(x.mix?'Made for you':x.title)}</span><small><em class="rec-tag">Recommended</em> · ${h(x.subtitle||KIND[kindOf(x)]?.[0]||'')}</small></div></button>`;}
function libraryView(){
  const all=libraryItems(),filter=state.libraryFilter,q=state.libraryQuery.trim().toLowerCase();
  const keyFor={Playlists:'playlists',Artists:'artists',Podcasts:'podcasts'};
  let items=filter==='All'?arrange([...all.playlists,...all.podcasts,...all.artists]):filter==='Bookmarked'?(state.library.saves||[]).map(t=>({...t,kind:'song',action:'play-one',artwork:t.albumArtUrl})):arrange(all[keyFor[filter]]||[]);
  if(q)items=items.filter(x=>`${x.title} ${x.artist||''} ${x.author||''}`.toLowerCase().includes(q));
  if(state.librarySort==='az')items=[...items].sort((a,b)=>a.title.localeCompare(b.title));
  const grid=prefs.libraryView!=='list',recs=grid&&filter==='All'&&!q?recommendations():[];
  return `<h1>Your Library</h1><div class="list-tools">${segmented('Library section',['All','Playlists','Bookmarked','Artists','Podcasts'],filter,'library-filter')}<label class="find">${icon('search')}<input id="library-find" value="${h(state.libraryQuery)}" placeholder="Search in Your Library" aria-label="Search in Your Library"></label><label class="sort">Sort <select id="library-sort" aria-label="Sort Your Library"><option value="recent" ${state.librarySort==='recent'?'selected':''}>Recents</option><option value="az" ${state.librarySort==='az'?'selected':''}>Alphabetical</option></select></label>${segmented('Library view',['Grid','List'],grid?'Grid':'List','library-view')}<button class="pill" data-action="create">${icon('plus')}New playlist</button></div>${items.length||recs.length?`<div class="${grid?'library-grid':'library-list'}">${items.map(x=>libraryItem(x,grid?'library-tile':'library-row')).join('')}${recs.map(recTile).join('')}</div>`:'<p class="empty-state">Nothing matches.</p>'}<p class="muted tip">Right-click anything to pin it or remove it. Recommendations fill the empty space at the end of the grid.</p>`;
}
// Show only as many recommendations as it takes to finish the last row and fill the visible page.
function fillRecommendations(){
  const grid=document.querySelector('.library-grid');if(!grid)return;const recs=[...grid.querySelectorAll('.rec')];if(!recs.length)return;
  recs.forEach(x=>x.hidden=false);
  const cols=getComputedStyle(grid).gridTemplateColumns.split(' ').filter(Boolean).length||1,owned=grid.children.length-recs.length;
  const tile=grid.firstElementChild?.getBoundingClientRect().height||220,room=document.querySelector('#main').clientHeight-(grid.getBoundingClientRect().top-document.querySelector('#main').getBoundingClientRect().top);
  const rows=Math.max(Math.ceil(owned/cols)+1,Math.ceil(Math.max(0,room)/(tile+22)));
  const want=Math.min(recs.length,Math.max(0,rows*cols-owned));
  recs.forEach((x,i)=>x.hidden=i>=want);
}
function avatar(){return /^data:image\/(jpeg|png|webp);base64,/.test(prefs.avatar||'')?`<img src="${h(prefs.avatar)}" alt="">`:h((prefs.displayName||'You').trim().charAt(0).toUpperCase()||'Y');}
function handle(){return `@${(prefs.displayName||'listener').toLowerCase().replace(/[^a-z0-9]+/g,'.').replace(/^\.|\.$/g,'')||'listener'}`;}
function profileView(){
  const hist=state.library.history,plays=hist.reduce((n,x)=>n+(x.plays||1),0),top=libraryArtists().slice(0,5);
  return `<section class="profile-card"><div class="avatar-wrap"><label class="avatar" title="Change profile picture">${avatar()}<input type="file" id="avatar-input" accept="image/png,image/jpeg,image/webp" aria-label="Choose a profile picture"><span class="avatar-edit" aria-hidden="true">${icon('plus')}</span></label>${prefs.avatar?'<button class="link" data-action="avatar-remove">Remove photo</button>':'<small class="muted">Click to add a photo</small>'}</div><div><span class="collection-type">Profile</span><form id="name-form" class="inline-form"><input name="name" value="${h(prefs.displayName)}" placeholder="Your name" aria-label="Display name" maxlength="40"><button class="secondary">Save name</button></form><div class="handle"><code>${h(handle())}</code><button class="pill" data-action="copy-handle">${icon('copy')}Copy username</button></div><p class="muted">${state.library.favorites.length} liked songs · ${state.library.playlists.length} playlists · ${state.library.shows.length} podcasts followed</p></div></section>
  <section class="profile-section"><h2>Friends</h2><p class="muted">Your library lives on this computer, so there is no friends list to sync yet. Share your username so friends can find your playlists once accounts are connected.</p></section>
  <section class="profile-section"><h2>Your listening</h2>${top.length?`<div class="top-artists">${top.map(a=>`<button class="person" data-action="library-artist" data-ref="${ref(a)}">${cover(a.artwork,'','','artist')}<span>${h(a.title)}<small>${a.plays} plays</small></span></button>`).join('')}</div>`:''}<p class="muted">${plays} plays across ${hist.length} songs. Daily Mixes use this history and refresh as you listen.</p>${hist.length?`<h3>Listening history</h3>${rows(hist.slice(0,20).map(x=>x.track),{from:'Listening history'})}`:''}</section>
  <section class="profile-section"><h2>Settings</h2><div class="setting"><div><strong>Appearance</strong><p>Choose dark, light, or match your computer.</p></div>${segmented('Appearance',['Dark','Light','System'],prefs.theme==='light'?'Light':prefs.theme==='system'?'System':'Dark','theme')}</div><div class="setting"><div><strong>Accent color</strong><p>Purple by default. Pick a swatch or slide to any hue.</p></div><div class="hues">${[[268,'Purple'],[215,'Blue'],[172,'Teal'],[140,'Green'],[28,'Orange'],[340,'Pink']].map(([v,n])=>`<button class="swatch ${Number(prefs.accentHue)===v?'on':''}" data-action="accent" data-value="${v}" data-hue="${v}" aria-label="${n}" aria-pressed="${Number(prefs.accentHue)===v}" title="${n}"></button>`).join('')}<input id="hue-range" class="range hue-range" type="range" min="0" max="359" value="${h(prefs.accentHue)}" aria-label="Accent hue"></div></div><div class="setting"><div><strong>Autoplay</strong><p>When your queue ends, keep playing similar songs.</p></div>${segmented('Autoplay',['On','Off'],prefs.autoplay===false?'Off':'On','autoplay')}</div><div class="setting"><div><strong>Lyrics text size</strong><p>${Math.round((prefs.lyricScale||1)*100)}%</p></div>${textSize()}</div><div class="setting"><div><strong>Home</strong><p>Pin, reorder, hide or add rows.</p></div><button class="secondary" data-action="customize-home">Customize Home</button></div><div class="setting"><div><strong>Hidden songs</strong><p>${(state.library.hidden||[]).length} hidden. Hidden songs are skipped during playback.</p></div></div>${(state.library.hidden||[]).map(t=>`<div class="hidden-song"><span>${h(t.title)} <small class="muted">${h(t.artist)}</small></span><button class="link" data-action="unhide" data-ref="${ref(t)}">Unhide</button></div>`).join('')}<div class="setting"><div><strong>Bring your music</strong><p>Import public Spotify playlists and albums.</p></div><button class="secondary" data-action="nav" data-page="import">Import</button></div></section>
  <section class="profile-section"><h2>About</h2><p>Built on Gamut with Spotify catalog integration adapted from Aran404/SpotAPI. Spotify supplies metadata; music playback uses matched public audio sources.</p><p class="notice">Independent project. Not affiliated with Spotify.</p><button class="secondary" data-action="licenses">Open source licenses</button></section>`;
}
function importView(){
  const a=state.account,all=[...a.mixes,...a.playlists],count=all.filter(x=>a.picked.has(x.uri)).length;
  const pick=x=>`<label class="pick-row"><input type="checkbox" data-action="spotify-pick" data-value="${h(x.uri)}" ${a.picked.has(x.uri)?'checked':''} ${a.progress?'disabled':''} aria-label="${h(x.title)}">${cover(x.artwork,'','','playlist')}<span><strong>${h(x.title)}</strong><small>${h(x.subtitle||'')}</small></span></label>`;
  const head=a.signedIn?`<div class="account-row"><div><strong>${a.name?`Signed in as ${h(a.name)}`:'Signed in to Spotify'}</strong><p class="muted">${a.loading?'Loading your playlists and mixes…':`${a.mixes.length} ${a.mixes.length===1?'mix':'mixes'} · ${a.playlists.length} ${a.playlists.length===1?'playlist':'playlists'}`}</p></div><button class="secondary" data-action="spotify-sign-out" ${a.progress?'disabled':''}>Sign out</button></div>
    ${all.length?`<div class="account-row"><button class="secondary" data-action="spotify-select-all" ${a.progress?'disabled':''}>${count===all.length?'Clear selection':'Select all'}</button><button class="primary" data-action="spotify-import" ${count&&!a.progress?'':'disabled'}>${count?`Import ${count}`:'Import'}</button></div>`:''}
    ${a.progress?`<p class="notice">${h(a.progress)}</p>`:''}${a.error?`<p class="notice">${h(a.error)} <button class="secondary" data-action="${/expired/.test(a.error)?'spotify-sign-in':'spotify-retry'}">${/expired/.test(a.error)?'Sign in again':'Retry'}</button></p>`:''}`
    :`<p>Log in with Spotify to bring over some or all of your playlists, plus your mixes like Daily Mix, Discover Weekly and Release Radar.</p><button class="primary" data-action="spotify-sign-in" ${a.loading?'disabled':''}>${a.loading?'Waiting for Spotify…':'Log in with Spotify'}</button><p class="muted small">Spotify's own sign-in page opens in a separate window. Cadence keeps only the sign-in session, encrypted on this computer.</p>`;
  return `<div class="import-page"><h1>Bring your music with you</h1><section class="account-card">${head}</section>
    ${a.signedIn&&a.loading?skeleton('rail'):''}${a.mixes.length?`<h2>Your mixes</h2><div class="pick-list">${a.mixes.map(pick).join('')}</div>`:a.signedIn&&!a.loading&&!a.error?'<p class="muted">No mixes found yet. Spotify makes them after some listening.</p>':''}
    ${a.playlists.length?`<h2>Your playlists</h2><div class="pick-list">${a.playlists.map(pick).join('')}</div>`:''}
    <div class="form"><h2>Or paste a link</h2><p>Paste a public Spotify playlist or album link. Keep its songs, order and artwork in your library.</p><form id="import-form"><label for="spotify-link">Spotify link</label><input id="spotify-link" name="link" placeholder="https://open.spotify.com/playlist/…" required><button class="primary" ${state.importing?'disabled':''}>${state.importing?'Importing…':'Import songs'}</button></form></div></div>`;
}
async function loadSpotify(force=false){
  const a=state.account;if(a.loading||a.progress)return;
  if(!force&&a.checked&&(!a.signedIn||a.playlists.length||a.mixes.length))return;
  a.loading=true;a.error=null;if(state.route.type==='import')render();
  try{
    const status=await call('spotifyAccount');a.checked=true;a.signedIn=status.signedIn;
    if(!status.signedIn){a.playlists=[];a.mixes=[];return;}
    const lib=await call('spotifyLibrary');a.signedIn=lib.signedIn!==false;a.name=lib.name||null;a.playlists=lib.playlists||[];a.mixes=lib.mixes||[];a.error=lib.error||null;
  }catch(e){a.error=e.message;}
  finally{a.loading=false;if(state.route.type==='import')render();}
}
async function importSpotify(){
  const a=state.account,cards=[...a.mixes,...a.playlists].filter(x=>a.picked.has(x.uri));if(!cards.length||a.progress)return;
  let done=0,failed=0,songs=0;
  try{for(const [i,card] of cards.entries()){
    a.progress=`Importing ${i+1} of ${cards.length}: ${card.title}`;render();
    try{const result=await call('import',card.uri);state.library=result.library;done++;songs+=result.count;a.picked.delete(card.uri);}catch{failed++;}
  }}finally{a.progress=null;render();}
  toast(`Imported ${done} ${done===1?'collection':'collections'} · ${songs} songs${failed?` · ${failed} couldn't be imported`:''}`);
}
const cleanBase=title=>String(title).split(' (')[0].split(' - ')[0]||String(title);
const cleanFromVideo=(song,v)=>({isrc:`youtube:${v.videoId}`,title:`${cleanBase(song.title)} (Clean)`,artist:song.artist,album:song.album,durationMs:v.durationMs>0?v.durationMs:song.durationMs,albumArtUrl:song.albumArtUrl,source:'YOUTUBE',youtubeVideoId:v.videoId,isExplicit:false});
/** Makes a clean version and keeps both: it goes right after the explicit song in the library, the page on screen and the queue. */
let cleanTimer=null;
function cleanBar(p){let el=document.querySelector('#clean-progress');if(!p){el?.remove();return;}if(!el){el=document.createElement('div');el.id='clean-progress';el.setAttribute('role','status');document.body.append(el);}const pct=Math.round(Math.max(0,Math.min(1,Number(p.fraction)||0))*100);el.innerHTML=`<strong>Making a clean version of ${h(p.title)}</strong><span>${pct}%</span><small>${h(p.step)}</small><div class="bar"><i style="width:${pct}%"></i></div>`;}
// Shows how far along finding and checking a clean version is, polled from the main process.
function watchClean(song){clearInterval(cleanTimer);cleanTimer=null;if(!song){cleanBar(null);return;}cleanBar({title:song.title,step:'Starting',fraction:0});cleanTimer=setInterval(async()=>{try{const p=await call('cleanProgress');if(p&&cleanTimer)cleanBar(p);}catch{}},500);}
async function applyClean(song,clean,verified=false){
  state.library=await call('addVersion',song.isrc,clean);
  const add=list=>{if(!Array.isArray(list)||!list.some(t=>t.isrc===song.isrc))return list;const rest=list.filter(t=>t.isrc!==clean.isrc),at=rest.findIndex(t=>t.isrc===song.isrc);return [...rest.slice(0,at+1),{...clean,...(rest[at]._from?{_from:rest[at]._from}:{})},...rest.slice(at+1)];};
  if(state.route.data?.tracks)state.route.data.tracks=add(state.route.data.tracks);refreshDetail();
  const before=state.queue.slice(0,state.index+1).some(t=>t.isrc===song.isrc)&&state.queue[state.index]?.isrc!==song.isrc;state.queue=add(state.queue);if(before)state.index=Math.max(0,state.queue.findIndex(t=>t.isrc===state.now?.isrc));
  toast(verified?`Made a clean version of ${cleanBase(song.title)}. Cadence listened to it and heard no swearing. Both versions are saved.`:`Added a clean version of ${cleanBase(song.title)}, but Cadence couldn't confirm it's clean. Both versions are saved.`);render();
}
async function loadNewEpisodes(force=false){
  if(!state.library.shows.length||(!force&&Date.now()-state.newEpisodesAt<30*60000))return;state.newEpisodesAt=Date.now();
  try{state.newEpisodes=await call('newEpisodes');if(state.route.type==='home'||state.route.type==='library')render();else renderSidebar();}catch{}
}
async function markSeen(show){if(!show?.feedUrl)return;state.newEpisodes=state.newEpisodes.filter(x=>x.show.feedUrl!==show.feedUrl);try{state.library=await call('markSeen',show.feedUrl);}catch{}}

function render(){
  const focused=document.activeElement?.id,selection=document.activeElement?.selectionStart;refs.clear();queues.clear();renderSidebar();renderToolbar();const r=state.route;
  content.innerHTML=(r.type==='home'?homeView():r.type==='search'?searchView():r.type==='browse'?browseView():['detail','album','collection','show','library-artist','radio'].includes(r.type)?collectionView():r.type==='library'?libraryView():r.type==='import'?importView():profileView())+(r.loading?skeleton():'')+(r.error?`<div class="notice">${h(r.error)} <button class="secondary" data-action="retry">Retry</button></div>`:'');
  renderPlayer();renderOverlays();fillRecommendations();paintSwatches();
  if(focused&&['search-input','page-find','library-find'].includes(focused)){const input=document.getElementById(focused);input?.focus();if(typeof selection==='number')input?.setSelectionRange(selection,selection);}
}

/* ---------- Player bar and overlays ---------- */
function progress(){return `<div class="progress"><span data-time="current">${time(audio.currentTime)}</span><input class="range" data-seek type="range" min="0" max="1000" value="0" aria-label="Playback position"><span data-time="total">${time(duration())}</span></div>`;}
function controls(){return `<div class="controls"><button class="icon ${state.shuffle?'saved':''}" data-action="shuffle" aria-pressed="${state.shuffle}" aria-label="Shuffle">${icon('shuffle')}</button>${button('previous','Previous song','previous')}<button class="icon pause" data-action="toggle" aria-label="${state.playing?'Pause':'Play'}">${state.opening?'<span class="skeleton pending-play"></span>':icon(state.playing?'pause':'play')}</button>${button('next','Next song','next')}<button class="icon ${state.repeat?'saved':''}" data-action="repeat" aria-pressed="${state.repeat}" aria-label="Repeat">${icon('repeat')}</button></div>`;}
function likeButtons(track,action='favorite-now'){return `${heart(track,action)}<button class="icon" data-action="playlist-now" aria-label="Add to playlist" title="Add to playlist">${icon('addlist')}</button>`;}
function applyBarColor(){
  const bar=document.querySelector('#player'),c=state.accent;
  if(!state.now||!c||!state.playing){bar.style.removeProperty('--bar');bar.classList.remove('tinted');return;}
  const light=document.documentElement.dataset.theme==='light';
  bar.style.setProperty('--bar',light?`hsl(${c.h} ${Math.max(25,c.s)}% 84%)`:`hsl(${c.h} ${Math.max(25,c.s)}% 24%)`);bar.classList.add('tinted');
}
function renderPlayer(){syncCollectionButton();
  const bar=document.querySelector('#player');
  bar.innerHTML=state.now?`<div class="bar-left">${controls()}</div><div class="bar-center"><div class="now">${cover(state.now.albumArtUrl)}<div class="now-name" data-action="full" role="button" tabindex="0" aria-label="Open now playing: ${h(state.now.title)}"><strong>${h(state.now.title)}</strong><small>${h(state.now.artist)}${state.preview===state.now.isrc?' · Preview':''}</small></div>${likeButtons(state.now)}${button('menu-now','Song options','more')}</div>${progress()}</div><div class="player-right"><button class="icon ${state.lyricsPopup?'saved':''}" data-action="lyrics-popup" aria-pressed="${state.lyricsPopup}" aria-label="${state.now.source==='PODCAST'?'Subtitles':'Lyrics'}" title="${state.now.source==='PODCAST'?'Subtitles':'Lyrics'}">${icon('lyrics')}</button>${button('queue','Queue','queue')}<button class="icon ${state.sleep?'saved':''}" data-action="sleep" aria-label="Sleep timer${state.sleep?' (on)':''}" title="Sleep timer">${icon('timer')}</button>${button('full','Open player','expand')}<span class="icon" aria-hidden="true">${icon('volume')}</span><input id="volume" class="range volume" type="range" min="0" max="1" step=".01" value="${audio.volume}" aria-label="Volume"></div>`:`<div class="muted idle">Choose a song or podcast to start listening.</div>`;
  applyBarColor();syncProgress();
}
function menuItem(action,title,name,fill=false){return `<button class="menu-item" data-action="${action}">${icon(name,fill)}${h(title)}</button>`;}
function modal(label,body,wide=false){return `<div class="overlay" data-dismiss><section class="modal ${wide?'wide':''}" role="dialog" aria-modal="true" aria-label="${h(label)}">${button('close','Close','close','close-modal')}${body}</section></div>`;}
function queueView(){
  const upcoming=state.queue.slice(state.index+1).map((t,i)=>({t,at:state.index+1+i})),groups=[];
  for(const x of upcoming){const from=x.t._from||'Queue';const last=groups.at(-1);if(last&&last.from===from)last.items.push(x);else groups.push({from,items:[x]});}
  return `<h2>Queue</h2>${state.now?`<h3 class="eyebrow">Now playing</h3>${rows([state.now],{compact:true,from:state.now._from})}`:''}${groups.length?`<div class="queue-tools"><button class="pill ghost" data-action="queue-clear">Clear upcoming</button></div>`:'<p class="muted">Nothing queued after this song.</p>'}${groups.map((g,gi)=>`<section class="queue-group"><div class="queue-group-head"><h3>Next from: ${h(g.from)} <small class="muted">${g.items.length}</small></h3>${button('queue-shuffle-group',`Shuffle ${g.from}`,'shuffle','small',`data-group="${gi}"`)}${button('queue-remove-group',`Remove ${g.from} from queue`,'close','small',`data-group="${gi}"`)}</div>${g.items.map(({t,at})=>`<div class="queue-item">${cover(t.albumArtUrl)}<button class="queue-title" data-action="queue-jump" data-index="${at}"><span>${h(t.title)}</span><small>${h(t.artist)}</small></button>${button('queue-up',`Move ${t.title} up`,'up','small',`data-index="${at}"`)}${button('queue-remove',`Remove ${t.title} from queue`,'close','small',`data-index="${at}"`)}</div>`).join('')}</section>`).join('')}`;
}
function queueGroups(){const groups=[];for(let i=state.index+1;i<state.queue.length;i++){const from=state.queue[i]._from||'Queue';const last=groups.at(-1);if(last&&last.from===from)last.indices.push(i);else groups.push({from,indices:[i]});}return groups;}
// Fullscreen playback, after a read-along layout: cover and controls on the left, large scrolling words on the right,
// over slow moving lights tinted from the album colour.
function fullPlayer(){
  const t=state.now,podcast=t.source==='PODCAST',title=podcast?'Subtitles':'Lyrics';
  return `<div class="overlay full-overlay"><section class="now-full" role="dialog" aria-modal="true" aria-label="Now playing"><div class="lights" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div><header class="now-full-head">${button('close','Close player','back')}<span class="from">Playing from <strong>${h(t._from||t.album||t.artist)}</strong></span>${textSize()}${button('menu-now','Song options','more')}</header><div class="now-full-body"><div class="now-left">${cover(t.albumArtUrl,'now-art')}<div class="full-title"><div><h2>${h(t.title)}${explicit(t)}</h2><p>${h(t.artist)}</p></div>${likeButtons(t)}${mark(t)}</div>${progress()}${controls()}${podcast?'<div class="controls skip"><button class="secondary" data-action="rewind">−15 seconds</button><button class="secondary" data-action="forward">+15 seconds</button></div>':''}<div class="full-options">${button('queue','Queue','queue')}${button('sleep','Sleep timer','timer',state.sleep?'saved':'')}${button('menu-now','More options','more')}</div></div><section class="now-words" aria-label="${title}"><div class="words-body readalong">${wordsBody(state,h)}</div></section></div></section></div>`;
}
function applyLights(){const el=document.querySelector('.now-full');if(!el)return;const c=state.accent||{h:Number(prefs.accentHue)||268,s:45};el.style.setProperty('--lh',String(Math.round(c.h)));el.style.setProperty('--ls',`${Math.round(Math.max(35,Math.min(80,c.s+15)))}%`);}
function contextMenu(){const c=state.ctx;return `<div class="ctx-layer" data-ctx-dismiss><div class="ctx" role="menu" aria-label="${h(c.title||'Options')}">${c.title?`<div class="ctx-title">${h(c.title)}</div>`:''}${c.items.map(([label,name,action,value,fill])=>label==='-'?'<hr>':`<button class="menu-item" role="menuitem" data-action="${action}" ${value!==undefined?`data-ref="${ref(value)}"`:''} data-ctx="1">${icon(name,fill)}${h(label)}</button>`).join('')}</div></div>`;}
function placeContextMenu(){const el=document.querySelector('.ctx');if(!el||!state.ctx)return;const r=el.getBoundingClientRect(),x=Math.min(state.ctx.x,innerWidth-r.width-8),y=Math.min(state.ctx.y,innerHeight-r.height-8);el.style.left=`${Math.max(8,x)}px`;el.style.top=`${Math.max(8,y)}px`;el.querySelector('button')?.focus();}
function songMenu(t){const music=t.source!=='PODCAST';return [[saved(t)?'Remove from Liked Songs':'Add to Liked Songs','heart','menu-save',t,saved(t)],['Add to playlist','addlist','menu-append',t],['Add to queue','queue','menu-queue',t],[later(t)?'Remove from Bookmarked':'Bookmark for later','bookmark','menu-later',t,later(t)],['-'],...(music?[['Go to album','album','menu-album',t],['Go to artist','artist','menu-artist',t],['Go to song radio','refresh','menu-radio',t],...(t.isExplicit===true?[['Make a clean version','check','menu-clean',t]]:[])]:[]),['Song credits','info','menu-credits',t],[hidden(t)?'Unhide song':'Hide song','hide','menu-hide',t],['Download for offline playback','download','menu-download',t]];}
function bulkMenu(list){return [['Like all','heart','bulk-like'],['Add to playlist','addlist','bulk-playlist'],['Add to queue','queue','bulk-queue'],['Bookmark for later','bookmark','bulk-save'],['-'],['Clear selection','close','bulk-clear']];}
function itemMenu(x){const pinned=(prefs.sidePinned||[]).includes(x.key),fixed=x.key==='liked'||x.key==='saves';return [[pinned?'Unpin':'Pin to the top','pin','side-pin',x,pinned],...(fixed?[]:[[x.kind==='podcast'?'Unfollow podcast':x.kind==='artist'?'Remove from Your Library':'Delete playlist','close','side-remove',x]])];}
function renderOverlays(){
  const root=document.querySelector('#overlays');let html='';
  if(state.menu){const t=state.menu;html=`<div class="overlay" data-dismiss><section class="modal" role="dialog" aria-modal="true" aria-label="Song options"><div class="modal-head">${cover(t.albumArtUrl)}<div><strong>${h(t.title)}</strong><p>${h(t.artist)}</p></div>${button('close','Close','close')}</div>${menuItem('menu-save',saved(t)?'Remove from Liked Songs':'Add to Liked Songs','heart',saved(t))}${menuItem('menu-append','Add to playlist','addlist')}${menuItem('menu-queue','Add to queue','queue')}${menuItem('menu-later',later(t)?'Remove from Bookmarked':'Bookmark for later','bookmark',later(t))}${t.source!=='PODCAST'?menuItem('menu-album','Go to album','album'):''}${t.source!=='PODCAST'?menuItem('menu-artist','Go to artist','artist'):''}${t.source!=='PODCAST'?menuItem('menu-radio','Go to song radio','refresh'):''}${t.source!=='PODCAST'&&t.isExplicit===true?menuItem('menu-clean',state.cleaning===t.isrc?'Looking for a clean version…':'Make a clean version','check'):''}${menuItem('menu-credits','Song credits','info')}${menuItem('sleep','Sleep timer','timer')}${menuItem('menu-hide',hidden(t)?'Unhide song':'Hide song','hide')}${menuItem('menu-download','Download for offline playback','download')}</section></div>`;}
  else if(state.modal?.type==='queue')html=modal('Queue',queueView(),true);
  else if(state.modal?.type==='create')html=modal('Create playlist',`<h2>Create playlist</h2><form id="create-form"><input name="name" aria-label="Playlist name" placeholder="Name your playlist" maxlength="100" required><button class="primary">Create</button></form>`);
  else if(state.modal?.type==='append'){const n=state.modal.tracks.length;html=modal('Add to playlist',`<h2>Add ${n===1?h(state.modal.tracks[0].title):`${n} songs`} to a playlist</h2><form id="new-playlist-form" class="inline-form"><input name="name" aria-label="New playlist name" placeholder="New playlist name" maxlength="100" required><button class="primary">Create and add</button></form>${state.library.playlists.map(p=>`<button class="menu-item" data-action="append" data-id="${h(p.id)}">${cover(p.artwork||p.tracks[0]?.albumArtUrl,'','','playlist')}<span>${h(p.title)}<small class="muted"> · ${p.tracks.length} songs</small></span></button>`).join('')||'<p class="muted">No playlists yet. Name one above.</p>'}`);}
  else if(state.modal?.type==='credits'){const t=state.modal.track;html=modal('Song credits',`<h2>Song credits</h2><dl class="credits"><dt>Title</dt><dd>${h(t.title)}</dd><dt>Performed by</dt><dd>${contributors([t]).map(h).join('<br>')}</dd>${t.album?`<dt>Album</dt><dd>${h(t.album)}</dd>`:''}<dt>Length</dt><dd>${time(t.durationMs/1000)}</dd><dt>Source</dt><dd>${t.source==='PODCAST'?'Publisher podcast feed':'Spotify catalog metadata with matched public audio'}</dd>${t.isExplicit===true?'<dt>Content</dt><dd>Explicit</dd>':''}</dl>`);}
  else if(state.modal?.type==='sleep'){const s=state.sleep;html=modal('Sleep timer',`<h2>Sleep timer</h2>${s?`<p class="muted">${s.endOfTrack?'Stops at the end of this song.':`Stops in ${Math.max(1,Math.ceil((s.until-Date.now())/60000))} min.`}</p>`:''}${[5,15,30,45,60].map(m=>`<button class="menu-item" data-action="sleep-set" data-value="${m}">${icon('timer')}${m} minutes</button>`).join('')}<button class="menu-item" data-action="sleep-set" data-value="end">${icon('timer')}End of this song</button>${s?`<button class="menu-item" data-action="sleep-set" data-value="off">${icon('close')}Turn off timer</button>`:''}`);}
  else if(state.modal?.type==='customize'){const all=orderedHome(homeSections()).filter(s=>!s.fixed);const candidates=[['liked','Liked Songs'],['saves','Bookmarked for later'],...state.library.playlists.map(p=>[`playlist:${p.id}`,p.title]),...(prefs.smart||[]).map(s=>[`smart:${s.id}`,s.title])].filter(([id])=>!(prefs.homeExtra||[]).includes(id));html=modal('Customize Home',`<h2>Customize Home</h2><p class="muted">Your Home, your rules. Show, pin or remove rows.</p>${all.map(s=>`<div class="custom-row"><label><input type="checkbox" data-home-row="${h(s.id)}" ${(prefs.homeHidden||[]).includes(s.id)?'':'checked'}> ${h(s.title)}</label>${button('row-pin',(prefs.homePinned||[]).includes(s.id)?`Unpin ${s.title}`:`Pin ${s.title} to the top`,'pin',(prefs.homePinned||[]).includes(s.id)?'saved small':'small',`data-row="${h(s.id)}"`)}${s.added?button('row-remove',`Remove ${s.title} from Home`,'close','small',`data-row="${h(s.id)}"`):''}</div>`).join('')}<label class="custom-row switch"><input type="checkbox" id="dynamic-rows" ${prefs.dynamicRows?'checked':''}> Show recommendation rows</label>${candidates.length?`<form id="home-add-form" class="inline-form"><select name="row" aria-label="Row to add from your library">${candidates.map(([id,title])=>`<option value="${h(id)}">${h(title)}</option>`).join('')}</select><button class="secondary">Add row</button></form>`:''}`);}
  else if(state.modal?.type==='clean'){const m=state.modal;const swore=(m.checked||[]).filter(x=>x.verdict==='explicit').length,note=v=>v.verdict==='unverified'?' · Couldn\'t confirm it\'s clean':v.verdict==='no-vocals'?' · Sounds instrumental':'';html=modal('Choose a clean version',`<h2>No verified clean version</h2><p class="muted">${(m.checked||[]).length?`We listened to ${m.checked.length} ${m.checked.length===1?'upload':'uploads'} of ${h(cleanBase(m.song.title))}${swore?` and heard swearing in ${swore}`:''}, so none was added.`:`No clean version of ${h(cleanBase(m.song.title))} turned up on its own.`} Pick an upload below and Cadence will listen to it for swearing before adding it.</p>${m.checking?`<p class="notice">Listening to ${h(m.checking)} for swearing…</p>`:''}${m.choices.length?m.choices.map(v=>`<div class="clean-choice">${cover(`https://i.ytimg.com/vi/${v.videoId}/mqdefault.jpg`,'wide-thumb')}<span><strong>${h(v.title)}</strong><small class="muted">${h(v.channel||'YouTube')}${v.durationMs?` · ${time(v.durationMs/1000)}`:''}${note(v)}</small></span>${button('clean-preview',`Preview ${v.title}`,'play','small',`data-ref="${ref(v)}"`)}<button class="secondary" data-action="clean-pick" data-ref="${ref(v)}" ${m.checking?'disabled':''}>Check and use</button></div>`).join(''):'<p>Nothing came back from YouTube. Try again later.</p>'}`,true);}
  else if(state.modal?.type==='licenses')html=modal('Open source licenses',`<h2>Open source licenses</h2><pre class="license">${h(state.modal.text)}</pre>`,true);
  else if(state.full&&state.now)html=fullPlayer();
  if(state.lyricsPopup&&state.now&&!html)html=lyricsPopup(state,h);
  if(state.ctx)html+=contextMenu();
  root.innerHTML=html;if(state.modal?.type==='create'||state.modal?.type==='append')root.querySelector('form input')?.focus();
  placeContextMenu();applyLights();state.lastActive=-2;
  syncProgress();syncWords();
}

/* ---------- Navigation and loading ---------- */
async function visit(route,push=true){
  if(route.type==='discover')route={type:'home'};
  if(push)state.history.push({...state.route,scroll:document.querySelector('#main').scrollTop});
  state.route={...route};state.selected.clear();if(push)state.pageQuery='';if(push&&route.type!==state.history.at(-1)?.type)state.artistTab='overview';
  const ticket=++state.request;document.querySelector('#main').scrollTop=route.scroll||0;render();
  if(route.type==='home'){void loadDiscover();void loadNewEpisodes();}
  if(route.type==='import')void loadSpotify();
  if(route.data)return;let loader;
  if(route.type==='home')loader=()=>call('home');else if(route.type==='search'&&state.query.trim())loader=()=>call('search',state.query.trim());else if(route.type==='browse')loader=()=>call('browse',route.title);else if(route.type==='collection')loader=()=>call('collection',route.uri);else if(route.type==='album')loader=()=>call('album',route.track);else if(route.type==='show')loader=()=>call('episodes',route.show);else if(route.type==='radio')loader=()=>call('radio',route.seed);
  if(!loader)return;state.route.loading=true;render();
  try{const data=await loader();if(ticket!==state.request)return;state.route.data=data;if(route.type==='home')state.home=data;if(['search','collection','album','detail','radio'].includes(route.type)&&data.tracks?.length)void call('prefetch',data.tracks.slice(0,2)).catch(()=>{});}
  catch(e){if(ticket!==state.request)return;state.route.error=e.message;}
  finally{if(ticket===state.request){state.route.loading=false;render();}}
}
async function reloadLibrary(){state.library=await call('library');if(!state.home.mixes?.length){const music=[...new Map([...state.library.history.map(x=>x.track),...state.library.favorites].filter(t=>t.source!=='PODCAST').map(t=>[t.isrc,t])).values()];const artists=[...new Set(music.map(t=>t.artist))].slice(0,3);state.home={mixes:artists.map((artist,i)=>({id:`daily-mix-${i+1}`,title:`Daily Mix ${i+1}`,subtitle:artist+' and your recent favorites',tracks:music.filter(t=>t.artist===artist),artwork:music.find(t=>t.artist===artist)?.albumArtUrl})),rails:[],songs:music.slice(0,10)};}}
function refreshDetail(){const d=state.route.data;if(!d)return;if(d.liked)d.tracks=state.library.favorites;else if(d.saves)d.tracks=state.library.saves||[];else if(d.local){const p=state.library.playlists.find(x=>x.id===d.id);if(p)d.tracks=p.tracks;}else if(d.smart){const s=(prefs.smart||[]).find(x=>x.id===d.id);if(s)d.tracks=smartTracks(s);}}
async function setLibrary(promise){state.library=await promise;refreshDetail();}

/* ---------- Playback ---------- */
async function attachAudio(source){
  hls?.destroy();hls=null;
  if(source.kind==='hls'&&Hls.isSupported()){
    const instance=new Hls({loader:FetchLoader,enableWorker:false,backBufferLength:30,maxBufferLength:20,maxMaxBufferLength:60,lowLatencyMode:false});hls=instance;
    await new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(Error('The music stream timed out.')),25000);
      instance.on(Hls.Events.MANIFEST_PARSED,()=>{clearTimeout(timer);resolve();});
      instance.on(Hls.Events.ERROR,(_event,data)=>{if(data.fatal){console.warn('Audio playback error',data.details,data.response?.code||'');clearTimeout(timer);reject(Error(`Stream failed: ${data.details}`));if(state.now&&!state.opening)void recoverAudio();}});
      instance.attachMedia(audio);instance.loadSource(source.url);
    });
  }else{audio.src=source.url;audio.load();}
}
async function startTrack(tracks,index=0,from='',{preview=false}={}){
  if(!tracks?.length)return;const request=++state.playRequest;audio.pause();clearTimeout(previewTimer);
  state.preview=preview?tracks[index].isrc:null;
  state.queue=tracks.map(t=>t._from?t:{...t,_from:from||'Your music'});state.index=index;state.now=state.queue[index];state.words=false;state.expandedWords=false;
  state.subtitle=state.now.source==='PODCAST'?{cues:[],working:true}:null;
  if(state.now.source==='PODCAST')void call('subtitlesStart',state.now,state.library.positions[state.now.isrc]||0).then(data=>{if(state.now?.isrc===data.trackId){state.subtitle=data;updateWords();}}).catch(error);else{void call('subtitlesStop').catch(()=>{});loadLyrics();}
  if('mediaSession'in navigator)navigator.mediaSession.metadata=new MediaMetadata({title:state.now.title,artist:state.now.artist,album:state.now.album||''});
  const track=state.now;state.accent=null;void artColor(track.albumArtUrl,track.album||track.title).then(c=>{if(state.now?.isrc===track.isrc){state.accent=c;applyBarColor();}});
  state.opening=true;state.playing=false;recorded=false;retried=0;renderPlayer();renderOverlays();if(state.route.type==='home')render();
  try{const source=await call('resolve',state.now);if(request!==state.playRequest)return;await attachAudio(source);await audio.play();if(request!==state.playRequest)return;state.opening=false;renderPlayer();renderOverlays();}
  catch(e){if(request!==state.playRequest)return;if(e.name==='NotSupportedError'||audio.error){await recoverAudio();return;}state.opening=false;error(e);renderPlayer();renderOverlays();}
}
function duration(){return Number.isFinite(audio.duration)?audio.duration:(state.now?.durationMs||0)/1000;}
function syncProgress(){const total=duration(),position=audio.currentTime||0;document.querySelectorAll('[data-seek]').forEach(el=>{if(!seeking)el.value=total?Math.floor(position/total*1000):0;el.style.setProperty('--played',`${Number(el.value)/10}%`);});document.querySelectorAll('[data-time="current"]').forEach(el=>el.textContent=time(position));document.querySelectorAll('[data-time="total"]').forEach(el=>el.textContent=time(total));}
function advance(direction=1){
  if(!state.queue.length)return;
  for(let attempt=0;attempt<state.queue.length;attempt++){
    let index=state.index+direction*(attempt+1);
    if(state.shuffle&&state.queue.length>1)index=(state.index+1+Math.floor(Math.random()*(state.queue.length-1)))%state.queue.length;
    if(index<0)index=0;
    if(index>=state.queue.length){if(!state.repeat){if(direction>0&&prefs.autoplay!==false)void autoplay();return;}index=index%state.queue.length;}
    if(!hidden(state.queue[index])||attempt===state.queue.length-1){void startTrack(state.queue,index);return;}
  }
}
let autoplaying=false;
async function autoplay(){
  const last=state.queue.at(-1);if(autoplaying||!last||last.source==='PODCAST')return;autoplaying=true;
  try{
    const data=await call('radio',{artist:last.artist,title:last.title,isrc:last.isrc,albumArtUrl:last.albumArtUrl,exclude:state.queue.map(t=>t.isrc).slice(-300)});
    if(state.queue.at(-1)?.isrc!==last.isrc)return;
    const from=`Autoplay · similar to ${last.title}`,start=state.queue.length;
    state.queue.push(...data.tracks.filter(t=>!hidden(t)).map(t=>({...t,_from:from})));
    if(state.queue.length>start){toast(`Autoplay: songs similar to ${last.title}`);void startTrack(state.queue,start);}
  }catch(e){toast(`Queue finished. ${e.message}`);}
  finally{autoplaying=false;}
}
function loadLyrics(){if(!state.now||state.now.source==='PODCAST'||state.subtitle)return;const track=state.now;state.subtitle={cues:[],working:true};updateWords();void call('lyrics',track).then(data=>{if(state.now?.isrc===track.isrc){state.subtitle=data;updateWords();}}).catch(()=>{if(state.now?.isrc===track.isrc){state.subtitle={cues:[],working:false,error:'Could not reach the lyrics service. Check your connection and try again.'};updateWords();}});}
function setLyricScale(delta){prefs.lyricScale=Math.min(1.6,Math.max(.8,Math.round(((prefs.lyricScale||1)+delta)*10)/10));savePrefs();applyTheme();if(state.route.type==='profile')render();}
async function eachTrack(tracks,fn){for(const t of tracks)state.library=await fn(t);refreshDetail();}
function copyText(text){try{const area=document.createElement('textarea');area.value=text;area.setAttribute('readonly','');area.style.position='fixed';area.style.opacity='0';document.body.append(area);area.select();const ok=document.execCommand('copy');area.remove();if(ok)return true;}catch{}return false;}

/* ---------- Interaction helpers ---------- */
function selectRow(row,range){
  const t=refs.get(row.dataset.track);if(!t)return;
  if(range&&state.anchor){const all=[...document.querySelectorAll('#content [data-track]')].map(el=>refs.get(el.dataset.track));const a=all.findIndex(x=>x?.isrc===state.anchor),b=all.findIndex(x=>x?.isrc===t.isrc);if(a>=0&&b>=0)for(const x of all.slice(Math.min(a,b),Math.max(a,b)+1))state.selected.add(x.isrc);}
  else{if(state.selected.has(t.isrc))state.selected.delete(t.isrc);else state.selected.add(t.isrc);state.anchor=t.isrc;}
  render();
}
// A quick pop on every copy of the like or bookmark button for this song.
function pop(kind,t){if(!t?.isrc)return;requestAnimationFrame(()=>document.querySelectorAll(`[data-${kind}="${CSS.escape(t.isrc)}"]`).forEach(el=>{el.classList.remove('pop');void el.offsetWidth;el.classList.add('pop');}));}
// Show all turns a row into a wrapped grid. The height change is animated both ways.
function toggleRow(button){
  const id=button.dataset.row,section=button.closest('.home-row'),rail=section?.querySelector('.rail,.clips');if(!rail)return;
  const open=!state.expandedRows.has(id);if(open)state.expandedRows.add(id);else state.expandedRows.delete(id);
  const from=rail.getBoundingClientRect().height;rail.classList.toggle('expanded',open);const to=rail.getBoundingClientRect().height;
  button.textContent=open?'Show less':'Show all';button.setAttribute('aria-expanded',String(open));
  rail.animate([{height:`${from}px`,overflow:'hidden'},{height:`${to}px`,overflow:'hidden'}],{duration:340,easing:'cubic-bezier(.2,.8,.2,1)'});
  if(!open)section.scrollIntoView({block:'nearest',behavior:'smooth'});
}
function paintSwatches(){document.querySelectorAll('[data-hue]').forEach(el=>el.style.setProperty('--sw',el.dataset.hue));}
async function setAvatar(file){
  if(file.size>20e6){toast('That image is too large. Pick one under 20 MB.');return;}
  try{const bitmap=await createImageBitmap(file),size=256,canvas=document.createElement('canvas');canvas.width=canvas.height=size;const side=Math.min(bitmap.width,bitmap.height);canvas.getContext('2d').drawImage(bitmap,(bitmap.width-side)/2,(bitmap.height-side)/2,side,side,0,0,size,size);prefs.avatar=canvas.toDataURL('image/jpeg',.86);savePrefs();toast('Profile picture updated');render();}
  catch{toast('Could not read that image. Try a JPEG or PNG.');}
}
document.addEventListener('contextmenu',event=>{
  const row=event.target.closest('[data-track]'),key=event.target.closest('[data-key]'),head=event.target.closest('[data-row-head]'),rec=event.target.closest('[data-rec]');
  let items=null,title='';
  if(row){const t=refs.get(row.dataset.track);if(!t)return;const group=state.selected.has(t.isrc)&&state.selected.size>1;items=group?bulkMenu():songMenu(t);title=group?`${state.selected.size} songs selected`:t.title;}
  else if(key){const x=refs.get(key.dataset.ref);if(!x)return;items=itemMenu(x);title=x.title;}
  else if(rec){items=[['Not interested','hide','rec-hide',rec.dataset.rec]];title='Recommended';}
  else if(head){const id=head.dataset.rowHead,s=homeSections().find(x=>x.id===id);if(!s)return;const pinned=(prefs.homePinned||[]).includes(id);items=[[pinned?'Unpin row':'Pin row to the top','pin','row-pin',id,pinned],['Hide row','hide','row-hide',id],...(s.added?[['Remove from Home','close','row-remove',id]]:[])];title=s.title;}
  if(!items)return;event.preventDefault();state.ctx={x:event.clientX,y:event.clientY,title,items};renderOverlays();
});
// Press and hold, then drag, to reorder library items and Home rows.
let dragging=null;
document.addEventListener('dragstart',event=>{
  const key=event.target.closest?.('[data-key]'),head=event.target.closest?.('[data-row-head]');
  if(key)dragging={el:key,kind:'key'};else if(head)dragging={el:head.closest('.home-row'),kind:'row'};else return;
  event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',dragging.kind);dragging.el.classList.add('dragging');
});
document.addEventListener('dragover',event=>{
  if(!dragging)return;const over=dragging.kind==='key'?event.target.closest?.('[data-key]'):event.target.closest?.('.home-row');
  if(!over||over.parentElement!==dragging.el.parentElement)return;event.preventDefault();if(over===dragging.el)return;
  const r=over.getBoundingClientRect(),sideways=over.classList.contains('library-tile'),after=sideways?event.clientX>r.left+r.width/2:event.clientY>r.top+r.height/2;
  over.parentElement.insertBefore(dragging.el,after?over.nextSibling:over);
});
document.addEventListener('drop',event=>{if(dragging)event.preventDefault();});
document.addEventListener('dragend',()=>{
  if(!dragging)return;const {el,kind}=dragging;dragging=null;el.classList.remove('dragging');
  if(kind==='key'){const keys=[...el.parentElement.querySelectorAll('[data-key]')].map(x=>x.dataset.key),pinned=prefs.sidePinned||[];prefs.sidePinned=[...keys.filter(k=>pinned.includes(k)),...pinned.filter(k=>!keys.includes(k))];const rest=keys.filter(k=>!pinned.includes(k));prefs.sideOrder=[...rest,...(prefs.sideOrder||[]).filter(k=>!rest.includes(k))];}
  else{const fixed=new Set(homeSections().filter(s=>s.fixed).map(s=>s.id)),ids=[...el.parentElement.querySelectorAll('.home-row[data-row]')].map(x=>x.dataset.row).filter(id=>!fixed.has(id)),pinned=prefs.homePinned||[];prefs.homePinned=ids.filter(id=>pinned.includes(id));prefs.homeOrder=ids.filter(id=>!pinned.includes(id));}
  savePrefs();render();
});
// Start resolving a song's audio while the pointer rests on it, so a click plays sooner.
let hoverTimer;
document.addEventListener('mouseover',event=>{
  const el=event.target.closest?.('[data-prefetch]');if(!el||el.dataset.warm)return;clearTimeout(hoverTimer);
  hoverTimer=setTimeout(()=>{const t=refs.get(el.dataset.prefetch);if(!t||t.source==='PODCAST'||t.isrc===state.now?.isrc)return;el.dataset.warm='1';void call('prefetch',[t]).catch(()=>{});},200);
});
document.addEventListener('wheel',event=>{if(event.target.closest?.('.now-words'))state.wordsScrolledAt=Date.now();},{passive:true});
addEventListener('resize',()=>{clearTimeout(state.resizeTimer);state.resizeTimer=setTimeout(fillRecommendations,150);});

/* ---------- Events ---------- */
document.addEventListener('click',async event=>{
  if(state.ctx&&!event.target.closest('.ctx [data-action]')){if(!event.target.closest('.ctx')){state.ctx=null;renderOverlays();}return;}
  const songRow=event.target.closest('#content [data-track]');
  if(songRow&&(event.ctrlKey||event.metaKey||event.shiftKey)&&!event.target.closest('.icon')){event.preventDefault();selectRow(songRow,event.shiftKey);return;}
  const element=event.target.closest('[data-action]');
  if(!element){if(event.target.matches('[data-dismiss]')){state.menu=null;state.modal=null;state.full=false;renderOverlays();}return;}
  const action=element.dataset.action,item=refs.get(element.dataset.ref),value=element.dataset.value;
  if(element.dataset.ctx){state.ctx=null;if(item&&action.startsWith('menu-'))state.menu=item;if(!['menu-append','menu-credits'].includes(action))renderOverlays();}
  try{switch(action){
    case'nav':state.filter='All';await visit({type:element.dataset.page});break;
    case'back':if(state.history.length)await visit(state.history.pop(),false);break;
    case'clear':state.query='';await visit({type:'search'},false);break;
    case'filter':state.filter=value;render();break;
    case'home-filter':state.homeFilter=value;render();break;
    case'library-filter':state.libraryFilter=value;render();break;
    case'library-view':prefs.libraryView=value==='Grid'?'grid':'list';savePrefs();render();break;
    case'library-page':await visit({type:'library'});break;
    case'releases-view':prefs.releasesView=value==='Grid'?'grid':'list';savePrefs();render();break;
    case'theme':prefs.theme=value.toLowerCase();savePrefs();applyTheme();render();break;
    case'browse':await visit({type:'browse',title:element.dataset.category,tab:'search'});break;
    case'search-for':state.query=value;state.filter='All';await visit({type:'search'});break;
    case'card':await visit({type:'collection',uri:item.uri,title:item.title});break;
    case'local':await visit({type:'detail',data:{...item,local:true,artwork:item.artwork||item.tracks?.[0]?.albumArtUrl,subtitle:'Playlist'}});break;
    case'smart':{const s=(prefs.smart||[]).find(x=>x.id===item.id)||item;await visit({type:'detail',data:{id:s.id,title:s.title,smart:true,subtitle:`Liked songs by ${s.artist}`,description:'Updates automatically as you like songs.',tracks:smartTracks(s),artwork:smartTracks(s)[0]?.albumArtUrl}});break;}
    case'library-artist':await visit({type:'detail',data:{title:`${item.title} in your library`,subtitle:'Liked and played songs',smartDraft:item.title,artwork:item.artwork,tracks:item.tracks,local:false}});break;
    case'save-smart':{const artist=state.route.data.smartDraft,s={id:`${Date.now()}`,title:`${artist} Mix`,artist};prefs.smart=[...(prefs.smart||[]),s];savePrefs();toast(`Saved smart playlist "${s.title}"`);render();break;}
    case'liked':await visit({type:'detail',data:{title:'Liked Songs',subtitle:'Playlist',liked:true,tracks:state.library.favorites}});break;
    case'saves':await visit({type:'detail',data:{title:'Bookmarked for later',subtitle:'Songs and episodes to explore later',saves:true,tracks:state.library.saves||[]}});break;
    case'mix':await visit({type:'detail',data:{...item,madeForYou:true}});break;
    case'show':void markSeen(item);await visit({type:'show',show:item,title:item.title});break;
    case'album':if(item?.source!=='PODCAST')await visit({type:'album',track:item,title:item.album||'Album'});break;
    case'play':{state.modal=null;state.menu=null;const q=queues.get(element.dataset.queue);await startTrack(q.tracks,Number(element.dataset.index),q.from);break;}
    case'play-one':await startTrack([item],0,'Bookmarked for later');break;
    case'preview':{const q=queues.get(element.dataset.queue),t=q.tracks[Number(element.dataset.index)];if(state.preview===t.isrc&&state.now?.isrc===t.isrc){if(state.playing)audio.pause();else await audio.play();render();break;}await startTrack(q.tracks,Number(element.dataset.index),q.from,{preview:true});break;}
    case'play-collection':{if(collectionPlaying()){if(state.playing||state.opening)audio.pause();else await audio.play();syncCollectionButton();break;}const tracks=state.route.data.tracks;const choices=tracks.map((_,i)=>i).filter(i=>tracks[i].isrc!==state.now?.isrc&&!hidden(tracks[i]));const pool=choices.length?choices:tracks.map((_,i)=>i);await startTrack(tracks,state.shuffle?pool[Math.floor(Math.random()*pool.length)]:pool[0],state.route.data.title||state.route.title);break;}
    case'play-artist':{const d=state.route.data,needle=(d.title||'').toLowerCase();const yours=state.library.history.filter(x=>x.track.artist.toLowerCase().includes(needle)).sort((a,b)=>(b.plays||1)-(a.plays||1)).map(x=>x.track);const list=[...new Map([...yours,...(d.tracks||[])].map(t=>[t.isrc,t])).values()];await startTrack(list,0,d.title);break;}
    case'artist-tab':state.artistTab=value;render();break;
    case'toggle-release':{const uri=item.uri;if(state.openReleases.has(uri))state.openReleases.delete(uri);else{state.openReleases.add(uri);if(!state.releases.has(uri)){state.releases.set(uri,{loading:true});call('collection',uri).then(d=>state.releases.set(uri,d)).catch(e=>state.releases.set(uri,{error:e.message})).finally(()=>render());}}render();break;}
    case'episode':await startTrack(state.route.data.tracks,Number(element.dataset.index),state.route.title);break;
    case'favorite':await setLibrary(call('favorite',item));render();pop('like',item);break;
    case'favorite-now':{const t=state.now;await setLibrary(call('favorite',t));render();pop('like',t);break;}
    case'save-later':await setLibrary(call('saveLater',item));toast(later(item)?'Bookmarked for later':'Removed from Bookmarked');render();pop('mark',item);break;
    case'side-filter':state.sideFilter=value||'';renderSidebar();break;
    case'side-pin':{const p=prefs.sidePinned||[];prefs.sidePinned=p.includes(item.key)?p.filter(k=>k!==item.key):[...p,item.key];savePrefs();toast(prefs.sidePinned.includes(item.key)?`Pinned ${item.title}`:`Unpinned ${item.title}`);render();break;}
    case'side-remove':{
      if(item.kind==='podcast')state.library=await call('follow',item);
      else if(item.kind==='artist')prefs.sideRemoved=[...new Set([...(prefs.sideRemoved||[]),item.title.toLowerCase()])];
      else if(item.smart)prefs.smart=(prefs.smart||[]).filter(x=>x.id!==item.id);
      else if(item.id)state.library=await call('deletePlaylist',item.id);
      prefs.sidePinned=(prefs.sidePinned||[]).filter(k=>k!==item.key);savePrefs();toast(`Removed ${item.title}`);
      if(state.route.data?.id&&state.route.data.id===item.id)await visit({type:'library'},false);else render();break;}
    case'rec-hide':prefs.recHidden=[...new Set([...(prefs.recHidden||[]),item])];savePrefs();toast('Got it. We will not suggest that again.');render();break;
    case'row-expand':toggleRow(element);break;
    case'accent':prefs.accentHue=Number(value);savePrefs();applyTheme();render();break;
    case'avatar-remove':prefs.avatar='';savePrefs();render();break;
    case'add-to-playlist':state.modal={type:'append',tracks:[item]};renderOverlays();break;
    case'playlist-now':state.modal={type:'append',tracks:[state.now]};state.full=false;renderOverlays();break;
    case'menu':state.menu=item;renderOverlays();break;
    case'menu-now':state.menu=state.now;renderOverlays();break;
    case'menu-save':{const t=state.menu;await setLibrary(call('favorite',t));state.menu=null;render();pop('like',t);toast(saved(t)?'Added to Liked Songs':'Removed from Liked Songs');break;}
    case'menu-later':{const t=state.menu;await setLibrary(call('saveLater',t));state.menu=null;toast(later(t)?'Bookmarked for later':'Removed from Bookmarked');render();pop('mark',t);break;}
    case'menu-hide':case'unhide':{const t=action==='unhide'?item:state.menu;await setLibrary(call('hide',t));state.menu=null;toast(hidden(t)?'Hidden. It will be skipped during playback.':'Song unhidden');render();break;}
    case'menu-credits':state.modal={type:'credits',track:state.menu};state.menu=null;renderOverlays();break;
    case'menu-artist':{const t=state.menu;state.menu=null;state.full=false;state.query=primaryArtist(t);state.filter='Artists';await visit({type:'search'});break;}
    case'menu-radio':{const t=state.menu;state.menu=null;state.full=false;await visit({type:'radio',seed:{artist:t.artist,title:t.title,isrc:t.isrc,albumArtUrl:t.albumArtUrl},title:`${t.title} Radio`});break;}
    case'artist-radio':{const d=state.route.data;await visit({type:'radio',seed:{artist:d.title,uri:d.uri,albumArtUrl:d.artwork},title:`${d.title} Radio`});break;}
    case'autoplay':prefs.autoplay=value==='On';savePrefs();render();break;
    case'menu-clean':{const t=state.menu;if(state.cleaning)break;state.menu=null;state.cleaning=t.isrc;renderOverlays();toast('Finding a clean version and listening to it for swearing. This can take a minute.');
      watchClean(t);try{const found=await call('cleanVersion',t);if(found.track)await applyClean(t,found.track,true);else{state.modal={type:'clean',song:t,choices:found.choices||[],checked:found.checked||[]};renderOverlays();}}finally{state.cleaning=null;watchClean(null);}break;}
    case'clean-pick':{const m=state.modal;if(m.checking)break;m.checking=item.title;renderOverlays();let result;watchClean(m.song);try{result=await call('cleanCheck',m.song,item.videoId,item.durationMs);}finally{m.checking=null;watchClean(null);}
      if(result.verdict==='explicit'){item.verdict='explicit';m.choices=m.choices.filter(x=>x.videoId!==item.videoId);m.checked=[...(m.checked||[]),{videoId:item.videoId,verdict:'explicit'}];toast(`Heard swearing in that upload${result.heard?.length?` (${result.heard.length} ${result.heard.length===1?'word':'words'})`:''}, so it wasn't added.`);if(state.modal===m)renderOverlays();break;}
      if(result.verdict==='no-vocals'){item.verdict='no-vocals';toast('That upload sounds instrumental, so it wasn\'t added.');if(state.modal===m)renderOverlays();break;}
      if(state.modal===m){state.modal=null;renderOverlays();}await applyClean(m.song,cleanFromVideo(m.song,item),result.verdict==='clean');break;}
    case'clean-preview':{const song=state.modal.song;await startTrack([cleanFromVideo(song,item)],0,'Clean version preview',{preview:true});break;}
    case'new-episode':await markSeen(item.show);await startTrack([item.episode],0,item.show.title);render();break;
    case'spotify-sign-in':{const a=state.account;a.loading=true;a.error=null;render();let ok=false;try{ok=(await call('spotifySignIn')).signedIn;}finally{a.loading=false;}if(ok){toast('Signed in to Spotify');Object.assign(a,{signedIn:true,checked:true,playlists:[],mixes:[],name:null});await loadSpotify(true);}else render();break;}
    case'spotify-sign-out':await call('spotifySignOut');Object.assign(state.account,{signedIn:false,checked:true,name:null,playlists:[],mixes:[],error:null});state.account.picked.clear();toast('Signed out of Spotify');render();break;
    case'spotify-retry':await loadSpotify(true);break;
    case'spotify-pick':{const set=state.account.picked;if(set.has(value))set.delete(value);else set.add(value);render();break;}
    case'spotify-select-all':{const a=state.account,all=[...a.mixes,...a.playlists];if(all.every(x=>a.picked.has(x.uri)))a.picked.clear();else all.forEach(x=>a.picked.add(x.uri));render();break;}
    case'spotify-import':await importSpotify();break;
    case'menu-album':{const track=state.menu;state.menu=null;state.full=false;await visit({type:'album',track,title:track.album||'Album'});break;}
    case'menu-append':state.modal={type:'append',tracks:[state.menu]};state.menu=null;renderOverlays();break;
    case'append':{const tracks=state.modal.tracks,id=element.dataset.id;await eachTrack(tracks,t=>call('append',id,t));state.modal=null;toast(tracks.length===1?'Added to playlist':`Added ${tracks.length} songs to playlist`);state.selected.clear();render();break;}
    case'menu-queue':state.queue.splice(state.index+1+state.queue.slice(state.index+1).filter(t=>t._from==='Added by you').length,0,{...state.menu,_from:'Added by you'});state.menu=null;toast('Added to queue');renderOverlays();break;
    case'menu-download':{const track=state.menu;state.menu=null;renderOverlays();toast('Saving audio…');await call('download',track);toast('Saved for offline playback');break;}
    case'bulk-like':{const picked=selectedTracks().filter(t=>!saved(t));await eachTrack(picked,t=>call('favorite',t));toast(`Added ${picked.length} ${picked.length===1?'song':'songs'} to Liked Songs`);state.selected.clear();render();picked.forEach(t=>pop('like',t));break;}
    case'bulk-save':{const picked=selectedTracks().filter(t=>!later(t));await eachTrack(picked,t=>call('saveLater',t));toast(`Bookmarked ${picked.length} ${picked.length===1?'song':'songs'} for later`);state.selected.clear();render();picked.forEach(t=>pop('mark',t));break;}
    case'bulk-playlist':state.modal={type:'append',tracks:selectedTracks()};renderOverlays();break;
    case'bulk-queue':{const picked=selectedTracks();if(!state.now){await startTrack(picked,0,'Selected songs');}else{state.queue.push(...picked.map(t=>({...t,_from:'Selected songs'})));toast(`Added ${picked.length} songs to queue`);}state.selected.clear();render();break;}
    case'bulk-clear':state.selected.clear();render();break;
    case'toggle':if(state.playing)audio.pause();else await audio.play();break;
    case'previous':advance(-1);break;
    case'next':advance(1);break;
    case'seek-word':{const ms=Number(element.dataset.ms);if(Number.isFinite(ms)){audio.currentTime=Math.min(duration(),ms/1000);if(!state.playing)await audio.play();syncProgress();syncWords();}break;}
    case'flip-words':{const wasExpanded=state.expandedWords;state.words=!state.words;state.expandedWords=false;if(wasExpanded||element.closest('.full-options'))renderOverlays();else document.querySelector('.word-stage')?.classList.toggle('flipped',state.words);}if(state.words)loadLyrics();break;
    case'expand-words':state.expandedWords=true;renderOverlays();break;
    case'minimize-words':state.expandedWords=false;renderOverlays();break;
    case'lyrics-popup':state.lyricsPopup=!state.lyricsPopup;if(state.lyricsPopup)loadLyrics();renderPlayer();renderOverlays();break;
    case'close-lyrics-popup':state.lyricsPopup=false;renderPlayer();renderOverlays();break;
    case'expand-popup-words':state.lyricsPopup=false;state.full=true;renderPlayer();renderOverlays();break;
    case'lyric-smaller':setLyricScale(-.1);break;
    case'lyric-larger':setLyricScale(.1);break;
    case'shuffle':state.shuffle=!state.shuffle;renderPlayer();renderOverlays();break;
    case'repeat':state.repeat=!state.repeat;renderPlayer();renderOverlays();break;
    case'full':state.full=true;state.lyricsPopup=false;loadLyrics();renderPlayer();renderOverlays();break;
    case'queue':state.modal={type:'queue'};renderOverlays();break;
    case'queue-remove':state.queue.splice(Number(element.dataset.index),1);renderOverlays();break;
    case'queue-up':{const i=Number(element.dataset.index);if(i>state.index+1)[state.queue[i-1],state.queue[i]]=[state.queue[i],state.queue[i-1]];renderOverlays();break;}
    case'queue-jump':await startTrack(state.queue,Number(element.dataset.index));state.modal={type:'queue'};renderOverlays();break;
    case'queue-clear':state.queue.length=state.index+1;renderOverlays();break;
    case'queue-remove-group':{const g=queueGroups()[Number(element.dataset.group)];if(g)state.queue.splice(g.indices[0],g.indices.length);toast(`Removed ${g?.from||'songs'} from queue`);renderOverlays();break;}
    case'queue-shuffle-group':{const g=queueGroups()[Number(element.dataset.group)];if(g){const part=g.indices.map(i=>state.queue[i]);for(let i=part.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[part[i],part[j]]=[part[j],part[i]];}state.queue.splice(g.indices[0],part.length,...part);}renderOverlays();break;}
    case'sleep':state.menu=null;state.modal={type:'sleep'};renderOverlays();break;
    case'sleep-set':state.sleep=value==='off'?null:value==='end'?{endOfTrack:true}:{until:Date.now()+Number(value)*60000};state.modal=null;toast(value==='off'?'Sleep timer off':value==='end'?'Stopping at the end of this song':`Stopping in ${value} minutes`);renderPlayer();renderOverlays();break;
    case'close':state.menu=null;state.modal=null;state.full=false;renderOverlays();break;
    case'create':state.modal={type:'create'};renderOverlays();break;
    case'customize-home':state.modal={type:'customize'};renderOverlays();break;
    case'row-pin':{const id=element.dataset.row||item,p=prefs.homePinned||[];prefs.homePinned=p.includes(id)?p.filter(x=>x!==id):[...p,id];savePrefs();render();break;}
    case'row-up':moveRow(element.dataset.row,-1);render();break;
    case'row-down':moveRow(element.dataset.row,1);render();break;
    case'row-hide':{const id=element.dataset.row||item;prefs.homeHidden=[...new Set([...(prefs.homeHidden||[]),id])];prefs.homePinned=(prefs.homePinned||[]).filter(x=>x!==id);savePrefs();toast('Row hidden. Bring it back from Customize Home.');render();break;}
    case'row-remove':{const id=element.dataset.row||item;prefs.homeExtra=(prefs.homeExtra||[]).filter(x=>x!==id);prefs.homePinned=(prefs.homePinned||[]).filter(x=>x!==id);savePrefs();render();break;}
    case'toggle-section':{const k=element.dataset.section,c=prefs.collapsed||[];prefs.collapsed=c.includes(k)?c.filter(x=>x!==k):[...c,k];savePrefs();renderSidebar();break;}
    case'section-up':{const k=element.dataset.section,o=[...(prefs.sectionOrder||[])];const at=o.indexOf(k);if(at>0)[o[at-1],o[at]]=[o[at],o[at-1]];prefs.sectionOrder=o;savePrefs();renderSidebar();break;}
    case'discover-category':state.discover.category=value;state.expandedRows.delete('discover');render();await loadDiscover();break;
    case'copy-handle':toast(copyText(handle())?`Copied ${handle()}`:`Your username is ${handle()}`);break;
    case'rewind':audio.currentTime=Math.max(0,audio.currentTime-15);break;
    case'forward':audio.currentTime=Math.min(duration(),audio.currentTime+15);break;
    case'follow-current':state.library=await call('follow',state.route.data.show);render();break;
    case'import-collection':{if(state.importing)break;state.importing=true;toast('Importing all pages…');try{const result=await call('import',state.route.data.uri);state.library=result.library;toast(`Imported ${result.count} songs${result.skipped?` · ${result.skipped} unavailable skipped`:''}`);render();}finally{state.importing=false;}break;}
    case'refresh-home':state.home=await call('home',true);await visit({type:'home',data:state.home},false);break;
    case'retry':{const route={...state.route};delete route.data;delete route.error;await visit(route,false);break;}
    case'licenses':state.modal={type:'licenses',text:await call('licenses')};renderOverlays();break;
  }}catch(e){error(e);}
});
document.addEventListener('input',event=>{
  const el=event.target;
  if(el.id==='search-input'){state.query=el.value;clearTimeout(searchTimer);searchTimer=setTimeout(()=>visit({type:'search'},false),300);}
  else if(el.id==='page-find'){state.pageQuery=el.value;render();}
  else if(el.id==='library-find'){state.libraryQuery=el.value;render();}
  else if(el.matches('[data-seek]')){seeking=true;el.style.setProperty('--played',`${Number(el.value)/10}%`);}
  else if(el.id==='volume')audio.volume=Number(el.value);
  else if(el.id==='hue-range'){prefs.accentHue=Number(el.value);applyTheme();}
});
document.addEventListener('change',event=>{
  const el=event.target;
  if(el.matches('[data-seek]')){audio.currentTime=duration()*Number(el.value)/1000;seeking=false;syncProgress();}
  else if(el.id==='hue-range'){prefs.accentHue=Number(el.value);savePrefs();render();}
  else if(el.id==='avatar-input'&&el.files?.[0])void setAvatar(el.files[0]);
  else if(el.matches('[data-home-row]')){const id=el.dataset.homeRow;prefs.homeHidden=el.checked?(prefs.homeHidden||[]).filter(x=>x!==id):[...new Set([...(prefs.homeHidden||[]),id])];savePrefs();render();}
  else if(el.id==='dynamic-rows'){prefs.dynamicRows=el.checked;savePrefs();render();}
  else if(el.id==='library-sort'){state.librarySort=el.value;render();}
});
document.addEventListener('keydown',event=>{
  if(event.key==='Escape'&&state.ctx){state.ctx=null;renderOverlays();return;}
  if(event.key==='Escape'&&state.selected.size&&!state.menu&&!state.modal&&!state.full){state.selected.clear();render();return;}
  if(event.key==='Escape'){if(state.menu||state.modal||state.full){state.menu=null;state.modal=null;state.full=false;}else state.lyricsPopup=false;renderPlayer();renderOverlays();}
  else if((event.key==='Enter'||event.key===' ')&&event.target.matches('.now-name')){event.preventDefault();state.full=true;renderOverlays();}
  else if(event.code==='Space'&&!['INPUT','TEXTAREA','BUTTON','SELECT'].includes(document.activeElement.tagName)&&state.now){event.preventDefault();if(state.playing)audio.pause();else audio.play().catch(error);}
});
document.addEventListener('submit',async event=>{
  event.preventDefault();
  try{const form=event.target;
    if(form.id==='import-form'){if(state.importing)return;const link=new FormData(form).get('link');state.importing=true;form.querySelector('button').disabled=true;try{const result=await call('import',link);state.library=result.library;toast(`Imported ${result.count} songs`);await visit({type:'library'});}finally{state.importing=false;}}
    else if(form.id==='create-form'){state.library=await call('create',new FormData(form).get('name'));state.modal=null;toast('Playlist created');render();}
    else if(form.id==='new-playlist-form'){const name=String(new FormData(form).get('name')||'').trim();const tracks=state.modal.tracks;state.library=await call('create',name);const created=state.library.playlists[0];await eachTrack(tracks,t=>call('append',created.id,t));state.modal=null;state.selected.clear();toast(`Created "${created.title}" with ${tracks.length} ${tracks.length===1?'song':'songs'}`);render();}
    else if(form.id==='name-form'){prefs.displayName=String(new FormData(form).get('name')||'').trim().slice(0,40);savePrefs();toast('Name saved');render();}
    else if(form.id==='home-add-form'){const id=new FormData(form).get('row');prefs.homeExtra=[...new Set([...(prefs.homeExtra||[]),id])];savePrefs();render();}
  }catch(e){error(e);const button=event.target.querySelector('button');if(button)button.disabled=false;}
});
document.addEventListener('error',event=>{if(event.target.tagName==='IMG'){event.target.parentElement.classList.add('empty');event.target.remove();}},true);
audio.addEventListener('playing',async()=>{
  state.playing=true;state.opening=false;
  if(state.preview===state.now?.isrc&&!state.previewStarted){state.previewStarted=true;const total=duration();if(total>60&&audio.currentTime<5)audio.currentTime=total*.3;clearTimeout(previewTimer);previewTimer=setTimeout(()=>{if(state.preview===state.now?.isrc){audio.pause();toast('Preview ended. Play the full song or save it for later.');}},30000);}
  renderPlayer();renderOverlays();if(state.route.type==='home')render();
  if(!recorded&&state.now&&!state.preview){recorded=true;try{await call('record',state.now);await reloadLibrary();if(state.route.type==='home')await visit({type:'home'},false);else renderSidebar();}catch(e){error(e);}const upcoming=state.queue[state.index+1];if(upcoming)void call('prefetch',[upcoming]).catch(()=>{});}
});
audio.addEventListener('pause',()=>{state.playing=false;renderPlayer();renderOverlays();if(state.route.type==='home')render();});
audio.addEventListener('ended',()=>{if(state.sleep?.endOfTrack){state.sleep=null;toast('Sleep timer stopped playback');renderPlayer();return;}if(state.preview)return;advance();});
audio.addEventListener('loadedmetadata',()=>{state.previewStarted=false;if(state.now?.source==='PODCAST'){const position=state.library.positions[state.now.isrc];if(position>0)audio.currentTime=Math.min(position/1000,Math.max(0,duration()-5));}syncProgress();});
audio.addEventListener('timeupdate',()=>{syncProgress();if(state.now?.source==='PODCAST'&&Date.now()-lastPositionSave>5000){lastPositionSave=Date.now();const ms=Math.floor(audio.currentTime*1000);state.library.positions[state.now.isrc]=ms;void call('position',state.now.isrc,ms).catch(()=>{});}});
async function recoverAudio(){
  if(!state.now)return;
  const request=state.playRequest;
  if(recovery?.request===request)return recovery.promise;
  const track=state.now,position=Number.isFinite(audio.currentTime)?audio.currentTime:0;
  const promise=(async()=>{
    state.opening=true;renderPlayer();renderOverlays();
    while(retried<2&&request===state.playRequest){
      const codec=++retried===1?'opus':'hls';
      try{
        const source=await call('resolve',track,true,codec);
        if(request!==state.playRequest)return;
        await attachAudio(source);
        await audio.play();
        if(request!==state.playRequest)return;
        if(position>0&&Number.isFinite(audio.duration))audio.currentTime=Math.min(position,Math.max(0,audio.duration-1));
        state.opening=false;renderPlayer();renderOverlays();return;
      }catch{}
    }
    if(request!==state.playRequest)return;
    state.opening=false;state.playing=false;
    toast('The music provider rejected the available streams. Try another song or pull to refresh; your saved library is intact.');
    renderPlayer();renderOverlays();
  })();
  recovery={request,promise};try{return await promise;}finally{if(recovery?.request===request)recovery=null;}
}
audio.addEventListener('error',()=>{void recoverAudio();});
if('mediaSession'in navigator){for(const [name,handler] of Object.entries({play:()=>audio.play(),pause:()=>audio.pause(),nexttrack:()=>advance(),previoustrack:()=>advance(-1),seekbackward:()=>audio.currentTime=Math.max(0,audio.currentTime-15),seekforward:()=>audio.currentTime=Math.min(duration(),audio.currentTime+15),seekto:details=>{audio.currentTime=details.seekTime;}}))navigator.mediaSession.setActionHandler(name,handler);}
setInterval(()=>{if(state.route.type==='home')void visit({type:'home'},false);},30*60000);
setInterval(()=>{if(state.sleep?.until&&Date.now()>=state.sleep.until){state.sleep=null;audio.pause();toast('Sleep timer stopped playback');renderPlayer();}},1000);
matchMedia('(prefers-color-scheme: dark)').addEventListener('change',()=>{if(prefs.theme==='system'){applyTheme();applyBarColor();}});
applyTheme();
render();call('homeSaved').then(saved=>{if(saved?.mixes?.length)state.home=saved;}).catch(()=>{}).then(reloadLibrary).then(()=>visit({type:'home'},false)).catch(e=>{state.route={type:'home',error:e.message};render();});

function updateWords(){document.querySelectorAll('.words-body').forEach(element=>{const top=element.scrollTop;element.innerHTML=wordsBody(state,h);element.scrollTop=top;});syncWords();}
function syncWords(){
  if(!state.subtitle?.timed)return;const cues=state.subtitle.cues||[];let active=-1;for(let i=0;i<cues.length;i++)if(cues[i].startMs<=audio.currentTime*1000)active=i;
  document.querySelectorAll('[data-word-index]').forEach(element=>element.classList.toggle('active',Number(element.dataset.wordIndex)===active));
  // The full player scrolls the words along instead of highlighting them. Manual scrolling pauses this for a few seconds.
  const box=document.querySelector('.now-words');if(!box||active===state.lastActive)return;state.lastActive=active;
  if(Date.now()-(state.wordsScrolledAt||0)<4000)return;const line=box.querySelector(`[data-word-index="${Math.max(0,active)}"]`);
  if(line)box.scrollTo({top:Math.max(0,line.offsetTop-box.clientHeight*.3),behavior:'smooth'});
}
setInterval(async()=>{if(state.now?.source!=='PODCAST')return;try{await call('subtitlesPosition',Math.floor(audio.currentTime*1000));const data=await call('subtitlesState');if(data.trackId===state.now?.isrc){const changed=JSON.stringify(data)!==JSON.stringify(state.subtitle);state.subtitle=data;if(changed)updateWords();else syncWords();}}catch{}},1000);
audio.addEventListener('timeupdate',syncWords);
