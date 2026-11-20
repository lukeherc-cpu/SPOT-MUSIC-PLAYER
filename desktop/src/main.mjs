import {app,BrowserWindow,ipcMain,protocol,nativeTheme,net} from 'electron';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {Catalog,resource} from './catalog.mjs';
import {Store,cleanTrack} from './store.mjs';
import {Resolver} from './resolver.mjs';
import {browse,recommendations,albumForTrack,radio} from './discovery.mjs';
import {searchPodcasts,episodes} from './podcasts.mjs';
import {ArtworkCache} from './artwork.mjs';
import {SubtitleEngine,lyrics} from './subtitles.mjs';

const here=path.dirname(fileURLToPath(import.meta.url));
protocol.registerSchemesAsPrivileged([{scheme:'spot',privileges:{standard:true,secure:true,stream:true,supportFetchAPI:true}},{scheme:'spot-audio',privileges:{standard:true,secure:true,stream:true,supportFetchAPI:true}},{scheme:'spot-art',privileges:{standard:true,secure:true,corsEnabled:true}}]);
if(process.env.SPOT_TEST_DATA)app.setPath('userData',process.env.SPOT_TEST_DATA);
const single=app.requestSingleInstanceLock();if(!single&&!process.env.SPOT_TEST_DATA)app.quit();
let win,store,resolver,subtitles;const catalog=new Catalog();
const string=(value,max=300)=>{if(typeof value!=='string'||value.length>max)throw Error('Invalid request.');return value;};
const show=value=>{if(!value||typeof value!=='object')throw Error('Invalid podcast.');return{id:string(String(value.id||value.feedUrl),4000),title:string(value.title),author:string(value.author||''),feedUrl:string(value.feedUrl,4000),artwork:typeof value.artwork==='string'?value.artwork:null};};
let homeCache=null,homeAt=0,homeNonce=0,homeKey='';
async function home(){
  const library=store.snapshot(),key=library.history.map(x=>x.track.isrc).join(',')+'|'+library.favorites.map(x=>x.isrc).join(',');
  if(homeCache&&key===homeKey&&Date.now()-homeAt<30*60000)return homeCache;
  const value=await recommendations(catalog,library,Math.floor(Date.now()/1800000)+homeNonce);homeCache=value;homeAt=Date.now();homeKey=key;return value;
}
async function dispatch(command,value){
  switch(command){
    case'library':return store.snapshot();
    case'version':return app.getVersion();
    case'search':{const q=string(value);const [music,podcasts]=await Promise.allSettled([catalog.search(q),searchPodcasts(q)]);return{...(music.status==='fulfilled'?music.value:{tracks:[],albums:[],artists:[],playlists:[],notice:'Spotify search is unavailable. Try again.'}),shows:podcasts.status==='fulfilled'?podcasts.value:[],podcastNotice:podcasts.status==='rejected'?'Podcast directory is unavailable. Try again.':null};}
    case'home':if(value===true){homeAt=0;homeNonce++;}return home();
    case'licenses':{const dir=app.isPackaged?path.join(process.resourcesPath,'licenses'):path.resolve(here,'../../docs/licenses');const agpl=app.isPackaged?path.join(dir,'Spot-Music-AGPL-3.0.txt'):path.resolve(here,'../../LICENSE');return (await readFile(agpl,'utf8'))+'\n\nSPOTAPI\n'+(await readFile(path.join(dir,'SpotAPI-GPL-3.0.txt'),'utf8'));}
    case'browse':return value==='Podcasts'?{title:'Podcasts',subtitle:'Find your next favorite show',shows:await searchPodcasts('popular')}:browse(catalog,string(value));
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
    case'create':{const name=string(value,100).trim();if(!name)throw Error('Enter a playlist name.');return store.change(s=>s.playlists.unshift({id:randomUUID(),title:name,tracks:[],createdAt:Date.now()}));}
    case'append':{const id=string(value.id,100),track=cleanTrack(value.track);return store.change(s=>{const playlist=s.playlists.find(x=>x.id===id);if(!playlist)throw Error('Playlist not found.');if(!playlist.tracks.some(t=>t.isrc===track.isrc))playlist.tracks.push(track);});}
    default:throw Error('Unknown command.');
  }
}
function createWindow(){
  win=new BrowserWindow({width:1420,height:940,minWidth:900,minHeight:620,backgroundColor:'#121212',title:'Spot Music',icon:path.join(here,'../resources/icon.png'),autoHideMenuBar:true,webPreferences:{preload:path.join(here,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,webSecurity:true}});
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  win.webContents.on('will-navigate',(event,url)=>{if(!url.startsWith('spot://app/'))event.preventDefault();});
  win.webContents.session.setPermissionRequestHandler((_contents,_permission,callback)=>callback(false));
  win.loadURL('spot://app/index.html');
}
app.whenReady().then(async()=>{
  nativeTheme.themeSource='system';
  store=new Store(app.getPath('userData'));await store.load();
  const resources=app.isPackaged?process.resourcesPath:path.resolve(here,'../resources');
  resolver=new Resolver(path.join(app.getPath('userData'),'audio'),path.join(resources,'resolver',process.platform==='win32'?'yt-dlp.exe':'yt-dlp'),process.execPath);await resolver.load();
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
}).catch(error=>{console.error('Could not start Spot Music:',error.message);app.exit(1);});
app.on('second-instance',()=>{if(win){if(win.isMinimized())win.restore();win.focus();}});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});
app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)createWindow();});
app.on('before-quit',()=>subtitles?.stop());
