/* SPDX-License-Identifier: GPL-3.0-only
 * Native JavaScript adaptation of Aran404/SpotAPI's public catalog protocol.
 * Mirrors core/core-network/SpotApiCatalog.kt. See ../THIRD_PARTY.md.
 */
import { createHmac, randomUUID } from 'node:crypto';
import { topKind } from './search.mjs';

const object = x => x && typeof x === 'object' && !Array.isArray(x) ? x : {};
const array = x => Array.isArray(x) ? x : [];
const text = x => typeof x === 'string' ? x : '';
const validId = x => /^[A-Za-z0-9]{22}$/.test(x);
const cdn = 'https://open.spotifycdn.com/cdn/build/web-player/';
const hosts = new Set(['open.spotify.com', 'open.spotifycdn.com', 'api-partner.spotify.com', 'clienttoken.spotify.com', 'api.spotify.com']);
export function resource(raw) {
  if (/^spotify:(playlist|album|artist):[A-Za-z0-9]{22}$/.test(raw)) return raw;
  let u; try { u = new URL(raw); } catch { throw Error('Paste a Spotify playlist or album link.'); }
  const parts = u.pathname.split('/').filter(x => x && !x.startsWith('intl-'));
  if (u.protocol !== 'https:' || u.hostname !== 'open.spotify.com' || u.username || u.password || parts.length !== 2 || !['playlist','album','artist'].includes(parts[0]) || !validId(parts[1])) throw Error('Paste a Spotify playlist or album link.');
  return `spotify:${parts.join(':')}`;
}
function unwrap(value) {
  let item = object(value);
  for (let i=0; i<5; i++) item = ['itemV2','item','data','track'].map(k => item[k]).find(x => x && typeof x === 'object' && !Array.isArray(x)) || item;
  return item;
}
const artistNames = item => array(item.artists?.items).map(a => a.profile?.name || a.name).filter(Boolean).join(', ');
function artwork(item) {
  return array(item?.coverArt?.sources || item?.visuals?.avatarImage?.sources || item?.images?.items?.[0]?.sources).sort((a,b)=>(Number(b.width)||0)-(Number(a.width)||0)).map(x => x.url).find(url => {
    try { const u = new URL(url); return u.protocol === 'https:' && (u.hostname.endsWith('.scdn.co') || u.hostname.endsWith('.spotifycdn.com')); } catch { return false; }
  }) || null;
}
export function decodeTrack(item, fallback = {}) {
  const t = unwrap(item), id = t.id || text(t.uri).replace('spotify:track:', '');
  if (!validId(id) || !t.name) return null;
  const album = t.albumOfTrack || fallback;
  return { isrc:`spotify:${id}`, title:t.name, artist:artistNames(t) || artistNames(album) || 'Unknown artist', album:album.name || null,
    durationMs:Number(t.duration?.totalMilliseconds || 0), albumArtUrl:artwork(album), source:'SPOTIFY', isExplicit:t.contentRating?.label? t.contentRating.label==='EXPLICIT':typeof t.explicit==='boolean'?t.explicit:null, albumUri: /^spotify:album:[A-Za-z0-9]{22}$/.test(album.uri) ? album.uri : validId(album.id) ? `spotify:album:${album.id}` : null };
}
const imageUrl = images => array(images).filter(x => { try { const u=new URL(x.url); return u.protocol==='https:' && (u.hostname.endsWith('.scdn.co') || u.hostname.endsWith('.spotifycdn.com')); } catch { return false; } }).sort((a,b)=>(Number(b.width)||0)-(Number(a.width)||0))[0]?.url || null;
const unique = (list, key = 'isrc') => [...new Map(list.map(x => [x[key], x])).values()];
function cards(root, section, type) {
  return unique(array((root[section] || root[`${section}V2`])?.items).map(unwrap).filter(x => text(x.uri).startsWith(`spotify:${type}:`) && (x.name || x.profile?.name)).map(x => ({ uri:x.uri, title:x.name || x.profile.name, subtitle:artistNames(x) || x.ownerV2?.data?.name || type, artwork:artwork(x), type })), 'uri');
}
export class Catalog {
  constructor() { this.account=null; this.session=null; this.hashes=new Map(); this.cookies=new Map(); this.bundles=[]; this.visited=new Set(); this.cache=new Map(); this.inflight=new Map(); this.tail=Promise.resolve(); }
  async request(url, body, headers={}, hops=0) {
    const u = new URL(url);
    if (u.protocol !== 'https:' || !hosts.has(u.hostname)) throw Error('Unexpected catalog host.');
    const jar=[...this.cookies].filter(([k])=>k!=='sp_dc');
    // The signed-in web session cookie goes only to open.spotify.com, where the access token is issued.
    if (u.hostname === 'open.spotify.com' && this.account) jar.push(['sp_dc',this.account]);
    const response = await fetch(url, { method:body ? 'POST' : 'GET', redirect:'manual', signal:AbortSignal.timeout(25000), headers:{
      'User-Agent':'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36', 'Accept':body||u.pathname==='/api/token'||u.hostname==='api.spotify.com'?'application/json':'text/html, */*', 'Accept-Language':'en',
      ...(u.hostname === 'open.spotify.com' && jar.length ? { Cookie:jar.map(([k,v]) => `${k}=${v}`).join('; ') } : {}), ...(body ? {'Content-Type':'application/json'} : {}), ...headers,
    }, body:body ? Buffer.from(JSON.stringify(body)) : undefined });
    if (response.status>=300 && response.status<400) {
      const next=response.headers.get('location'); let target=null; try { target=new URL(next,url); } catch {}
      if (!body && hops<3 && target && target.protocol==='https:' && hosts.has(target.hostname)) return this.request(target.href, undefined, headers, hops+1);
      const e=Error(`Spotify request failed (${response.status}).`); e.status=response.status; throw e;
    }
    if (!response.ok) { const e=Error(`Spotify request failed (${response.status}).`); e.status=response.status; throw e; }
    if (u.hostname === 'open.spotify.com') for (const cookie of response.headers.getSetCookie()) { const pair=cookie.split(';')[0], at=pair.indexOf('='); if (at>0) this.cookies.set(pair.slice(0,at),pair.slice(at+1)); }
    let size=0; const chunks=[]; const limit=u.hostname==='open.spotifycdn.com'?16e6:4e6;
    for await (const chunk of response.body) { size+=chunk.length; if(size>limit) throw Error('Catalog response too large.'); chunks.push(chunk); }
    return Buffer.concat(chunks).toString('utf8');
  }
  discover(source) { for (const m of source.matchAll(/["']([A-Za-z][A-Za-z0-9_]+)["']\s*,\s*["'](?:query|mutation)["']\s*,\s*["']([a-f0-9]{64})["']/g)) this.hashes.set(m[1],m[2]); }
  chunks(source) {
    const start=source.search(/\.u\s*=\s*(?:[\w$]+\s*=>|function\s*\()/);
    const maps=[...source.slice(Math.max(start,0)).matchAll(/\{\d+:"[^"]+"(?:,\d+:"[^"]+")*\}/g)].slice(0,2).map(m => new Map([...m[0].matchAll(/(\d+):"([^"]+)"/g)].map(x=>[x[1],x[2]])));
    if (maps.length<2) return [];
    maps.sort((a,b)=>[...b.values()].filter(x=>/^[a-f0-9]{8,}$/.test(x)).length-[...a.values()].filter(x=>/^[a-f0-9]{8,}$/.test(x)).length);
    return [...maps[1]].filter(([,name])=>/^[A-Za-z0-9._-]+$/.test(name)).flatMap(([id,name])=> maps[0].has(id)?[`${cdn}${name}.${maps[0].get(id)}.js`]:[]);
  }
  async authorize() {
    const account=this.account;
    const html=await this.request('https://open.spotify.com/');
    const encoded=html.match(/<script\b[^>]*\bid=["']appServerConfig["'][^>]*>([^<]+)<\/script>/)?.[1];
    if(!encoded) throw Error('Spotify catalog has changed. Update Cadence.');
    const config=JSON.parse(Buffer.from(encoded,'base64').toString());
    const links=[...html.matchAll(/["'](https:\/\/open\.spotifycdn\.com\/cdn\/build\/web-player\/[^"']+\.js)["']/g)].map(m=>m[1]);
    const main=await this.request(links.find(x=>x.includes('/web-player.')) || '');
    const candidates=[...main.matchAll(/secret\s*:\s*(["'])(.*?)\1\s*,?\s*version\s*:\s*(\d+)/gs)];
    if(!candidates.length) throw Error('Spotify session format changed. Update Cadence.');
    const secret=candidates.sort((a,b)=>Number(b[3])-Number(a[3]))[0];
    const key=Buffer.from([...secret[2]].map((c,i)=>(c.charCodeAt(0)^(i%33+9)).toString()).join(''));
    const counter=Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)));
    const digest=createHmac('sha1',key).update(counter).digest(), offset=digest.at(-1)&15;
    const totp=((digest.readUInt32BE(offset)&0x7fffffff)%1000000).toString().padStart(6,'0');
    this.hashes.clear(); this.visited.clear(); this.discover(main); this.bundles=[...new Set([...links,...this.chunks(main)])];
    if(!this.hashes.has('searchDesktop')) await this.hash('searchDesktop');
    const params=new URLSearchParams({reason:'init',productType:'web-player',totp,totpServer:totp,totpVer:secret[3]});
    const access=JSON.parse(await this.request('https://open.spotify.com/api/token?'+params));
    const token=JSON.parse(await this.request('https://clienttoken.spotify.com/v1/clienttoken',{client_data:{client_version:config.clientVersion,client_id:access.clientId,js_sdk_data:{device_brand:'unknown',device_model:'unknown',os:'windows',os_version:'NT 10.0',device_id:this.cookies.get('sp_t')||randomUUID(),device_type:'computer'}}}));
    if(!access.accessToken || !token.granted_token?.token) throw Error('Spotify session unavailable. Try again.');
    this.session={access:access.accessToken,client:token.granted_token.token,version:config.clientVersion,expires:access.accessTokenExpirationTimestampMs,account,anonymous:!account||access.isAnonymous===true};
  }
  async hash(operation) {
    if(this.hashes.has(operation)) return this.hashes.get(operation);
    const kind=operation.includes('Playlist')?'playlist':operation.includes('Album')?'album':operation.includes('Artist')?'artist':'search';
    for(const url of this.bundles.filter(x=>x.includes(`xpui-routes-${kind}`)||x.toLowerCase().includes(`${kind}-page`)).slice(0,10)) {
      if(!this.visited.has(url)) { this.visited.add(url); this.discover(await this.request(url)); }
      if(this.hashes.has(operation)) return this.hashes.get(operation);
    }
    if(kind==='search') {
      // Library, home and profile queries live in other route bundles; look through those once.
      const hint=operation==='libraryV3'?'librar':operation==='home'?'home':'';
      const rest=this.bundles.filter(x=>!this.visited.has(x)).sort((a,b)=>Number(Boolean(hint)&&b.toLowerCase().includes(hint))-Number(Boolean(hint)&&a.toLowerCase().includes(hint)));
      for(const url of rest.slice(0,40)) { this.visited.add(url); try { this.discover(await this.request(url)); } catch {} if(this.hashes.has(operation)) return this.hashes.get(operation); }
    }
    const fallback={searchDesktop:'db61238974d27839a136c9dc02bfdbe3fab7635f21cf85976ebff9a1ee281345',getAlbum:'0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f',queryArtistOverview:'eafb1620d783ece118e72fbfede2b0064483e029d3c1aafafa1c2de0e25a89e7'};
    if(fallback[operation]) return fallback[operation];
    throw Error('Spotify collection format changed. Update Cadence.');
  }
  execute(operation,variables) {
    const run=async()=> {
      for(let attempt=0;attempt<2;attempt++) {
        if(!this.session || this.session.expires<Date.now()+60000 || this.session.account!==this.account) await this.authorize();
        const s=this.session;
        try {
          const body=JSON.parse(await this.request('https://api-partner.spotify.com/pathfinder/v2/query',{operationName:operation,variables,extensions:{persistedQuery:{version:1,sha256Hash:await this.hash(operation)}}},{Authorization:`Bearer ${s.access}`,'client-token':s.client,'spotify-app-version':s.version,'app-platform':'WebPlayer',Origin:'https://open.spotify.com',Referer:'https://open.spotify.com/'}));
          if(body.errors?.length) throw Error('This Spotify collection is private or unavailable.');
          return body.data;
        } catch(e) { if(attempt===0 && e.status===401) this.session=null; else throw e; }
      }
    };
    const result=this.tail.then(run,run); this.tail=result.catch(()=>{}); return result;
  }
  async search(query) {
    const q=text(query).trim().slice(0,300); if(q.length<2) return {tracks:[],albums:[],artists:[],playlists:[],podcasts:[]};
    const cached=this.cache.get(q); if(cached && Date.now()-cached.at<600000) return cached.result;
    if(this.inflight.has(q)) return this.inflight.get(q);
    const promise=(async()=> {
      const data=await this.execute('searchDesktop',{searchTerm:q,offset:0,limit:25,numberOfTopResults:5,includeAudiobooks:false,includeArtistHasConcertsField:false,includePreReleases:false,includeAlbumPreReleases:false,includeAuthors:false,includeEpisodeContentRatingsV2:false,isPrefix:null,sectionFilters:['GENERIC']});
      const root=data.searchV2||data.search; if(!root) throw Error('Spotify returned an incomplete search.');
      const result={tracks:unique(array((root.tracksV2||root.tracks)?.items).map(x=>decodeTrack(x)).filter(Boolean)),albums:cards(root,'albums','album'),artists:cards(root,'artists','artist'),playlists:cards(root,'playlists','playlist'),podcasts:cards(root,'podcasts','show'),top:array((root.topResultsV2||root.topResults)?.itemsV2).map(x=>x?.item?.data||x?.itemV2?.data||x?.data).filter(Boolean).map(d=>({kind:topKind(d.__typename),title:d.name||d.profile?.name||'',uri:text(d.uri)})).filter(x=>x.kind&&x.title).slice(0,5)};
      if(this.cache.size>=100) this.cache.delete(this.cache.keys().next().value); this.cache.set(q,{at:Date.now(),result}); return result;
    })();
    this.inflight.set(q,promise); try{return await promise;}finally{this.inflight.delete(q);}
  }
  /** Signs the catalog in with the web player's session cookie, or out with null. */
  setAccount(cookie) {
    const value=typeof cookie==='string'?cookie.trim():'';
    this.account=value && value.length<4096 && !/[\s;]/.test(value) ? value : null;
    this.session=null; this.cache.clear();
  }
  // Spotify's Web API with the signed-in web session, only for the listener's own library.
  webApi(path, params={}) {
    if(!this.account) return Promise.reject(Object.assign(Error('Sign in to Spotify to see your library.'),{status:401}));
    const run=async()=> {
      for(let attempt=0;attempt<2;attempt++) {
        if(!this.session || this.session.expires<Date.now()+60000 || this.session.account!==this.account) await this.authorize();
        const s=this.session;
        if(s.anonymous) throw Object.assign(Error('Your Spotify sign-in has expired. Sign in again.'),{status:401});
        try { return JSON.parse(await this.request(`https://api.spotify.com${path}?${new URLSearchParams(params)}`,undefined,{Authorization:`Bearer ${s.access}`,'client-token':s.client,'app-platform':'WebPlayer',Origin:'https://open.spotify.com',Referer:'https://open.spotify.com/'})); }
        catch(e) { if(attempt===0 && e.status===401) this.session=null; else throw e; }
      }
    };
    const result=this.tail.then(run,run); this.tail=result.catch(()=>{}); return result;
  }
  async accountName() {
    try { const me=await this.webApi('/v1/me'); if(me.display_name) return text(me.display_name); } catch {}
    try { return text((await this.execute('profileAttributes',{})).me?.profile?.name)||null; } catch { return null; }
  }
  /** The listener's playlists: the Web API first, then GraphQL, which is how the web player reads Your Library. */
  async libraryPlaylists() {
    let failure=null,answered=false;
    for(const source of [()=>this.libraryFromWebApi(),()=>this.libraryFromGraph()]) {
      try { const found=await source(); answered=true; if(found.length) return found; } catch(e) { failure=e; }
    }
    if(answered) return [];
    throw failure;
  }
  async libraryFromWebApi() {
    const found=[]; let offset=0,total=0,items=[];
    do {
      const page=await this.webApi('/v1/me/playlists',{limit:'50',offset:String(offset)}); items=array(page.items); total=Number(page.total)||0;
      for(const item of items) if(text(item?.uri).startsWith('spotify:playlist:')&&item.name) found.push({uri:item.uri,title:text(item.name),subtitle:[`${Number((item.tracks||item.items)?.total)||0} songs`,text(item.owner?.display_name)].filter(Boolean).join(' · '),artwork:imageUrl(item.images),type:'playlist'});
      offset+=items.length;
    } while(items.length && offset<total && offset<2000);
    return unique(found,'uri');
  }
  async libraryFromGraph() {
    if(!this.account) throw Object.assign(Error('Sign in to Spotify to see your library.'),{status:401});
    const found=[]; let offset=0,total=0,items=[];
    do {
      const root=(await this.execute('libraryV3',{filters:['Playlists'],order:null,textFilter:'',features:['LIKED_SONGS','YOUR_EPISODES'],limit:50,offset,flatten:true,expandedFolders:[],folderUri:null,includeFoldersWhenFlattening:false})).me?.libraryV3||{};
      items=array(root.items); total=Number(root.totalCount)||0;
      for(const entry of items) { const item=unwrap(entry); if(text(item.uri).startsWith('spotify:playlist:')&&item.name) found.push({uri:item.uri,title:text(item.name),subtitle:text(item.ownerV2?.data?.name)||'Playlist',artwork:artwork(item),type:'playlist'}); }
      offset+=items.length;
    } while(items.length && offset<total && offset<2000);
    return unique(found,'uri');
  }
  /** Mixes made for this listener: Daily Mix, Discover Weekly, Release Radar, artist, genre and mood mixes. */
  async personalMixes() {
    const found=new Map();
    const keep=card=>{const id=card.uri.replace('spotify:playlist:','');const personal=id.startsWith('37i9dQZF1E')||(id.startsWith('37i9dQZEVX')&&/^(discover weekly|release radar)$/i.test(card.title));if(card.uri.startsWith('spotify:playlist:')&&personal&&!found.has(card.uri))found.set(card.uri,card);};
    const visit=(value,depth=0)=>{if(!value||depth>12)return;if(Array.isArray(value)){value.forEach(x=>visit(x,depth+1));return;}if(typeof value!=='object')return;
      if(text(value.uri).startsWith('spotify:playlist:')&&value.name)keep({uri:value.uri,title:text(value.name),subtitle:text(value.description).replace(/<[^>]*>/g,'')||'Made for you',artwork:(Array.isArray(value.images)?imageUrl(value.images):null)||artwork(value),type:'playlist'});
      Object.values(value).forEach(x=>visit(x,depth+1));};
    for(const path of ['/v1/views/made-for-x','/v1/browse/categories/0JQ5DAt0tbjZptfcdMSKl3/playlists']) { try { visit(await this.webApi(path,{limit:'50',content_limit:'50',locale:'en'})); } catch {} }
    // The signed-in home feed holds the Made For You shelf with every Daily Mix.
    if(!found.size) { try { visit(await this.execute('home',{timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC',sp_t:this.cookies.get('sp_t')||'',facet:'',sectionItemsLimit:30})); } catch {} }
    // Signed in, Spotify's search returns the listener's own mixes too.
    for(const query of ['Daily Mix','Discover Weekly','Release Radar','On Repeat','Repeat Rewind','Mix']) { try { (await this.search(query)).playlists.forEach(keep); } catch {} }
    try { (await this.libraryPlaylists()).forEach(keep); } catch {}
    return [...found.values()];
  }
  // Only the cover of a playlist, for cards that are known by id but have no artwork yet.
  async cover(raw) {
    const uri=resource(raw), hit=this.cache.get('cover:'+uri); if(hit) return hit.result;
    const data=await this.execute('fetchPlaylistMetadata',{uri,locale:'',offset:0,limit:1,enableWatchFeedEntrypoint:false});
    const root=Object.values(data).find(x=>x?.uri===uri)||Object.values(data).find(x=>x&&typeof x==='object');
    const result=artwork(root); if(result) this.cache.set('cover:'+uri,{at:Date.now(),result}); return result;
  }
  async collection(raw) {
    const uri=resource(raw), type=uri.split(':')[1];
    const variables={uri,locale:'',offset:0,limit:100,enableWatchFeedEntrypoint:false,includeEpisodeContentRatingsV2:true};
    const data=await this.execute(type==='playlist'?'fetchPlaylistMetadata':type==='album'?'getAlbum':'queryArtistOverview',variables);
    const root=Object.values(data).find(x=>x?.uri===uri)||Object.values(data).find(x=>x&&typeof x==='object');
    if(!root?.name && !root?.profile?.name) throw Error('This collection is private or unavailable.');
    const tracks=[]; let total=0,skipped=0;
    if(type==='artist') { tracks.push(...array(root.discography?.topTracks?.items||root.topTracks?.items).map(x=>decodeTrack(x)).filter(Boolean)); total=tracks.length; }
    else {
      let offset=0;
      do {
        const pageData=await this.execute(type==='playlist'?'fetchPlaylistContents':'queryAlbumTracks',{...variables,offset,limit:type==='album'?50:100});
        const page=type==='playlist'?pageData.playlistV2?.content:pageData.albumUnion?.tracksV2;
        if(!Array.isArray(page?.items)) throw Error('Spotify did not return the collection songs.');
        total=Math.max(Number(page.totalCount)||0,page.items.length+offset);
        if(!page.items.length && offset<total) throw Error('Incomplete collection. Try again.');
        for(const entry of page.items) { const track=decodeTrack(entry,type==='album'?root:{}); if(track) tracks.push(track); else skipped++; }
        offset=page.pagingInfo?.nextOffset>offset?page.pagingInfo.nextOffset:offset+page.items.length;
        if(offset>10000) throw Error('Import fewer than 10,000 songs at a time.');
      } while(offset<total);
    }
    const albums=[];
    function visit(x,depth=0) { if(!x || depth>10 || albums.length>=40)return; if(Array.isArray(x))x.forEach(y=>visit(y,depth+1));else if(typeof x==='object'){if(text(x.uri).startsWith('spotify:album:')&&x.name)albums.push({uri:x.uri,title:x.name,subtitle:'Album',artwork:artwork(x),type:'album'});else Object.values(x).forEach(y=>visit(y,depth+1));} }
    if(type==='artist') ['albums','singles','popularReleasesAlbums'].forEach(k=>visit(root.discography?.[k]));
    return {uri,title:root.name||root.profile.name,subtitle:type==='artist'?'Artist':artistNames(root)||root.ownerV2?.data?.name||'Spotify playlist',artwork:artwork(root),description:text(root.description||root.profile?.biography?.text).replace(/<[^>]*>/g,''),tracks:unique(tracks),total,skipped,albums:unique(albums,'uri'),...(type==='artist'?{artist:artistInfo(root)}:{})};
  }
}

const count=value=>{const n=Number(value);return Number.isFinite(n)&&n>=0?n:null;};
// Same artist-overview fields the Android app reads: stats, verified badge, latest release and related artists.
export function artistInfo(root){
  const latestItem=unwrap(root.discography?.latest);
  const latest=text(latestItem.uri).startsWith('spotify:album:')&&latestItem.name?{uri:latestItem.uri,title:latestItem.name,subtitle:[latestItem.type?String(latestItem.type).toLowerCase().replace(/^./,c=>c.toUpperCase()):'Release',latestItem.date?.year].filter(Boolean).join(' · '),artwork:artwork(latestItem),type:'album'}:null;
  const related=unique(array(root.relatedContent?.relatedArtists?.items).map(unwrap).filter(x=>text(x.uri).startsWith('spotify:artist:')&&x.profile?.name).map(x=>({uri:x.uri,title:x.profile.name,subtitle:'Artist',artwork:artwork(x),type:'artist'})),'uri').slice(0,20);
  return {monthlyListeners:count(root.stats?.monthlyListeners),followers:count(root.stats?.followers),verified:root.profile?.verified===true||root.profile?.verified?.verified===true,latest,related};
}
