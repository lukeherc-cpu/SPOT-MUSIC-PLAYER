export function skeleton(kind='page'){
  if(kind==='words')return '<div class="skeleton words-skeleton" role="status" aria-label="Loading lyrics"><i></i><i></i><i></i><i></i><i></i></div>';
  return `<div class="skeleton content-skeleton" role="status" aria-label="Loading content"><i class="skeleton-heading"></i><div class="skeleton-rail">${Array.from({length:3},()=>'<div><i class="skeleton-art"></i><i class="skeleton-label"></i></div>').join('')}</div>${kind==='page'?'<i class="skeleton-row"></i><i class="skeleton-row"></i><i class="skeleton-row"></i>':''}</div>`;
}
export function wordsBody(state,h){
  const data=state.subtitle;
  if(!data||(!data.cues?.length&&data.working))return skeleton('words');
  if(!data.cues?.length)return `<p>${h(data.error||'No words in this section yet.')}</p>`;
  return `${data.generated?'<small>Auto-generated subtitles</small>':data.clean?'<small>Clean lyrics</small>':!data.timed?'<small>Timing unavailable</small>':''}${data.cues.map((cue,index)=>data.timed?`<p data-word-index="${index}" data-action="seek-word" data-ms="${Math.max(0,Number(cue.startMs)||0)}" title="Play from this line">${h(cue.text)}</p>`:`<p data-word-index="${index}">${h(cue.text)}</p>`).join('')}`;
}
export function wordStage(state,cover,h){
  const title=state.now.source==='PODCAST'?'Subtitles':'Lyrics';
  return `<div class="word-stage ${state.words?'flipped':''}"><div class="word-flip"><button class="word-face word-front" data-action="flip-words" aria-label="Cover art, show ${title.toLowerCase()}">${cover(state.now.albumArtUrl)}<span>Tap for ${title.toLowerCase()}</span></button><section class="word-face word-back"><header><strong data-action="flip-words">${title}</strong><button class="icon" data-action="expand-words" aria-label="Expand ${title}">⛶</button></header><div class="words-body" data-action="flip-words" role="button" tabindex="0" aria-label="${title}, tap to return to cover art">${wordsBody(state,h)}</div></section></div></div>`;
}
export function expandedWords(state,h){
  const title=state.now.source==='PODCAST'?'Subtitles':'Lyrics';
  return `<section class="expanded-words" role="dialog" aria-modal="true" aria-label="Expanded ${title}"><header><strong>${title}</strong>${textSize()}<button class="icon" data-action="minimize-words" aria-label="Minimize text">⛶</button></header><div class="words-body" data-action="flip-words" role="button" tabindex="0" aria-label="${title}, tap to return to cover art">${wordsBody(state,h)}</div></section>`;
}
export function textSize(){return '<span class="text-size" role="group" aria-label="Text size"><button class="icon small" data-action="lyric-smaller" aria-label="Smaller text">A−</button><button class="icon small" data-action="lyric-larger" aria-label="Larger text">A+</button></span>';}
export function lyricsPopup(state,h){
  const title=state.now?.source==='PODCAST'?'Subtitles':'Lyrics';
  return `<section class="lyrics-popup" role="dialog" aria-label="${title} popup"><header><strong>${title}</strong>${textSize()}<button class="icon small" data-action="expand-popup-words" aria-label="Open full ${title.toLowerCase()}">⛶</button><button class="icon small" data-action="close-lyrics-popup" aria-label="Close ${title.toLowerCase()} popup">✕</button></header><div class="words-body popup-body">${wordsBody(state,h)}</div></section>`;
}
