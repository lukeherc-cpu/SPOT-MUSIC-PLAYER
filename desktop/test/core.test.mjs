import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,writeFile,stat} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {Catalog,decodeTrack,resource,artistInfo} from '../src/catalog.mjs';
import {parseFeed} from '../src/podcasts.mjs';
import {publicAddress} from '../src/public-network.mjs';
import {Store} from '../src/store.mjs';
import {Resolver,rewriteManifest} from '../src/resolver.mjs';
import {recommendations,browse,albumForTrack,US,seedArtists,radio} from '../src/discovery.mjs';
import {parseTranscript,cleanVersion,cleanLyricText,decoderInput} from '../src/subtitles.mjs';
import {Readable} from 'node:stream';
import {get} from 'node:http';

test('speech transport preserves byte ranges and hides publisher URLs behind an expiring local route',async()=>{
  const controller=new AbortController(),calls=[];
  const input=await decoderInput('https://publisher.example/audio.mp3',controller.signal,async(url,options)=>{
    calls.push({url,range:options.range});const stream=Readable.from(Buffer.from('2345'));stream.statusCode=206;stream.headers={'content-type':'audio/mpeg','content-length':'4','content-range':'bytes 2-5/10','accept-ranges':'bytes'};return stream;
  });
  const request=(url,headers={})=>new Promise((resolve,reject)=>{get(url,{headers},response=>{const chunks=[];response.on('data',chunk=>chunks.push(chunk));response.on('end',()=>resolve({status:response.statusCode,headers:response.headers,text:Buffer.concat(chunks).toString()}));response.on('error',reject);}).on('error',reject);});
  try{
    assert.equal(new URL(input.url).hostname,'127.0.0.1');assert.equal(input.url.includes('publisher.example'),false);
    const response=await request(input.url,{Range:'bytes=2-5'});assert.equal(response.status,206);assert.equal(response.text,'2345');assert.equal(response.headers['content-range'],'bytes 2-5/10');assert.deepEqual(calls,[{url:'https://publisher.example/audio.mp3',range:'bytes=2-5'}]);
    assert.equal((await request(new URL('/wrong-token',input.url))).status,404);
    assert.equal((await request(input.url,{Range:'bytes=0-1,3-4'})).status,416);assert.equal(calls.length,1);
  }finally{await input.close();}
  await assert.rejects(()=>request(input.url));
});

test('publisher subtitles keep their real timestamps and clean lyrics mask explicit words',()=>{
  const parsed=parseTranscript('WEBVTT\n\n00:00:01.200 --> 00:00:03.500\nHello world\n');
  assert.deepEqual(parsed.cues,[{startMs:1200,endMs:3500,text:'Hello world'}]);
  assert.equal(cleanVersion({title:'Song (Clean)'}),true);
  assert.equal(cleanVersion({title:'Song',isExplicit:false}),true);
  assert.equal(cleanLyricText('This shit is fucking loud'),'This ••• is ••• loud');
  assert.equal(cleanLyricText('A class of bass musicians'),'A class of bass musicians');
});

const albumUri='spotify:album:4m2880jivSbbyEGAKfITCa';
const song=(id='one',artist='Artist A')=>({isrc:id,title:id,artist,album:'Album',albumUri,durationMs:60000,source:'SPOTIFY'});
const trackEnvelope=id=>({itemV2:{data:{uri:`spotify:track:${String(id).padStart(22,'0')}`,name:`Song ${id}`,duration:{totalMilliseconds:60000},artists:{items:[{profile:{name:'Artist'}}]},albumOfTrack:{uri:albumUri,name:'Album'}}}});
test('Spotify URLs validate host and decode album identity',()=>{
  assert.equal(resource('https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa?si=abc'),albumUri);
  assert.throws(()=>resource('https://open.spotify.com.evil/album/4m2880jivSbbyEGAKfITCa'));
  assert.throws(()=>resource('https://open.spotify.com/playlist/../../secret'));
  assert.equal(decodeTrack(trackEnvelope(1)).albumUri,albumUri);
});
test('public playlist pagination completes before an import can be saved',async()=>{
  const catalog=new Catalog(),offsets=[];
  catalog.execute=async(op,v)=>{
    if(op==='fetchPlaylistMetadata')return {playlistV2:{uri:v.uri,name:'Playlist'}};
    offsets.push(v.offset);
    return {playlistV2:{content:{totalCount:150,items:Array.from({length:v.offset===0?100:50},(_,i)=>trackEnvelope(v.offset+i+1)),pagingInfo:{nextOffset:v.offset+100}}}};
  };
  const result=await catalog.collection('spotify:playlist:37i9dQZF1DXcBWIGoYBM5M');
  assert.equal(result.tracks.length,150);assert.deepEqual(offsets,[0,100]);
  catalog.execute=async(op,v)=>op==='fetchPlaylistMetadata'?{playlistV2:{uri:v.uri,name:'Playlist'}}:{playlistV2:{content:{totalCount:150,items:[]}}};
  await assert.rejects(()=>catalog.collection('spotify:playlist:37i9dQZF1DXcBWIGoYBM5M'),/Incomplete/);
});
test('podcast parsing handles enclosures, upgrades HTTP, deduplicates and rejects external entities',()=>{
  const show={title:'Show',feedUrl:'https://example.org/feed.xml'};
  const xml='<rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel><item><guid>one</guid><title>Episode</title><itunes:duration>1:30</itunes:duration><enclosure url="http://example.org/audio.mp3" type="audio/mpeg"/></item><item><guid>one</guid><enclosure url="http://example.org/audio.mp3"/></item><item><enclosure url="https://example.org/video.mp4" type="video/mp4"/></item></channel></rss>';
  const episodes=parseFeed(xml,show);assert.equal(episodes.length,1);assert.equal(episodes[0].durationMs,90000);assert.equal(episodes[0].directAudioUrl,'https://example.org/audio.mp3');
  const repeated=parseFeed(xml.replace('<title>Episode</title>','<title>Coral reef story</title><itunes:title>Coral reef story</itunes:title>').replace('<itunes:duration>1:30</itunes:duration>','<duration>1:30</duration><itunes:duration>1:30</itunes:duration>'),show);
  assert.equal(repeated[0].title,'Coral reef story');assert.equal(repeated[0].durationMs,90000);
  assert.throws(()=>parseFeed('<!DOCTYPE x [<!ENTITY ext SYSTEM "file:///secret">]><rss/>',show));
});
test('publisher requests reject loopback, private and mapped private addresses',()=>{
  for(const ip of ['127.0.0.1','10.1.2.3','192.168.1.1','172.16.0.1','169.254.169.254','100.64.0.1','::1','fc00::1','fe80::1','::ffff:127.0.0.1'])assert.equal(publicAddress(ip),false,ip);
  assert.equal(publicAddress('8.8.8.8'),true);assert.equal(publicAddress('2606:4700:4700::1111'),true);
});
test('library mutations survive concurrency and restart; failed mutations do not commit',async t=>{
  const directory=await mkdtemp(path.join(os.tmpdir(),'spot-library-'));t.after(()=>rm(directory,{recursive:true,force:true}));
  const store=new Store(directory);await store.load();
  await Promise.all([store.favorite(song('a')),store.favorite(song('b')),store.importCollection({title:'Imported',tracks:[song('a'),song('b'),song('a')]})]);
  assert.equal(store.snapshot().favorites.length,2);assert.equal(store.snapshot().playlists[0].tracks.length,2);
  await assert.rejects(()=>store.change(s=>{s.favorites=[];throw Error('interrupted');}));
  const restart=new Store(directory);await restart.load();assert.equal(restart.snapshot().favorites.length,2);assert.equal(restart.snapshot().playlists[0].tracks[0].albumUri,albumUri);
});
test('saved source URLs persist across restart, expiry forces a new lookup, local files support ranges',async t=>{
  const directory=await mkdtemp(path.join(os.tmpdir(),'spot-audio-'));t.after(()=>rm(directory,{recursive:true,force:true}));
  let calls=0;const runner=async()=>{calls++;return {stdout:JSON.stringify({id:'abcdefghijk',url:`https://rr1.googlevideo.com/audio?expire=${Math.floor(Date.now()/1000)+3600}`,ext:'m4a'})};};
  const resolver=new Resolver(directory,'unused','unused',runner,async()=>({statusCode:206,destroy(){}}));await resolver.load();await resolver.resolve(song());assert.equal(calls,1);
  const restart=new Resolver(directory,'unused','unused',runner,async()=>({statusCode:206,destroy(){}}));await restart.load();await restart.resolve(song());assert.equal(calls,1);
  restart.cache.get('one').expires=0;await restart.resolve(song());assert.equal(calls,2);
  const bytes=Buffer.from('0123456789');await writeFile(path.join(directory,'saved.audio'),bytes);restart.cache.set('one',{id:'one',file:'saved.audio',mime:'audio/wav',expires:0});
  const source=await restart.resolve(song());const response=await restart.response(new Request(source.url,{headers:{Range:'bytes=2-5'}}));
  assert.equal(response.status,206);assert.equal(await response.text(),'2345');assert.equal(response.headers.get('content-range'),'bytes 2-5/10');assert.equal(calls,2);
  for(let i=0;i<501;i++)restart.cache.set(`stream-${i}`,{id:`stream-${i}`,url:'https://rr1.googlevideo.com/audio',expires:Date.now()+3600000});
  await restart.persist();
  const afterEviction=new Resolver(directory,'unused','unused',runner,async()=>({statusCode:206,destroy(){}}));await afterEviction.load();
  assert.equal((await afterEviction.resolve(song())).saved,true);assert.equal(calls,2);
  if(process.platform!=='win32')assert.equal((await stat(path.join(directory,'sources.json'))).mode&0o777,0o600);
});
test('mixes follow listening history and rotate without podcast contamination',async()=>{
  const catalog={search:async seed=>({tracks:Array.from({length:25},(_,i)=>song(seed+i,seed)),albums:[],playlists:[]})};
  const a={favorites:[],history:[{track:song('old','Artist A')}]};
  const first=await recommendations(catalog,a,1),rotated=await recommendations(catalog,a,2);
  assert.notDeepEqual(first.mixes[0].tracks.map(x=>x.isrc),rotated.mixes[0].tracks.map(x=>x.isrc));
  a.history.unshift({track:song('new','Artist B')});assert.equal(seedArtists(a.history,a.favorites)[0],'Artist B');
  const next=await recommendations(catalog,a,1);assert.ok(next.mixes[0].tracks.some(x=>x.artist==='Artist B'));
  a.history.unshift({track:{...song('podcast','Podcast Host'),source:'PODCAST'}});assert.equal(seedArtists(a.history,a.favorites)[0],'Artist B');
});
test('Charts loads the real US playlist and album navigation uses album IDs',async()=>{
  const calls=[],catalog={collection:async uri=>{calls.push(uri);return {title:'Top 50 - USA',tracks:[song()],artwork:null};}};
  const result=await browse(catalog,'Charts');assert.deepEqual(calls,[US.uri]);assert.equal(result.rails[0].cards[0].uri,US.uri);
  await albumForTrack(catalog,song());assert.equal(calls[1],albumUri);
});

test('blocked direct URLs use a playable manifest without changing a chosen YouTube video',async t=>{
  const directory=await mkdtemp(path.join(os.tmpdir(),'spot-hls-unit-'));t.after(()=>rm(directory,{recursive:true,force:true}));
  const calls=[];
  const runner=async(_binary,args)=>{calls.push(args);const hls=args.includes('youtube:player_client=web_safari');return{stdout:JSON.stringify({id:'abcdefghijk',url:hls?'https://manifest.googlevideo.com/index.m3u8':'https://rr1.googlevideo.com/audio',protocol:hls?'m3u8_native':'https',ext:hls?'mp4':'m4a'})};};
  const request=async()=>({statusCode:403,destroy(){}});
  const resolver=new Resolver(directory,'unused','unused',runner,request);await resolver.load();
  const result=await resolver.resolve({...song(),source:'YOUTUBE',youtubeVideoId:'abcdefghijk'});
  assert.equal(result.kind,'hls');assert.equal(calls.length,2);
  assert.equal(calls[1].at(-1),'https://www.youtube.com/watch?v=abcdefghijk');
  assert.equal(resolver.cache.get('one').videoId,'abcdefghijk');
});
test('HLS manifests rewrite segments and keys into opaque app URLs and reject other hosts',()=>{
  const refs=[];
  const out=rewriteManifest('#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXTINF:5,\nsegment.ts\n','https://manifest.googlevideo.com/index.m3u8',(url,manifest)=>{refs.push({url,manifest});return `spot://app/audio/${refs.length}`;});
  assert.ok(out.includes('URI="spot://app/audio/1"'));assert.ok(out.includes('spot://app/audio/2'));
  assert.equal(refs[1].manifest,false);assert.equal(refs[1].url,'https://manifest.googlevideo.com/segment.ts');
  assert.throws(()=>rewriteManifest('#EXTM3U\nhttps://example.org/segment.ts','https://manifest.googlevideo.com/index.m3u8',x=>x),/Unsupported/);
});
test('saved HLS manifests and segments play after restart without network',async t=>{
  const {Readable}=await import('node:stream');
  const directory=await mkdtemp(path.join(os.tmpdir(),'spot-offline-hls-'));t.after(()=>rm(directory,{recursive:true,force:true}));
  const base='https://manifest.googlevideo.com/index.m3u8';
  const requester=async url=>{const stream=Readable.from([Buffer.from(url===base?'#EXTM3U\n#EXTINF:5,\nsegment.ts\n#EXT-X-ENDLIST\n':'media-bytes')]);stream.statusCode=200;stream.headers={'content-type':url===base?'application/vnd.apple.mpegurl':'video/mp2t'};return stream;};
  const resolver=new Resolver(directory,'unused','unused',async()=>{throw Error('not used');},requester);await resolver.load();
  resolver.cache.set('one',{id:'one',url:base,kind:'hls',mime:'audio/mp4',expires:Date.now()+3600000});
  await resolver.download(song());
  const restart=new Resolver(directory,'unused','unused',async()=>{throw Error('offline');},async()=>{throw Error('offline');});await restart.load();
  const source=await restart.resolve(song());assert.equal(source.saved,true);assert.equal(source.kind,'hls');
  const manifest=await (await restart.response(new Request(source.url))).text();
  const segment=manifest.split('\n').find(x=>x.startsWith('spot:'));
  assert.equal(await (await restart.response(new Request(segment))).text(),'media-bytes');
});

test('artist overview exposes stats, verification, latest release and related artists',()=>{
  const img=[{url:'https://i.scdn.co/image/abc',width:300}];
  const info=artistInfo({stats:{monthlyListeners:1234,followers:'99'},profile:{verified:true},discography:{latest:{uri:'spotify:album:4m2880jivSbbyEGAKfITCa',name:'New One',type:'SINGLE',date:{year:2026},coverArt:{sources:img}}},relatedContent:{relatedArtists:{items:[{uri:'spotify:artist:0TnOYISbd1XYRBk9myaseg',profile:{name:'Friend'},visuals:{avatarImage:{sources:img}}},{uri:'spotify:track:x',profile:{name:'Not an artist'}}]}}});
  assert.deepEqual([info.monthlyListeners,info.followers,info.verified],[1234,99,true]);
  assert.equal(info.latest.title,'New One');assert.equal(info.latest.subtitle,'Single · 2026');
  assert.deepEqual(info.related.map(x=>x.title),['Friend']);assert.equal(info.related[0].artwork,'https://i.scdn.co/image/abc');
  assert.deepEqual(artistInfo({}),{monthlyListeners:null,followers:null,verified:false,latest:null,related:[]});
});
test('radio mixes the seed artist with related artists and skips what is already queued',async()=>{
  const t=(id,artist)=>({isrc:id,title:id,artist,source:'SPOTIFY'});
  const catalog={
    search:async q=>({tracks:[t('s1','Seed'),t('x','Other')],artists:[{uri:'spotify:artist:0TnOYISbd1XYRBk9myaseA',title:'Seed'}]}),
    collection:async uri=>uri.endsWith('A')?{tracks:[t('s2','Seed'),t('s3','Seed'),t('queued','Seed')],artist:{related:[{uri:'spotify:artist:0TnOYISbd1XYRBk9myaseB',title:'Pal'}]}}:{tracks:[t('p1','Pal'),t('p2','Pal')]},
  };
  const result=await radio(catalog,{artist:'Seed, Guest',title:'Song',isrc:'seed-song'},['queued']);
  assert.equal(result.title,'Song Radio');assert.equal(result.radio,true);
  const ids=result.tracks.map(x=>x.isrc);
  assert.ok(ids.includes('p1')&&ids.includes('s2'));assert.ok(!ids.includes('queued')&&!ids.includes('x'));
  assert.equal(new Set(ids).size,ids.length);
  assert.match(result.subtitle,/Pal/);
  await assert.rejects(()=>radio({search:async()=>({tracks:[],artists:[]})},{artist:'Nobody'}),/Couldn't build a radio/);
});
