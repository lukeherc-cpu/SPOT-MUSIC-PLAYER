// Opt-in real-network check through the actual renderer; uses a disposable profile.
import {_electron as electron} from '@playwright/test';
import {mkdtemp,mkdir,readFile,writeFile,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const directory=await mkdtemp(path.join(os.tmpdir(),'spot-live-audio-'));
const tracks=[{isrc:'spotify:4uLU6hMCjMI75M1A2tKUQC',title:'Never Gonna Give You Up',artist:'Rick Astley',durationMs:213000,source:'SPOTIFY'},{isrc:'spotify:69kOkLUCkxIZYexIgSG8rq',title:'Get Lucky',artist:'Daft Punk',durationMs:369000,source:'SPOTIFY'}];
await mkdir(path.join(directory,'audio'));
if(process.env.SPOT_PROFILE_CACHE)await writeFile(path.join(directory,'audio/sources.json'),await readFile(process.env.SPOT_PROFILE_CACHE));
await writeFile(path.join(directory,'library.json'),JSON.stringify({version:1,favorites:tracks,playlists:[],shows:[],history:[],positions:{}}));
const flags=process.platform==='linux'?['--no-sandbox','--ozone-platform=x11']:[];
const app=await electron.launch({...(process.env.SPOT_PACKAGED_EXEC?{executablePath:path.resolve(process.env.SPOT_PACKAGED_EXEC)}:{}),args:[...(process.env.SPOT_PACKAGED_EXEC?[]:[path.resolve('.')]),...flags],env:{...process.env,APPIMAGE_EXTRACT_AND_RUN:'1',SPOT_TEST_DATA:directory}});
app.process().stderr.on('data',data=>{const text=data.toString();if(text.includes('Music lookup error'))console.log(text);});
try{
 const page=await app.firstWindow();page.on('pageerror',e=>console.log('Renderer error',e.message));page.on('console',m=>{if(['error','warning'].includes(m.type()))console.log('Console',m.text().replace(/https?:\/\/\S+/g,'[url]'));});page.on('response',r=>{if(r.status()>=400){const u=new URL(r.url());console.log('HTTP failure',r.status(),u.protocol,u.hostname,u.pathname.startsWith('/audio/')?'audio':'other');}});await page.waitForLoadState('domcontentloaded');
 await page.locator('#sidebar [data-action="liked"]').click();
 for(const track of tracks){
  const start=Date.now();
  await page.getByRole('button',{name:`Play ${track.title}`,exact:true}).first().click();
  try{await page.waitForFunction(()=>{const a=document.querySelector('#audio');return !a.paused&&a.currentTime>2;},null,{timeout:120000});}catch(e){console.log('Failure state',await page.locator('#audio').evaluate(a=>({time:a.currentTime,ready:a.readyState,network:a.networkState,error:a.error?.message})),await page.locator('#toast').innerText());throw e;}
  const playback=await page.locator('#audio').evaluate(a=>({playing:!a.paused,time:a.currentTime,duration:a.duration,error:a.error?.message||null}));
  const source=await page.evaluate(async track=>{const r=await window.spot.resolve(track);return r.ok?{kind:r.value.kind,saved:r.value.saved}:{error:r.error};},track);
  console.log(JSON.stringify({track:track.title,elapsedMs:Date.now()-start,...source,...playback}));
  if(playback.error||!playback.playing)throw Error('Real music playback failed.');
  await page.locator('#audio').evaluate(a=>a.pause());
 }
 console.log('LIVE AUDIO CHECK PASSED');
}finally{await app.close();await rm(directory,{recursive:true,force:true});}
