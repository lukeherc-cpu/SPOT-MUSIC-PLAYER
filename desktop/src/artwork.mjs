import {publicRequest,secureUrl} from './public-network.mjs';

/** Bounded image cache using the same validated, pinned public-network path as feeds. */
export class ArtworkCache {
  constructor(){this.cache=new Map();this.inflight=new Map();this.bytes=0;}
  async get(raw){
    const url=secureUrl(raw).href;
    if(this.cache.has(url))return this.cache.get(url);
    if(this.inflight.has(url))return this.inflight.get(url);
    const task=(async()=>{
      const response=await publicRequest(url),mime=String(response.headers['content-type']||'').split(';')[0];
      if(response.statusCode!==200||!['image/jpeg','image/png','image/webp','image/gif','image/avif'].includes(mime)){response.destroy();throw Error('Artwork unavailable.');}
      const chunks=[];let size=0;
      for await(const chunk of response){size+=chunk.length;if(size>8*1024*1024){response.destroy();throw Error('Artwork is too large.');}chunks.push(chunk);}
      const value={body:Buffer.concat(chunks),mime};
      while(this.bytes+size>32*1024*1024&&this.cache.size){const key=this.cache.keys().next().value;this.bytes-=this.cache.get(key).body.length;this.cache.delete(key);}
      this.bytes+=size;this.cache.set(url,value);return value;
    })();
    this.inflight.set(url,task);try{return await task;}finally{this.inflight.delete(url);}
  }
}
