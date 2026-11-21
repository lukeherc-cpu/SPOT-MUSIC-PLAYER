const {contextBridge,ipcRenderer}=require('electron');
const call=(command,args)=>ipcRenderer.invoke('spot:request',command,args);
contextBridge.exposeInMainWorld('spot',Object.freeze({
  library:()=>call('library'),search:q=>call('search',q),home:(force=false)=>call('home',force),browse:category=>call('browse',category),licenses:()=>call('licenses'),
  collection:uri=>call('collection',uri),album:track=>call('album',track),radio:seed=>call('radio',seed),import:uri=>call('import',uri),
  favorite:track=>call('favorite',track),saveLater:track=>call('save-later',track),hide:track=>call('hide',track),follow:show=>call('follow',show),episodes:show=>call('episodes',show),
  resolve:(track,force=false,codec='auto')=>call('resolve',{track,force,codec}),prefetch:tracks=>call('prefetch',tracks),
  download:track=>call('download',track),record:track=>call('record',track),position:(id,value)=>call('position',{id,value}),
  create:name=>call('create',name),deletePlaylist:id=>call('delete-playlist',id),append:(id,track)=>call('append',{id,track}),
  version:()=>call('version'),homeSaved:()=>call('home-saved'),newEpisodes:()=>call('new-episodes'),markSeen:feedUrl=>call('mark-seen',feedUrl),
  spotifyAccount:()=>call('spotify-account'),spotifySignIn:()=>call('spotify-sign-in'),spotifySignOut:()=>call('spotify-sign-out'),spotifyLibrary:()=>call('spotify-library'),
  cleanVersion:track=>call('clean-version',track),cleanCheck:(track,videoId,durationMs)=>call('clean-check',{track,videoId,durationMs}),addVersion:(old,track)=>call('add-version',{old,track}),
  subtitlesStart:(track,position=0)=>call('subtitles-start',{track,position}),subtitlesState:()=>call('subtitles-state'),subtitlesPosition:value=>call('subtitles-position',value),subtitlesStop:()=>call('subtitles-stop'),lyrics:track=>call('lyrics',track),
}));
