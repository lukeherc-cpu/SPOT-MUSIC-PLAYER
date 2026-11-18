package com.gamut.speech

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.gamut.podcasts.PublicPodcastDns
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** Decode a bounded window ahead of playback. Never record the microphone or upload audio. */
internal object PodcastPcmDecoder {
    suspend fun decode(source: String, startMs: Long, lengthMs: Long): FloatArray {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            if (source.startsWith("/")) {
                require(File(source).isFile)
                extractor.setDataSource(source)
            } else {
                val uri = URI(source)
                require(uri.scheme == "https" && uri.userInfo == null && !uri.host.isNullOrBlank()) { "Unsupported podcast audio address" }
                PublicPodcastDns.lookup(uri.host)
                extractor.setDataSource(source, mapOf("User-Agent" to "SpotMusic/1.0", "Accept-Encoding" to "identity"))
            }
            val audio = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: error("No audio track")
            val format = extractor.getTrackFormat(audio)
            extractor.selectTrack(audio)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { decoder = it }
            codec.configure(format, null, null, 0); codec.start()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val output = FloatArray((lengthMs * 16).toInt())
            var written = 0
            var inputEnded = false
            var ended = false
            var targetUs = startMs * 1000.0
            val untilUs = (startMs + lengthMs) * 1000.0
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 60_000_000_000L
            while (!ended && targetUs < untilUs) {
                currentCoroutineContext().ensureActive()
                check(System.nanoTime() < deadline) { "Podcast audio decoding timed out" }
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                        else { codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val actual = codec.outputFormat
                        sampleRate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (actual.containsKey(MediaFormat.KEY_PCM_ENCODING)) actual.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    }
                    else -> if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        require(encoding == AudioFormat.ENCODING_PCM_FLOAT || encoding == AudioFormat.ENCODING_PCM_16BIT) { "Unsupported decoded audio format" }
                        val frames = info.size / (bytes * channels)
                        val endUs = info.presentationTimeUs + frames * 1_000_000.0 / sampleRate
                        if (targetUs < info.presentationTimeUs) targetUs = max(targetUs, info.presentationTimeUs.toDouble())
                        while (targetUs < endUs && targetUs < untilUs && frames > 0) {
                            val at = ((targetUs - info.presentationTimeUs) * sampleRate / 1_000_000.0).coerceIn(0.0, (frames - 1).toDouble())
                            val frame = floor(at).toInt(); val next = min(frame + 1, frames - 1); val mix = (at - frame).toFloat()
                            fun sample(frameIndex: Int): Float {
                                var sum = 0f
                                repeat(channels) { channel ->
                                    val offset = info.offset + (frameIndex * channels + channel) * bytes
                                    sum += if (bytes == 4) buffer.getFloat(offset) else buffer.getShort(offset) / 32768f
                                }
                                return sum / channels
                            }
                            val slot = ((targetUs - startMs * 1000) * .016).roundToInt()
                            if (slot in output.indices) { output[slot] = (sample(frame) * (1 - mix) + sample(next) * mix).coerceIn(-1f, 1f); written = max(written, slot + 1) }
                            targetUs += 62.5
                        }
                        ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            return output.copyOf(written)
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            decoder?.release(); extractor.release()
        }
    }
}
