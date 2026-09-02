#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <memory>
#include "micro_transformer.h"
#include "micro_frontier_engine.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "MicroTransformerJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <iostream>
#define LOGI(...)
#define LOGW(...)
#define LOGE(...)
#endif

enum class ModelType {
    NONE,
    TRF2_LEGACY,
    TRF3_FRONTIER
};

struct UnifiedNeuralHolder {
    ModelType type{ModelType::NONE};
    std::unique_ptr<micro_transformer::MicroTransformerModelCpp> trf2;
    std::unique_ptr<micro_frontier::MicroFrontierEngine> trf3;

    void unload() {
        if (trf2) trf2->unload();
        if (trf3) trf3->unload();
        type = ModelType::NONE;
    }

    bool isLoaded() const {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->isLoaded();
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->isLoaded();
        return false;
    }

    bool loadModel(const std::string& filePath) {
        unload();
        std::ifstream file(filePath, std::ios::binary);
        if (!file.is_open()) {
            LOGE("Failed to open model file: %s", filePath.c_str());
            return false;
        }
        uint32_t magic = 0;
        file.read(reinterpret_cast<char*>(&magic), 4);
        file.close();

        if (magic == 0x33465254 || magic == 0x54524633) { // "TRF3"
            LOGI("Detected TRF3 Frontier K-Cheb BitNet model in %s", filePath.c_str());
            trf3 = std::make_unique<micro_frontier::MicroFrontierEngine>();
            if (trf3->loadModel(filePath)) {
                type = ModelType::TRF3_FRONTIER;
                LOGI("TRF3 Loaded successfully: V=%d, D=%d, L=%d", trf3->getVocabSize(), trf3->getModelDim(), trf3->getNumLayers());
                return true;
            }
            trf3.reset();
            return false;
        } else {
            LOGI("Detected TRF2 / Legacy model in %s", filePath.c_str());
            trf2 = std::make_unique<micro_transformer::MicroTransformerModelCpp>();
            if (trf2->loadModel(filePath)) {
                type = ModelType::TRF2_LEGACY;
                LOGI("TRF2 Loaded successfully: V=%d, D=%d, L=%d", trf2->getVocabSize(), trf2->getModelDim(), trf2->getNumLayers());
                return true;
            }
            trf2.reset();
            return false;
        }
    }

    bool loadModelFromMemory(const uint8_t* data, size_t size) {
        unload();
        if (size < 4) return false;
        uint32_t magic = *reinterpret_cast<const uint32_t*>(data);
        if (magic == 0x33465254 || magic == 0x54524633) { // "TRF3"
            LOGI("Detected TRF3 Frontier buffer");
            trf3 = std::make_unique<micro_frontier::MicroFrontierEngine>();
            if (trf3->loadModelFromMemory(data, size)) {
                type = ModelType::TRF3_FRONTIER;
                return true;
            }
            trf3.reset();
            return false;
        } else {
            LOGI("Detected TRF2 buffer");
            trf2 = std::make_unique<micro_transformer::MicroTransformerModelCpp>();
            if (trf2->loadModelFromMemory(data, size)) {
                type = ModelType::TRF2_LEGACY;
                return true;
            }
            trf2.reset();
            return false;
        }
    }

    int tokenize(const char* text, size_t textLen, int32_t* outTokens, int maxTokens) {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->tokenize(text, textLen, outTokens, maxTokens);
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->tokenize(text, textLen, outTokens, maxTokens);
        return 0;
    }

    int tokenizeTail(const char* text, size_t textLen, int32_t* outTokens, int maxTokens) {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->tokenizeTail(text, textLen, outTokens, maxTokens);
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->tokenizeTail(text, textLen, outTokens, maxTokens);
        return 0;
    }

    bool forward(const int32_t* contextTokens, int numTokens, float* outHidden) {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->forward(contextTokens, numTokens, outHidden);
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->forward(contextTokens, numTokens, outHidden);
        return false;
    }

    void scoreCandidates(const float* hT, const int32_t* candidateIds, int numCandidates, float* outLogits) {
        if (type == ModelType::TRF3_FRONTIER && trf3) trf3->scoreCandidates(hT, candidateIds, numCandidates, outLogits);
        else if (type == ModelType::TRF2_LEGACY && trf2) trf2->scoreCandidates(hT, candidateIds, numCandidates, outLogits);
    }

    int scoreTopK(const float* hT, const int32_t* candidateIds, int numCandidates, int k,
                  int32_t* outTopTokens, float* outTopScores) {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->scoreTopK(hT, candidateIds, numCandidates, k, outTopTokens, outTopScores);
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->scoreTopK(hT, candidateIds, numCandidates, k, outTopTokens, outTopScores);
        return 0;
    }

    const std::vector<int32_t>& getWordStartTokenIds() const {
        static const std::vector<int32_t> empty;
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->getWordStartTokenIds();
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->getWordStartTokenIds();
        return empty;
    }

    std::string getTokenText(int32_t tokenId) const {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->getTokenText(tokenId);
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->getTokenText(tokenId);
        return "";
    }

    int getVocabSize() const {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->getVocabSize();
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->getVocabSize();
        return 0;
    }

    int getModelDim() const {
        if (type == ModelType::TRF3_FRONTIER && trf3) return trf3->getModelDim();
        if (type == ModelType::TRF2_LEGACY && trf2) return trf2->getModelDim();
        return 0;
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeCreate(
        JNIEnv* /* env */, jobject /* thisObj */) {
    auto* holder = new (std::nothrow) UnifiedNeuralHolder();
    return reinterpret_cast<jlong>(holder);
}

JNIEXPORT void JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeDestroy(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle != 0) {
        auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);
        delete holder;
    }
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeLoadModel(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jstring filePath) {
    if (handle == 0 || filePath == nullptr) return JNI_FALSE;
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    const char* pathStr = env->GetStringUTFChars(filePath, nullptr);
    if (!pathStr) return JNI_FALSE;

    bool success = holder->loadModel(pathStr);
    env->ReleaseStringUTFChars(filePath, pathStr);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeLoadModelBuffer(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jobject byteBuffer) {
    if (handle == 0 || byteBuffer == nullptr) return JNI_FALSE;
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    void* bufferAddr = env->GetDirectBufferAddress(byteBuffer);
    jlong bufferCapacity = env->GetDirectBufferCapacity(byteBuffer);
    if (!bufferAddr || bufferCapacity < 64) return JNI_FALSE;

    bool success = holder->loadModelFromMemory(reinterpret_cast<const uint8_t*>(bufferAddr),
                                              static_cast<size_t>(bufferCapacity));
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeUnload(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle != 0) {
        auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);
        holder->unload();
    }
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeIsLoaded(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle == 0) return JNI_FALSE;
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);
    return holder->isLoaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeTokenize(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jstring text, jintArray outTokens, jint maxTokens) {
    if (handle == 0 || text == nullptr || outTokens == nullptr || maxTokens <= 0) return 0;
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    const char* utf8Chars = env->GetStringUTFChars(text, nullptr);
    if (!utf8Chars) return 0;
    jsize strLen = env->GetStringUTFLength(text);

    jint* tokenBuf = env->GetIntArrayElements(outTokens, nullptr);
    if (!tokenBuf) {
        env->ReleaseStringUTFChars(text, utf8Chars);
        return 0;
    }

    int count = holder->tokenize(utf8Chars, static_cast<size_t>(strLen),
                                reinterpret_cast<int32_t*>(tokenBuf), maxTokens);

    env->ReleaseIntArrayElements(outTokens, tokenBuf, 0);
    env->ReleaseStringUTFChars(text, utf8Chars);
    return count;
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeTokenizeTail(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jstring text, jintArray outTokens, jint maxTokens) {
    if (handle == 0 || text == nullptr || outTokens == nullptr || maxTokens <= 0) return 0;
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    const char* utf8Chars = env->GetStringUTFChars(text, nullptr);
    if (!utf8Chars) return 0;
    jsize strLen = env->GetStringUTFLength(text);

    jint* tokenBuf = env->GetIntArrayElements(outTokens, nullptr);
    if (!tokenBuf) {
        env->ReleaseStringUTFChars(text, utf8Chars);
        return 0;
    }

    int count = holder->tokenizeTail(utf8Chars, static_cast<size_t>(strLen),
                                    reinterpret_cast<int32_t*>(tokenBuf), maxTokens);

    env->ReleaseIntArrayElements(outTokens, tokenBuf, 0);
    env->ReleaseStringUTFChars(text, utf8Chars);
    return count;
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeForward(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jintArray contextTokens, jint numTokens, jfloatArray outHidden) {
    if (handle == 0 || contextTokens == nullptr || numTokens <= 0 || outHidden == nullptr) return JNI_FALSE;
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    jsize hiddenLen = env->GetArrayLength(outHidden);
    if (hiddenLen < holder->getModelDim()) return JNI_FALSE;

    jsize tokensLen = env->GetArrayLength(contextTokens);
    if (tokensLen < numTokens) return JNI_FALSE;

    jint* tokens = env->GetIntArrayElements(contextTokens, nullptr);
    jfloat* hidden = env->GetFloatArrayElements(outHidden, nullptr);
    if (!tokens || !hidden) {
        if (tokens) env->ReleaseIntArrayElements(contextTokens, tokens, JNI_ABORT);
        if (hidden) env->ReleaseFloatArrayElements(outHidden, hidden, JNI_ABORT);
        return JNI_FALSE;
    }

    bool success = holder->forward(reinterpret_cast<const int32_t*>(tokens), numTokens, hidden);

    env->ReleaseIntArrayElements(contextTokens, tokens, JNI_ABORT);
    env->ReleaseFloatArrayElements(outHidden, hidden, 0);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeScoreCandidates(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jfloatArray hT, jintArray candidateIds, jint numCandidates, jfloatArray outLogits) {
    if (handle == 0 || hT == nullptr || candidateIds == nullptr || outLogits == nullptr || numCandidates <= 0) return;
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    jsize hTLen = env->GetArrayLength(hT);
    jsize candLen = env->GetArrayLength(candidateIds);
    jsize logitsLen = env->GetArrayLength(outLogits);
    if (hTLen < holder->getModelDim() || candLen < numCandidates || logitsLen < numCandidates) return;

    jfloat* hTPtr = env->GetFloatArrayElements(hT, nullptr);
    jint* candPtr = env->GetIntArrayElements(candidateIds, nullptr);
    jfloat* logitsPtr = env->GetFloatArrayElements(outLogits, nullptr);

    if (hTPtr && candPtr && logitsPtr) {
        holder->scoreCandidates(hTPtr, reinterpret_cast<const int32_t*>(candPtr), numCandidates, logitsPtr);
    }

    if (hTPtr) env->ReleaseFloatArrayElements(hT, hTPtr, JNI_ABORT);
    if (candPtr) env->ReleaseIntArrayElements(candidateIds, candPtr, JNI_ABORT);
    if (logitsPtr) env->ReleaseFloatArrayElements(outLogits, logitsPtr, 0);
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeScoreTopK(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jfloatArray hT, jintArray candidateIds, jint numCandidates, jint k,
        jintArray outTopTokens, jfloatArray outTopScores) {
    if (handle == 0 || hT == nullptr || candidateIds == nullptr || outTopTokens == nullptr || outTopScores == nullptr || numCandidates <= 0 || k <= 0) {
        return 0;
    }
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    jsize hTLen = env->GetArrayLength(hT);
    jsize candLen = env->GetArrayLength(candidateIds);
    jsize topTokensLen = env->GetArrayLength(outTopTokens);
    jsize topScoresLen = env->GetArrayLength(outTopScores);
    if (hTLen < holder->getModelDim() || candLen < numCandidates || topTokensLen < k || topScoresLen < k) return 0;

    jfloat* hTPtr = env->GetFloatArrayElements(hT, nullptr);
    jint* candPtr = env->GetIntArrayElements(candidateIds, nullptr);
    jint* topTokensPtr = env->GetIntArrayElements(outTopTokens, nullptr);
    jfloat* topScoresPtr = env->GetFloatArrayElements(outTopScores, nullptr);

    int count = 0;
    if (hTPtr && candPtr && topTokensPtr && topScoresPtr) {
        count = holder->scoreTopK(hTPtr, reinterpret_cast<const int32_t*>(candPtr), numCandidates, k,
                                 reinterpret_cast<int32_t*>(topTokensPtr), topScoresPtr);
    }

    if (hTPtr) env->ReleaseFloatArrayElements(hT, hTPtr, JNI_ABORT);
    if (candPtr) env->ReleaseIntArrayElements(candidateIds, candPtr, JNI_ABORT);
    if (topTokensPtr) env->ReleaseIntArrayElements(outTopTokens, topTokensPtr, 0);
    if (topScoresPtr) env->ReleaseFloatArrayElements(outTopScores, topScoresPtr, 0);
    return count;
}

JNIEXPORT jintArray JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeGetWordStartTokenIds(
        JNIEnv* env, jobject /* thisObj */, jlong handle) {
    if (handle == 0) return env->NewIntArray(0);
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);

    const auto& starts = holder->getWordStartTokenIds();
    jintArray result = env->NewIntArray(static_cast<jsize>(starts.size()));
    if (result && !starts.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(starts.size()),
                               reinterpret_cast<const jint*>(starts.data()));
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeGetTokenText(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jint tokenId) {
    if (handle == 0) return env->NewStringUTF("");
    auto* holder = reinterpret_cast<UnifiedNeuralHolder*>(handle);
    std::string text = holder->getTokenText(tokenId);
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeGetVocabSize(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle == 0) return 0;
    return reinterpret_cast<UnifiedNeuralHolder*>(handle)->getVocabSize();
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeGetModelDim(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle == 0) return 0;
    return reinterpret_cast<UnifiedNeuralHolder*>(handle)->getModelDim();
}

} // extern "C"
