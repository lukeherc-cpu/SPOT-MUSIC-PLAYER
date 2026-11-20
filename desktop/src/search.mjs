// Search ranking helpers. Kept free of network code so they can be tested directly.
export const fold=s=>String(s||'').toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g,'').replace(/&/g,'and').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
export const plainTitle=title=>String(title).replace(/\s*[(\[][^)\]]*(?:clean|explicit|official|lyrics?|remaster(?:ed)?|feat\.?|ft\.?|with )[^)\]]*[)\]]/gi,'').replace(/\s+-\s+(?:clean|remaster(?:ed)?|single version|radio edit).*$/i,'').trim();
const bare=s=>fold(s).replace(/^the /,'');
const primary=artist=>String(artist||'').split(/,\s*/)[0];
const KINDS={Podcast:'podcast',Artist:'artist',Album:'album',Playlist:'playlist',Track:'song'};
export const topKind=type=>KINDS[type]||null;

function match(query,title){const q=bare(query),t=bare(title);if(!q||!t)return 0;if(t===q)return 100;if(t.startsWith(q+' ')||q.startsWith(t+' '))return 70;if(t.includes(q))return 40;return 0;}

// Picks the single best result across every kind, the way a person would read the query.
// "the journal" should land on the podcast called The Journal, not one of many songs with that title.
export function topResult(query,result){
  const spotifyTop=(result.top||[])[0]?.kind;
  const sameTitleSongs=(result.tracks||[]).filter(t=>bare(plainTitle(t.title))===bare(query)).length;
  const candidates=[
    ...(result.shows||[]).slice(0,5).map((x,i)=>({kind:'podcast',item:x,score:match(query,x.title)+12-i})),
    ...(result.artists||[]).slice(0,5).map((x,i)=>({kind:'artist',item:x,score:match(query,x.title)+10-i})),
    ...(result.albums||[]).slice(0,5).map((x,i)=>({kind:'album',item:x,score:match(query,x.title)+4-i})),
    ...(result.playlists||[]).slice(0,5).map((x,i)=>({kind:'playlist',item:x,score:match(query,x.title)+2-i})),
    // A title shared by several songs is generic, so a song is a weaker answer than a show or artist with that exact name.
    ...(result.tracks||[]).slice(0,5).map((x,i)=>({kind:'song',item:x,score:match(query,plainTitle(x.title))+8-i-(sameTitleSongs>=3?20:0)})),
  ];
  for(const c of candidates)if(spotifyTop&&c.kind===spotifyTop&&c.score>=40)c.score+=25;
  const best=candidates.filter(c=>c.score>=40).sort((a,b)=>b.score-a.score)[0];
  if(best)return best;
  const firstSong=(result.tracks||[])[0];
  return firstSong?{kind:'song',item:firstSong,score:0}:null;
}

const versionKey=t=>`${fold(plainTitle(t.title))}|${fold(primary(t.artist))}`;
// Puts the explicit and clean versions of the same song next to each other, explicit first.
export function pairVersions(tracks){
  const groups=new Map();for(const t of tracks){const k=versionKey(t);if(!groups.has(k))groups.set(k,[]);groups.get(k).push(t);}
  const out=[],done=new Set();
  for(const t of tracks){const k=versionKey(t);if(done.has(k))continue;done.add(k);const g=groups.get(k);const e=g.filter(x=>x.isExplicit===true),c=g.filter(x=>x.isExplicit!==true);out.push(...(e.length&&c.length?[e[0],c[0],...e.slice(1),...c.slice(1)]:g));}
  return out;
}
// If the first song is explicit and no clean version came back, look for one so both show up first.
export async function withCleanVersion(catalog,tracks){
  const first=tracks[0];if(!first||first.isExplicit!==true)return pairVersions(tracks);
  const key=versionKey(first);if(tracks.some(t=>t!==first&&t.isExplicit!==true&&versionKey(t)===key))return pairVersions(tracks);
  try{
    const more=await catalog.search(`${plainTitle(first.title)} ${primary(first.artist)}`);
    const clean=(more.tracks||[]).find(t=>t.isExplicit===false&&versionKey(t)===key&&t.isrc!==first.isrc);
    if(clean)return pairVersions([first,clean,...tracks.slice(1).filter(t=>t.isrc!==clean.isrc)]);
  }catch{}
  return pairVersions(tracks);
}
