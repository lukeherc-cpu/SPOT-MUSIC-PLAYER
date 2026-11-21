import {app,BrowserWindow,ipcMain,protocol,nativeTheme,net,session,safeStorage} from 'electron';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {randomUUID} from 'node:crypto';
import {readFile,writeFile,rename,rm} from 'node:fs/promises';
import {Catalog,resource} from './catalog.mjs';
import {Store,cleanTrack} from './store.mjs';
import {Resolver} from './resolver.mjs';
import {browse,recommendations,albumForTrack,radio,categories} from './discovery.mjs';
import {findClean} from './clean.mjs';
import {CleanVerifier} from './clean-verify.mjs';
import {searchMusicSongs} from './ytmusic.mjs';
import {searchPodcasts,episodes} from './podcasts.mjs';
import {ArtworkCache} from './artwork.mjs';
import {SubtitleEngine,lyrics} from './subtitles.mjs';
import {fold,topResult,withCleanVersion,withSavedVersions} from './search.mjs';

const here=path.dirname(fileURLToPath(import.meta.url));
protocol.registerSchemesAsPrivileged([{scheme:'spot',privileges:{standard:true,secure:true,stream:true,supportFetchAPI:true}},{scheme:'spot-audio',privileges:{standard:true,secure:true,stream:true,supportFetchAPI:true}},{scheme:'spot-art',privileges:{standard:true,secure:true,corsEnabled:true}}]);
// The app was renamed from Spot Music to Cadence. Keep the existing data folder
// so libraries, likes and caches carry over.
if(process.env.SPOT_TEST_DATA)app.setPath('userData',process.env.SPOT_TEST_DATA);else app.setPath('userData',path.join(app.getPath('appData'),'Spot Music'));
const single=app.requestSingleInstanceLock();if(!single&&!process.env.SPOT_TEST_DATA)app.quit();
let win,store,resolver,subtitles,verifier;const catalog=new Catalog();
const string=(value,max=300)=>{if(typeof value!=='string'||value.length>max)throw Error('Invalid request.');return value;};
const show=value=>{if(!value||typeof value!=='object')throw Error('Invalid podcast.');return{id:string(String(value.id||value.feedUrl),4000),title:string(value.title),author:string(value.author||''),feedUrl:string(value.feedUrl,4000),artwork:typeof value.artwork==='string'?value.artwork:null};};
let homeCache=null,homeAt=0,homeNonce=0,homeKey='';
const homeFile=()=>path.join(app.getPath('userData'),'home.json');
async function home(){
  const library=store.snapshot(),key=library.history.map(x=>x.track.isrc).join(',')+'|'+library.favorites.map(x=>x.isrc).join(',');
  if(homeCache&&key===homeKey&&Date.now()-homeAt<30*60000)return homeCache;
  const value=await recommendations(catalog,library,Math.floor(Date.now()/1800000)+homeNonce);homeCache=value;homeAt=Date.now();homeKey=key;
  // The last Home is saved so the next launch shows mixes and their artwork at once.
  void writeFile(homeFile()+'.tmp',JSON.stringify(value),{mode:0o600}).then(()=>rename(homeFile()+'.tmp',homeFile())).catch(()=>{});
  return value;
}
async function savedHome(){try{const value=JSON.parse(await readFile(homeFile(),'utf8'));return value&&Array.isArray(value.mixes)?value:null;}catch{return null;}}

/* Spotify account: the web player's session cookie, encrypted with the system keychain where available. */
const accountFile=()=>path.join(app.getPath('userData'),'spotify-account.bin');
async function loadAccount(){
  try{const data=await readFile(accountFile());const cookie=safeStorage.isEncryptionAvailable()?safeStorage.decryptString(data):null;if(cookie)catalog.setAccount(cookie);}catch{}
}
async function saveAccount(cookie){
  catalog.setAccount(cookie);
  if(!catalog.account)throw Error('Spotify did not finish signing in. Try again.');
  if(safeStorage.isEncryptionAvailable()){const file=accountFile();await writeFile(file+'.tmp',safeStorage.encryptString(catalog.account),{mode:0o600});await rename(file+'.tmp',file);}
}
let signingIn=null;
function signIn(){
  if(signingIn)return signingIn;
  signingIn=new Promise((resolve,reject)=>{
    // A separate, in-memory session: nothing from this window reaches the app's own pages.
    const partition=`spotify-login-${randomUUID()}`,login=session.fromPartition(partition,{cache:false});
    login.setPermissionRequestHandler((_c,_p,callback)=>callback(false));
    // Present as plain Chrome; some sign-in providers turn away embedded browsers.
    login.setUserAgent(app.userAgentFallback.split(' ').filter(x=>!/^(Electron|Cadence|spot-music-desktop)\//i.test(x)).join(' '));
    const child=new BrowserWindow({parent:win,modal:true,width:520,height:760,title:'Log in with Spotify',autoHideMenuBar:true,backgroundColor:'#ffffff',webPreferences:{partition,contextIsolation:true,nodeIntegration:false,sandbox:true}});
    child.webContents.setWindowOpenHandler(({url})=>{if(url.startsWith('https://'))void child.loadURL(url);return{action:'deny'};});
    child.webContents.on('will-navigate',(event,url)=>{if(!url.startsWith('https://'))event.preventDefault();});
    let done=false;
    const check=async()=>{
      if(done)return;
      const cookie=(await login.cookies.get({name:'sp_dc'})).find(x=>/(^|\.)spotify\.com$/.test(x.domain.replace(/^\./,'')))?.value;
      if(!cookie)return;
      done=true;
      try{await saveAccount(cookie);resolve(true);}catch(e){reject(e);}
      finally{await login.clearStorageData().catch(()=>{});if(!child.isDestroyed())child.close();}
    };
    child.webContents.on('did-navigate',()=>void check());child.webContents.on('did-navigate-in-page',()=>void check());child.webContents.on('did-finish-load',()=>void check());
    child.on('closed',()=>{if(!done){done=true;void login.clearStorageData().catch(()=>{});resolve(false);}});
    void child.loadURL('https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F');
  }).finally(()=>{signingIn=null;});
  return signingIn;
}
const settled=promise=>promise.then(value=>({value}),error=>({error:error.message||'Spotify did not respond.'}));
async function spotifyLibrary(){
  // Each part loads on its own, so one failing source never hides the others.
  const [name,playlists,mixes]=await Promise.all([catalog.accountName().catch(()=>null),settled(catalog.libraryPlaylists()),settled(catalog.personalMixes())]);
  const found=(playlists.value||[]).length+(mixes.value||[]).length,failure=playlists.error||mixes.error;
  const expired=/sign/i.test(failure||'')||/\(401\)/.test(failure||'');
  return{signedIn:true,name,playlists:playlists.value||[],mixes:mixes.value||[],error:found?null:expired?'Your Spotify sign-in has expired. Sign in again.':failure?`Couldn't load your Spotify library. ${failure}`:"Spotify didn't return any playlists or mixes for this account."};
}
let episodeCache=new Map();
async function newEpisodes(){
  const library=store.snapshot(),seen=library.seen||{},cutoff=Date.now()-14*86400000;
  const found=await Promise.all(library.shows.slice(0,15).map(async show=>{
    try{
      let hit=episodeCache.get(show.feedUrl);if(!hit||Date.now()-hit.at>30*60000){hit={at:Date.now(),tracks:await episodes(show)};episodeCache.set(show.feedUrl,hit);}
      const latest=hit.tracks.map(t=>({t,at:Date.parse(t.published)||0})).sort((a,b)=>b.at-a.at)[0];
      return latest&&latest.at>cutoff&&latest.at>(seen[show.feedUrl]||0)?{show,episode:latest.t,publishedAt:latest.at}:null;
    }catch{return null;}
  }));
  return found.filter(Boolean).sort((a,b)=>b.publishedAt-a.publishedAt);
}
async function dispatch(command,value){
  switch(command){
    case'library':return store.snapshot();
    case'version':return app.getVersion();
    case'search':{
      const q=string(value);const [music,podcasts]=await Promise.allSettled([catalog.search(q),searchPodcasts(q)]);
      const found=music.status==='fulfilled'?{...music.value}:{tracks:[],albums:[],artists:[],playlists:[],top:[],notice:'Spotify search is unavailable. Try again.'};
      let shows=podcasts.status==='fulfilled'?podcasts.value:[];
      // When Spotify's own top result is a podcast, make sure that show is in the playable podcast list.
      const spotifyTop=found.top?.[0];
      if(spotifyTop?.kind==='podcast'&&!shows.some(x=>fold(x.title)===fold(spotifyTop.title))){try{const exact=(await searchPodcasts(spotifyTop.title)).find(x=>fold(x.title)===fold(spotifyTop.title));if(exact)shows=[exact,...shows.filter(x=>x.feedUrl!==exact.feedUrl)];}catch{}}
      found.tracks=withSavedVersions(await withCleanVersion(catalog,found.tracks||[]),store.snapshot().versions);
      return{...found,shows,top:topResult(q,{...found,shows}),podcastNotice:podcasts.status==='rejected'?'Podcast directory is unavailable. Try again.':null};}
    case'home':if(value===true){homeAt=0;homeNonce++;}return home();
    case'home-saved':return homeCache||savedHome();
    case'new-episodes':return newEpisodes();
    case'mark-seen':return store.markSeen(string(value,4000));
    case'spotify-account':return{signedIn:Boolean(catalog.account)};
    case'spotify-sign-in':return{signedIn:await signIn()};
    case'spotify-sign-out':catalog.setAccount(null);await rm(accountFile(),{force:true});return{signedIn:false};
    case'spotify-library':if(!catalog.account)return{signedIn:false};return spotifyLibrary();
    case'clean-version':{const track=cleanTrack(value);if(track.source==='PODCAST')throw Error('Choose a song.');return findClean(catalog,q=>resolver.search(q,10),track,{musicSearch:q=>searchMusicSongs(q),verifier});}
    case'clean-check':{if(!value||typeof value!=='object')throw Error('Invalid request.');const track=cleanTrack(value.track);return verifier.check(track,string(value.videoId,20),Math.max(0,Number(value.durationMs)||0));}
    case'add-version':{if(!value||typeof value!=='object')throw Error('Invalid request.');return store.addVersion(string(value.old,160),value.track);}
    case'licenses':{const dir=app.isPackaged?path.join(process.resourcesPath,'licenses'):path.resolve(here,'../../docs/licenses');const agpl=app.isPackaged?path.join(dir,'Cadence-AGPL-3.0.txt'):path.resolve(here,'../../LICENSE');return (await readFile(agpl,'utf8'))+'\n\nSPOTAPI\n'+(await readFile(path.join(dir,'SpotAPI-GPL-3.0.txt'),'utf8'));}
    case'browse':{const name=string(value),topic=categories.find(x=>x[0]===name&&x[4]==='podcast');
      if(name==='Podcasts')return{title:'Podcasts',subtitle:'Find your next favorite show',shows:await searchPodcasts('popular')};
      if(topic)return{title:name,subtitle:`Shows about ${topic[2]}`,shows:await searchPodcasts(topic[2])};
      return browse(catalog,name);}
    case'collection':return catalog.collection(resource(string(value,500)));
    case'album':return albumForTrack(catalog,cleanTrack(value));
    case'radio':{if(!value||typeof value!=='object')throw Error('Invalid radio request.');const seed={artist:string(value.artist,250),title:typeof value.title==='string'?value.title.slice(0,300):'',isrc:typeof value.isrc==='string'?value.isrc.slice(0,160):'',uri:/^spotify:artist:[A-Za-z0-9]{22}$/.test(value.uri)?value.uri:'',albumArtUrl:typeof value.albumArtUrl==='string'&&value.albumArtUrl.startsWith('https://')?value.albumArtUrl.slice(0,4000):null};const exclude=Array.isArray(value.exclude)?value.exclude.slice(0,300).filter(x=>typeof x==='string'&&x.length<=160):[];return radio(catalog,seed,exclude);}
    case'import':{const uri=resource(string(value,500));if(!/^spotify:(playlist|album):/.test(uri))throw Error('Import a playlist or album.');const collection=await catalog.collection(uri);await store.importCollection(collection);return{library:store.snapshot(),count:collection.tracks.length,skipped:collection.skipped};}
    case'favorite':return store.favorite(value);
    case'save-later':return store.saveLater(value);
    case'hide':return store.hide(value);
    case'follow':return store.follow(show(value));
    case'episodes':{const podcast=show(value);return{title:podcast.title,subtitle:podcast.author,artwork:podcast.artwork,show:podcast,tracks:await episodes(podcast)};}
    case'resolve':{const codec=value.codec||'auto';if(!['auto','opus','aac','hls'].includes(codec))throw Error('Invalid audio format.');return resolver.resolve(cleanTrack(value.track),value.force===true,codec);}
    case'prefetch':{if(!Array.isArray(value)||value.length>2)throw Error('Invalid prefetch request.');for(const t of value)if(resolver.active.size<2)void resolver.resolve(cleanTrack(t)).catch(()=>{});return true;}
    case'download':return resolver.download(cleanTrack(value));
    case'subtitles-start':{const track=cleanTrack(value.track);if(track.source!=='PODCAST')throw Error('Choose a podcast.');return subtitles.start(track,Math.max(0,Math.min(86400000,Number(value.position)||0)));}
    case'subtitles-state':return subtitles.snapshot();
    case'subtitles-position':subtitles.position=Math.max(0,Math.min(86400000,Number(value)||0));return true;
    case'subtitles-stop':subtitles.stop();return true;
    case'lyrics':{let track=cleanTrack(value);if(track.isExplicit===null&&track.source==='SPOTIFY'){try{const match=(await catalog.search(`${track.artist} ${track.title}`)).tracks.find(x=>x.isrc===track.isrc);if(match)track={...track,isExplicit:match.isExplicit};}catch{}}return lyrics(track);}
    case'record':await store.record(value);homeAt=0;return true;
    case'position':{const id=string(value.id,160),position=Number(value.value);if(!Number.isFinite(position)||position<0||position>86400000)throw Error('Invalid playback position.');await store.change(s=>{s.positions[id]=position;const keys=Object.keys(s.positions);if(keys.length>1000)delete s.positions[keys[0]];});return true;}
    case'delete-playlist':{const id=string(value,100);return store.change(s=>{s.playlists=s.playlists.filter(x=>x.id!==id);});}
    case'create':{const name=string(value,100).trim();if(!name)throw Error('Enter a playlist name.');return store.change(s=>s.playlists.unshift({id:randomUUID(),title:name,tracks:[],createdAt:Date.now()}));}
    case'append':{const id=string(value.id,100),track=cleanTrack(value.track);return store.change(s=>{const playlist=s.playlists.find(x=>x.id===id);if(!playlist)throw Error('Playlist not found.');if(!playlist.tracks.some(t=>t.isrc===track.isrc))playlist.tracks.push(track);});}
    default:throw Error('Unknown command.');
  }
}
function createWindow(){
  win=new BrowserWindow({width:1420,height:940,minWidth:900,minHeight:620,backgroundColor:'#121212',title:'Cadence',icon:path.join(here,'../resources/icon.png'),autoHideMenuBar:true,webPreferences:{preload:path.join(here,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,webSecurity:true}});
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  win.webContents.on('will-navigate',(event,url)=>{if(!url.startsWith('spot://app/'))event.preventDefault();});
  win.webContents.session.setPermissionRequestHandler((_contents,_permission,callback)=>callback(false));
  win.loadURL('spot://app/index.html');
}
app.whenReady().then(async()=>{
  nativeTheme.themeSource='system';
  store=new Store(app.getPath('userData'));await store.load();await loadAccount();
  const resources=app.isPackaged?process.resourcesPath:path.resolve(here,'../resources');
  resolver=new Resolver(path.join(app.getPath('userData'),'audio'),path.join(resources,'resolver',process.platform==='win32'?'yt-dlp.exe':'yt-dlp'),process.execPath);await resolver.load();
  verifier=new CleanVerifier({ytdlp:resolver.binary,runtime:process.execPath,speech:path.join(resources,'speech'),lyricsFor:track=>lyrics(track)});
  subtitles=new SubtitleEngine(path.join(app.getPath('userData'),'subtitles'),path.join(resources,'speech'),async track=>{
    const saved=resolver.cache.get(track.isrc);return saved?.file?path.join(resolver.directory,saved.file):track.directAudioUrl;
  });
  protocol.handle('spot',async request=>{
    const u=new URL(request.url);
    if(u.hostname==='app'&&/^\/audio\/[a-f0-9-]{36}$/.test(u.pathname)){try{return await resolver.response(request);}catch{return new Response('Audio unavailable',{status:502});}}
    if(u.hostname==='app'&&u.pathname==='/hls.mjs')return net.fetch(pathToFileURL(path.join(here,'../node_modules/hls.js/dist/hls.light.mjs')).href);
    const allowed=new Set(['/index.html','/app.js','/words.js','/prefs.js','/color.js','/style.css']);
    if(u.hostname!=='app'||!allowed.has(u.pathname))return new Response(null,{status:404});
    return net.fetch(pathToFileURL(path.join(here,'../ui',u.pathname.slice(1))).href);
  });
  protocol.handle('spot-audio',async request=>{try{return await resolver.response(request);}catch{return new Response('Audio unavailable',{status:502});}});
  const artwork=new ArtworkCache();
  protocol.handle('spot-art',async request=>{
    try{const url=new URL(request.url).searchParams.get('url');if(!url||url.length>4000)throw Error('Invalid artwork.');const image=await artwork.get(url);return new Response(image.body,{headers:{'Content-Type':image.mime,'Cache-Control':'max-age=3600','Access-Control-Allow-Origin':'spot://app'}});}catch{return new Response(null,{status:404});}
  });
  ipcMain.handle('spot:request',async(event,command,value)=>{
    if(event.sender!==win?.webContents||!event.senderFrame?.url.startsWith('spot://app/'))throw Error('Untrusted caller.');
    try{return{ok:true,value:await dispatch(command,value)};}catch(e){return{ok:false,error:e.message||'Something went wrong. Try again.'};}
  });
  createWindow();
}).catch(error=>{console.error('Could not start Cadence:',error.message);app.exit(1);});
app.on('second-instance',()=>{if(win){if(win.isMinimized())win.restore();win.focus();}});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});
app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)createWindow();});
app.on('before-quit',()=>subtitles?.stop());
