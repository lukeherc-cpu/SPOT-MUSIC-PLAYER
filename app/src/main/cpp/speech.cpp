#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <mutex>
#include <string>
#include <sstream>
#include <algorithm>

static std::mutex speech_mutex;
static whisper_context *context = nullptr;
static std::string loaded_model;
static std::atomic<bool> cancelled{false};
static std::string escaped(const char *value) {
    std::string out;
    for (const unsigned char c : std::string(value)) {
        if (c == '"' || c == '\\') { out += '\\'; out += c; }
        else if (c == '\n') out += "\\n";
        else if (c == '\r') out += "\\r";
        else if (c == '\t') out += "\\t";
        else if (c >= 32) out += c;
    }
    return out;
}
static jbyteArray bytes(JNIEnv *env, const std::string &text) {
    auto result = env->NewByteArray(text.size());
    env->SetByteArrayRegion(result, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}
extern "C" JNIEXPORT void JNICALL Java_com_gamut_speech_WhisperNative_cancel(JNIEnv *, jobject) { cancelled = true; }
extern "C" JNIEXPORT void JNICALL Java_com_gamut_speech_WhisperNative_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(speech_mutex);
    if (context) whisper_free(context);
    context = nullptr; loaded_model.clear();
}
extern "C" JNIEXPORT jstring JNICALL Java_com_gamut_speech_WhisperNative_language(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> guard(speech_mutex);
    return env->NewStringUTF(context ? whisper_lang_str(whisper_full_lang_id(context)) : "auto");
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_gamut_speech_WhisperNative_transcribe(JNIEnv *env, jobject, jstring model, jfloatArray audio, jstring language) {
    std::lock_guard<std::mutex> guard(speech_mutex);
    cancelled = false;
    const char *path = env->GetStringUTFChars(model, nullptr);
    std::string wanted(path);
    env->ReleaseStringUTFChars(model, path);
    if (!context || loaded_model != wanted) {
        if (context) whisper_free(context);
        auto options = whisper_context_default_params(); options.use_gpu = false;
        context = whisper_init_from_file_with_params(wanted.c_str(), options);
        loaded_model = wanted;
    }
    if (!context) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Could not load Whisper Tiny"); return nullptr; }
    auto parameters = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    parameters.n_threads = 2;
    const char *language_chars = env->GetStringUTFChars(language, nullptr);
    std::string selected_language(language_chars);
    env->ReleaseStringUTFChars(language, language_chars);
    parameters.language = selected_language.c_str();
    parameters.translate = false;
    parameters.print_realtime = false; parameters.print_progress = false; parameters.print_timestamps = false; parameters.print_special = false;
    parameters.no_context = true; parameters.suppress_blank = true; parameters.suppress_nst = true;
    parameters.token_timestamps = true; parameters.max_len = 64; parameters.split_on_word = true;
    parameters.abort_callback = [](void *) { return cancelled.load(); };
    const auto count = env->GetArrayLength(audio);
    auto pcm = env->GetFloatArrayElements(audio, nullptr);
    const int result = whisper_full(context, parameters, pcm, count);
    env->ReleaseFloatArrayElements(audio, pcm, JNI_ABORT);
    if (result != 0) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), cancelled ? "Transcription cancelled" : "Transcription failed"); return nullptr; }
    std::ostringstream json; json << "[";
    for (int i = 0; i < whisper_full_n_segments(context); ++i) {
        if (i) json << ",";
        json << "{\"start\":" << whisper_full_get_segment_t0(context, i) * 10 << ",\"end\":" << whisper_full_get_segment_t1(context, i) * 10 << ",\"text\":\"" << escaped(whisper_full_get_segment_text(context, i)) << "\"}";
    }
    json << "]"; return bytes(env, json.str());
}
