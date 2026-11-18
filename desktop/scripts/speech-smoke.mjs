import {mkdtemp,rm,readFile} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {SubtitleEngine} from '../src/subtitles.mjs';
const platform=process.platform;
const packaged=platform==='darwin'?path.resolve('dist',(process.env.SPOT_TARGET_ARCH||process.arch)==='arm64'?'mac-arm64':'mac','Spot Music.app/Contents/Resources/speech'):path.resolve('dist',platform==='win32'?'win-unpacked':'linux-unpacked','resources/speech');
const resources=process.env.SPOT_SPEECH_RESOURCES||packaged;
const directory=await mkdtemp(path.join(os.tmpdir(),'spot-speech-check-'));
const sample=path.resolve('../build/speech-bootstrap/whisper.cpp-1.9.5/samples/jfk.wav');
const engine=new SubtitleEngine(directory,resources,async()=>sample);
try{
  engine.start({isrc:'podcast:real-speech-fixture',title:'Public speech fixture',artist:'Fixture',durationMs:11000,source:'PODCAST'});
  const deadline=Date.now()+120000;
  while(Date.now()<deadline&&!engine.state.cues.length&&!engine.state.error)await new Promise(resolve=>setTimeout(resolve,250));
  const result=engine.snapshot();
  assert.ok(result.generated);
  assert.ok(result.cues.every(cue=>cue.startMs>=0&&cue.endMs>cue.startMs&&cue.endMs<=14000));
  assert.match(result.cues.map(cue=>cue.text).join(' '),/fellow Americans/i);
  console.log('Real speech transcribed offline with timed, cached subtitles:',result.cues.length,'cues');
}finally{engine.stop();await engine.task;await rm(directory,{recursive:true,force:true,maxRetries:5,retryDelay:200});}
