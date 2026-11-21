// Measures how often "Make a clean version" ends up with clean audio, before and after verification.
// Run after `npm run prepare:resolver` and prepare_speech.py: node scripts/clean-eval.mjs
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {appendFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {Catalog} from '../src/catalog.mjs';
import {Resolver} from '../src/resolver.mjs';
import {lyrics} from '../src/subtitles.mjs';
import {CleanVerifier} from '../src/clean-verify.mjs';
import {searchMusicSongs} from '../src/ytmusic.mjs';
import {findClean,core,pickSpotifyClean,pickVideoClean,videoTrack,baseTitle} from '../src/clean.mjs';

const run=promisify(execFile);
const SONGS=[['Stuck in the Middle','Tai Verdes'],['WHATS POPPIN (feat. DaBaby, Tory Lanez & Lil Wayne) - Remix','Jack Harlow'],['Wraith','Chance the Rapper'],['Money Trees','Kendrick Lamar'],['HUMBLE.','Kendrick Lamar'],['SICKO MODE','Travis Scott'],['Lose Yourself','Eminem'],
  ["God's Plan",'Drake'],['WAP','Cardi B'],['INDUSTRY BABY','Lil Nas X'],['rockstar','Post Malone'],['Congratulations','Post Malone'],['Mo Bamba','Sheck Wes'],
  ['Gold Digger','Kanye West'],['POWER','Kanye West'],['Starboy','The Weeknd'],['The Hills','The Weeknd'],['Love Galore','SZA'],['Kill Bill','SZA'],
  ['vampire','Olivia Rodrigo'],['Lucid Dreams','Juice WRLD'],['Mask Off','Future']];
const resources=path.resolve('resources');
const ytdlp=path.join(resources,'resolver',process.platform==='win32'?'yt-dlp.exe':'yt-dlp');
const catalog=new Catalog(),resolver=new Resolver(path.resolve('build/eval-audio'),ytdlp,process.execPath);
const verifier=new CleanVerifier({ytdlp,runtime:process.execPath,speech:path.join(resources,'speech'),lyricsFor:t=>lyrics(t)});
const search=q=>resolver.search(q,10);

// The matching shipped before verification: a Spotify clean track plays whatever the resolver finds for
// "artist title official audio" unless a clean-labeled upload was also found.
async function legacy(song){
  const artist=song.artist.split(',')[0].trim();
  const s=(await Promise.all([`${song.title} ${artist}`,`${song.title} ${artist} clean`,`${core(song.title)} ${artist} edited`].map(q=>catalog.search(q).then(r=>r.tracks||[]).catch(()=>[])))).flat();
  const v=[...new Map((await Promise.all([`${artist} ${baseTitle(song.title)} clean`,`${artist} ${baseTitle(song.title)} radio edit`].map(q=>search(q).catch(()=>[])))).flat().map(x=>[x.videoId,x])).values()];
  const clean=pickSpotifyClean(song,s),video=pickVideoClean(song,v);
  if(clean){
    if(video)return{videoId:video.videoId,durationMs:video.durationMs,how:'Spotify clean + clean upload'};
    const {stdout}=await run(ytdlp,['--flat-playlist','--no-warnings','--dump-single-json','--',`ytsearch1:${clean.artist} ${clean.title} official audio`],{timeout:60000,maxBuffer:8e6});
    const e=JSON.parse(stdout).entries?.[0];return e?{videoId:e.id,durationMs:(e.duration||0)*1000,how:'Spotify clean, audio by title search'}:null;
  }
  if(video)return{videoId:video.videoId,durationMs:video.durationMs,how:'clean-labeled upload'};
  return null;
}

const rows=[];
async function evaluate([title,artist]){
  const found=(await catalog.search(`${title} ${artist}`)).tracks||[];
  const song=found.find(t=>t.isExplicit===true&&core(t.title)===core(title))||null;
  if(!song)return{title,artist,skipped:'no explicit Spotify track'};
  const row={title:song.title,artist:song.artist};
  try{const old=await legacy(song);row.oldHow=old?.how||'nothing found';const r=old?await verifier.check(song,old.videoId,old.durationMs):{verdict:'none'};row.old=r.verdict;if(r.message)row.oldError=r.message;}catch(e){row.old='error';row.oldHow=String(e.message).slice(0,80);}
  try{const control=await verifier.control(song);row.controlHeard=control.texts.filter(t=>/\S/.test(t)).length;row.fromLyrics=control.fromLyrics;}catch{}
  try{const now=await findClean(catalog,search,song,{musicSearch:q=>searchMusicSongs(q),verifier});row.now=now.verified?'clean':'none verified';row.nowChecked=(now.checked||[]).map(x=>`${x.verdict}`).join(', ');row.nowErrors=(now.checked||[]).map(x=>x.message).filter(Boolean).slice(0,1);row.nowSource=now.track?.youtubeVideoId||'';}catch(e){row.now='error';row.nowChecked=String(e.message).slice(0,80);}
  return row;
}
for(let i=0;i<SONGS.length;i+=3)rows.push(...await Promise.all(SONGS.slice(i,i+3).map(s=>evaluate(s).catch(e=>({title:s[0],artist:s[1],skipped:String(e.message).slice(0,80)})))));

const tested=rows.filter(r=>!r.skipped),n=tested.length,count=(k,v)=>tested.filter(r=>r[k]===v).length;
const pct=x=>n?`${Math.round(100*x/n)}%`:'n/a';
const oldClean=count('old','clean'),oldExplicit=count('old','explicit'),oldNone=count('old','none');
const nowClean=count('now','clean');
const lines=[
  `## Clean version check (${n} explicit songs)`,'',
  `**Before (shipped matching):** verified clean ${oldClean}/${n} (${pct(oldClean)}), swearing heard ${oldExplicit}/${n} (${pct(oldExplicit)}), nothing found ${oldNone}/${n}, could not tell ${n-oldClean-oldExplicit-oldNone}/${n}.`,
  `**After (listen before trusting):** verified clean ${nowClean}/${n} (${pct(nowClean)}). The rest show "No verified clean version" instead of adding audio.`,'',
  '| Song | Before: how | Before: verdict | After: candidates checked | After |','|---|---|---|---|---|',
  ...rows.map(r=>r.skipped?`| ${r.title} — ${r.artist} | skipped: ${r.skipped} | | | |`:`| ${r.title} — ${r.artist} | ${r.oldHow} | ${r.old} | ${r.nowChecked||'—'} | ${r.now} |`),
];
console.log(lines.join('\n'));console.log(JSON.stringify(rows,null,1));
await writeFile('clean-eval.json',JSON.stringify(rows,null,1));
if(process.env.GITHUB_STEP_SUMMARY)await appendFile(process.env.GITHUB_STEP_SUMMARY,lines.join('\n')+'\n');
