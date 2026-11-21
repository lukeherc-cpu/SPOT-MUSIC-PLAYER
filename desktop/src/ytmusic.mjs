// YouTube Music song search. Labels upload both the explicit and the clean master of a release,
// and YouTube Music marks the explicit one with a badge, so a song result without that badge is
// the label's own clean version (the same recording Spotify serves as its clean track).
const SONGS='EgWKAQIIAWoMEA4QChADEAQQCRAF';
const text=x=>(x?.runs||[]).map(r=>r.text).join('');
const seconds=value=>{const m=String(value||'').match(/^(?:(\d+):)?(\d+):(\d{2})$/);return m?((Number(m[1]||0)*60+Number(m[2]))*60+Number(m[3])):0;};

function* renderers(node){
  if(!node||typeof node!=='object')return;
  if(node.musicResponsiveListItemRenderer)yield node.musicResponsiveListItemRenderer;
  for(const value of Object.values(node))if(value&&typeof value==='object')yield* renderers(value);
}

export function parseSongs(root){
  const out=[];
  for(const r of renderers(root)){
    const columns=(r.flexColumns||[]).map(c=>c.musicResponsiveListItemFlexColumnRenderer?.text);
    const title=text(columns[0]);
    const runs=columns[1]?.runs||[];
    const videoId=r.playlistItemData?.videoId||columns[0]?.runs?.[0]?.navigationEndpoint?.watchEndpoint?.videoId||r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId;
    if(!title||!/^[A-Za-z0-9_-]{11}$/.test(videoId||''))continue;
    const page=run=>run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType||'';
    const artists=runs.filter(x=>page(x)==='MUSIC_PAGE_TYPE_ARTIST').map(x=>x.text);
    const album=runs.find(x=>page(x)==='MUSIC_PAGE_TYPE_ALBUM')?.text||null;
    const duration=[...runs].reverse().find(x=>/^\d+:\d{2}(:\d{2})?$/.test(String(x.text).trim()))?.text;
    const fixed=text(r.fixedColumns?.[0]?.musicResponsiveListItemFixedColumnRenderer?.text);
    out.push({videoId,title,artist:artists.join(', ')||String(runs[0]?.text||''),album,durationMs:seconds(duration||fixed)*1000,
      explicit:JSON.stringify(r.badges||[]).includes('MUSIC_EXPLICIT_BADGE')});
  }
  return [...new Map(out.map(x=>[x.videoId,x])).values()];
}

export async function searchMusicSongs(query,fetcher=fetch){
  const response=await fetcher('https://music.youtube.com/youtubei/v1/search?prettyPrint=false',{method:'POST',signal:AbortSignal.timeout(20000),
    headers:{'Content-Type':'application/json','Origin':'https://music.youtube.com','Referer':'https://music.youtube.com/','User-Agent':'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'},
    body:JSON.stringify({context:{client:{clientName:'WEB_REMIX',clientVersion:'1.20241111.01.00',hl:'en',gl:'US'}},query:String(query).slice(0,200),params:SONGS})});
  if(!response.ok)throw Error(`YouTube Music search failed (${response.status}).`);
  const body=await response.text();if(body.length>6e6)throw Error('YouTube Music response too large.');
  return parseSongs(JSON.parse(body));
}
