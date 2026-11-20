const {contextBridge,ipcRenderer}=require('electron');
const call=(command,args)=>ipcRenderer.invoke('spot:request',command,args);
contextBridge.exposeInMainWorld('spot',Object.freeze({
  library:()=>call('library'),search:q=>call('search',q),home:(force=false)=>call('home',force),browse:category=>call('browse',category),licenses:()=>call('licenses'),
  collection:uri=>call('collection',uri),album:track=>call('album',track),import:uri=>call('import',uri),
  favorite:track=>call('favorite',track),saveLater:track=>call('save-later',track),hide:track=>call('hide',track),follow:show=>call('follow',show),episodes:show=>call('episodes',show),
  resolve:(track,force=false,codec='auto')=>call('resolve',{track,force,codec}),prefetch:tracks=>call('prefetch',tracks),
  download:track=>call('download',track),record:track=>call('record',track),position:(id,value)=>call('position',{id,value}),
  create:name=>call('create',name),append:(id,track)=>call('append',{id,track}),
  version:()=>call('version'),
  subtitlesStart:(track,position=0)=>call('subtitles-start',{track,position}),subtitlesState:()=>call('subtitles-state'),subtitlesPosition:value=>call('subtitles-position',value),subtitlesStop:()=>call('subtitles-stop'),lyrics:track=>call('lyrics',track),
}));
