// Viewer preferences kept on this computer only. Every read and write tolerates blocked storage.
const KEY='spot-music-preferences';
const defaults=()=>({
  theme:'dark',            // dark | light | system
  lyricScale:1,            // 0.8 – 1.6
  homeHidden:[],           // home row ids the listener hid
  homePinned:[],           // pinned home row ids, in pin order
  homeOrder:[],            // manual order of unpinned rows
  homeExtra:[],            // library rows added to home: liked | saves | playlist:<id> | smart:<id>
  accentHue:268,           // accent colour hue; purple by default
  avatar:'',               // profile picture as a small JPEG data URL
  sidePinned:[],           // pinned sidebar item keys, in pin order
  sideOrder:[],            // manual drag order of sidebar item keys
  sideRemoved:[],          // artists removed from the sidebar
  recHidden:[],            // library recommendations marked not interested
  dynamicRows:true,        // recommendation rows on or off
  sectionOrder:['playlists','saves','artists','podcasts'],
  collapsed:[],            // collapsed sidebar library sections
  libraryView:'grid',      // grid | list
  releasesView:'list',     // list | grid on artist pages
  smart:[],                // smart playlists: {id,title,artist}
  displayName:'',
  autoplay:true,           // keep playing similar songs when the queue ends
});
// Version 2 (Cadence) makes the full library a grid by default, once.
function migrate(p){if((p.v||1)<2){p.libraryView='grid';p.v=2;}return p;}
function read(){try{const raw=localStorage.getItem(KEY);return migrate(raw?{...defaults(),...JSON.parse(raw)}:{...defaults(),v:2});}catch{return{...defaults(),v:2};}}
export const prefs=read();
export function savePrefs(){try{localStorage.setItem(KEY,JSON.stringify(prefs));}catch{}}
export function applyTheme(){
  const dark=prefs.theme==='system'?matchMedia('(prefers-color-scheme: dark)').matches:prefs.theme!=='light';
  document.documentElement.dataset.theme=dark?'dark':'light';
  document.documentElement.style.setProperty('--lyric-scale',String(prefs.lyricScale||1));
  const hue=Number.isFinite(Number(prefs.accentHue))?((Number(prefs.accentHue)%360)+360)%360:268;
  document.documentElement.style.setProperty('--hue',String(hue));
  return dark;
}
