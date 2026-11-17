import { createHash } from 'node:crypto';
import { XMLParser } from 'fast-xml-parser';
import { readPublic, secureUrl } from './public-network.mjs';

const list=x=>x==null?[]:Array.isArray(x)?x:[x];
const plain=x=>String(typeof x==='object'?x?.['#text']||'':x||'').replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim();
function milliseconds(raw){const parts=String(raw||'').split(':').map(Number);return parts.every(Number.isFinite)?parts.reduce((a,n)=>a*60+n,0)*1000:0;}
export function parseFeed(xml,show) {
  if(Buffer.byteLength(xml)>16*1024*1024 || /<!DOCTYPE|<!ENTITY/i.test(xml))throw Error('Unsupported podcast feed.');
  const root=new XMLParser({ignoreAttributes:false,attributeNamePrefix:'',removeNSPrefix:true,processEntities:false}).parse(xml);
  const channel=root.rss?.channel||root.feed||{};
  const out=[];const seen=new Set();
  for(const item of list(channel.item||channel.entry)) {
    const enclosure=list(item.enclosure).find(x=>!x.type||x.type.startsWith('audio/'))||list(item.link).find(x=>x.rel==='enclosure'&&(!x.type||x.type.startsWith('audio/')));
    const raw=enclosure?.url||enclosure?.href;if(!raw)continue;
    let audio;try{audio=secureUrl(new URL(raw,show.feedUrl).href).href;}catch{continue;}
    if(enclosure.type?.startsWith('video/'))continue;
    const id=createHash('sha256').update(show.feedUrl+'|'+plain(item.guid||item.id||audio)).digest('hex').slice(0,24);
    if(seen.has(id))continue;seen.add(id);
    out.push({isrc:`podcast:${id}`,title:plain(item.title)||'Episode',artist:show.title,album:show.title,albumArtUrl:item.image?.href||show.artwork,durationMs:milliseconds(item.duration),source:'PODCAST',directAudioUrl:audio,feedUrl:show.feedUrl,description:plain(item.description||item.summary||item.content).slice(0,4000),published:plain(item.pubDate||item.published)});
    if(out.length>=500)break;
  }
  return out;
}
export async function searchPodcasts(query) {
  const params=new URLSearchParams({term:query,media:'podcast',entity:'podcast',limit:'30',country:'US'});
  const result=JSON.parse(await readPublic('https://itunes.apple.com/search?'+params,4e6));
  return (result.results||[]).filter(x=>x.feedUrl).map(x=>({id:String(x.collectionId),title:x.collectionName,author:x.artistName||'',feedUrl:secureUrl(x.feedUrl).href,artwork:x.artworkUrl600||x.artworkUrl100}));
}
export async function episodes(show){const tracks=parseFeed(await readPublic(show.feedUrl),show);if(!tracks.length)throw Error('This feed has no available audio episodes.');return tracks;}
