package com.gamut.speech

@androidx.annotation.Keep
internal object WhisperNative {
    @Volatile private var loaded = false
    @Synchronized fun load() { if (!loaded) { System.loadLibrary("spot_speech"); loaded = true } }
    fun cancelIfLoaded() { if (loaded) cancel() }
    fun releaseIfLoaded() { if (loaded) release() }
    external fun transcribe(model: String, pcm: FloatArray, language: String): ByteArray
    external fun language(): String
    external fun cancel()
    external fun release()
}
