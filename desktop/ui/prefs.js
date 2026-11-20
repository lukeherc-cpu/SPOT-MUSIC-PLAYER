// Viewer preferences kept on this computer only. Every read and write tolerates blocked storage.
const KEY='spot-music-preferences';
const defaults=()=>({
  theme:'dark',            // dark | light | system
  lyricScale:1,            // 0.8 – 1.6
  homeHidden:[],           // home row ids the listener hid
  homePinned:[],           // pinned home row ids, in pin order
  homeOrder:[],            // manual order of unpinned rows
  homeExtra:[],            // library rows added to home: liked | saves | playlist:<id> | smart:<id>
  dynamicRows:true,        // recommendation rows on or off
  sectionOrder:['playlists','saves','artists','podcasts'],
  collapsed:[],            // collapsed sidebar library sections
  libraryView:'list',      // list | grid
  releasesView:'list',     // list | grid on artist pages
  smart:[],                // smart playlists: {id,title,artist}
  displayName:'',
  autoplay:true,           // keep playing similar songs when the queue ends
});
function read(){try{const raw=localStorage.getItem(KEY);return raw?{...defaults(),...JSON.parse(raw)}:defaults();}catch{return defaults();}}
export const prefs=read();
export function savePrefs(){try{localStorage.setItem(KEY,JSON.stringify(prefs));}catch{}}
export function applyTheme(){
  const dark=prefs.theme==='system'?matchMedia('(prefers-color-scheme: dark)').matches:prefs.theme!=='light';
  document.documentElement.dataset.theme=dark?'dark':'light';
  document.documentElement.style.setProperty('--lyric-scale',String(prefs.lyricScale||1));
  return dark;
}
