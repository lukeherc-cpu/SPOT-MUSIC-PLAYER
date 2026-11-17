import https from 'node:https';
import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';

export function publicAddress(address) {
  const ip=address.toLowerCase();
  if(isIP(ip)===4) {
    const [a,b]=ip.split('.').map(Number);
    return !(a===0||a===10||a===127||a>=224||(a===169&&b===254)||(a===172&&b>=16&&b<=31)||(a===192&&b===168)||(a===100&&b>=64&&b<=127)||(a===198&&(b===18||b===19)));
  }
  // Only global unicast IPv6; mapped IPv4 is validated by its embedded address.
  if(ip.startsWith('::ffff:')) { const tail=ip.slice(7); if(isIP(tail)===4)return publicAddress(tail); return false; }
  return isIP(ip)===6 && /^[23]/.test(ip) && !ip.startsWith('2001:db8:') && !ip.startsWith('2002:') && !/^2001:(?:0:|:)/.test(ip);
}
export function secureUrl(value) {
  const u=new URL(value); if(u.protocol==='http:')u.protocol='https:';
  if(u.protocol!=='https:'||u.username||u.password||(u.port&&u.port!=='443'))throw Error('Only public HTTPS publisher URLs are supported.');
  return u;
}
export async function publicRequest(raw,{range,redirects=5}={}) {
  const u=secureUrl(raw);
  const addresses=await lookup(u.hostname,{all:true});
  if(!addresses.length||addresses.some(x=>!publicAddress(x.address)))throw Error('Publisher address is not public.');
  const response=await new Promise((resolve,reject)=> {
    const request=https.get(u,{headers:{'User-Agent':'SpotMusic/0.2 PodcastPlayer',...(range?{Range:range}:{})},lookup:(_host,options,callback)=> {
      if(options.all)callback(null,addresses);else callback(null,addresses[0].address,addresses[0].family);
    }},resolve);
    request.setTimeout(35000,()=>request.destroy(Error('Publisher timed out.'))); request.on('error',reject);
  });
  if([301,302,303,307,308].includes(response.statusCode)) {
    response.resume(); if(redirects<=0)throw Error('Too many publisher redirects.');
    return publicRequest(new URL(response.headers.location,u).href,{range,redirects:redirects-1});
  }
  return response;
}
export async function readPublic(url,limit=16*1024*1024) {
  const r=await publicRequest(url);if(r.statusCode<200||r.statusCode>=300){r.destroy();throw Error(`Publisher returned HTTP ${r.statusCode}.`);}
  const chunks=[];let size=0;
  for await(const chunk of r){size+=chunk.length;if(size>limit){r.destroy();throw Error('Publisher feed is too large.');}chunks.push(chunk);}
  return Buffer.concat(chunks).toString('utf8');
}
