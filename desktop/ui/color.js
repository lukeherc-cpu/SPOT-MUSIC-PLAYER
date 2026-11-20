// Picks one representative colour from cover art for the player bar.
const cache=new Map();
function hash(text){let n=2166136261;for(const c of String(text))n=Math.imul(n^c.charCodeAt(0),16777619);return n>>>0;}
export function fallbackHue(seed){return hash(seed)%360;}
function toHsl(r,g,b){r/=255;g/=255;b/=255;const max=Math.max(r,g,b),min=Math.min(r,g,b),l=(max+min)/2;if(max===min)return[0,0,l*100];const d=max-min,s=l>.5?d/(2-max-min):d/(max+min);let hue=max===r?(g-b)/d+(g<b?6:0):max===g?(b-r)/d+2:(r-g)/d+4;return[hue*60,s*100,l*100];}
export function artColor(url,seed=''){
  if(!url||!/^https:\/\//.test(url))return Promise.resolve({h:fallbackHue(seed||'spot'),s:45});
  if(cache.has(url))return cache.get(url);
  const result=new Promise(resolve=>{
    const image=new Image();image.crossOrigin='anonymous';
    const fallback=()=>resolve({h:fallbackHue(url),s:45});
    image.onload=()=>{try{
      const canvas=document.createElement('canvas');canvas.width=canvas.height=24;const g=canvas.getContext('2d',{willReadFrequently:true});g.drawImage(image,0,0,24,24);
      const data=g.getImageData(0,0,24,24).data;let best=null,total=[0,0,0,0];
      const buckets=new Map();
      for(let i=0;i<data.length;i+=4){const [hue,sat,light]=toHsl(data[i],data[i+1],data[i+2]);total[0]+=data[i];total[1]+=data[i+1];total[2]+=data[i+2];total[3]++;if(sat<18||light<12||light>90)continue;const key=Math.round(hue/20);const b=buckets.get(key)||{weight:0,h:0,s:0};b.weight+=sat;b.h+=hue*sat;b.s+=sat*sat;buckets.set(key,b);}
      for(const b of buckets.values())if(!best||b.weight>best.weight)best=b;
      if(best)resolve({h:best.h/best.weight,s:Math.min(80,best.s/best.weight)});
      else{const [hue,sat]=toHsl(total[0]/total[3],total[1]/total[3],total[2]/total[3]);resolve({h:hue,s:Math.min(30,sat)});}
    }catch{fallback();}};
    image.onerror=fallback;
    image.src=`spot-art://image/?url=${encodeURIComponent(url)}`;
  });
  cache.set(url,result);return result;
}
