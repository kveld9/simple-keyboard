#include <jni.h>
#include <string>
#include <vector>
#include "micro_transformer.h"

using namespace micro_transformer;

extern "C" {

JNIEXPORT jlong JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeCreate(
        JNIEnv* /* env */, jobject /* thisObj */) {
    auto* model = new (std::nothrow) MicroTransformerModelCpp();
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT void JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeDestroy(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle != 0) {
        auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);
        delete model;
    }
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeLoadModel(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jstring filePath) {
    if (handle == 0 || filePath == nullptr) return JNI_FALSE;
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    const char* pathStr = env->GetStringUTFChars(filePath, nullptr);
    if (!pathStr) return JNI_FALSE;

    bool success = model->loadModel(pathStr);
    env->ReleaseStringUTFChars(filePath, pathStr);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeLoadModelBuffer(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jobject byteBuffer) {
    if (handle == 0 || byteBuffer == nullptr) return JNI_FALSE;
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    void* bufferAddr = env->GetDirectBufferAddress(byteBuffer);
    jlong bufferCapacity = env->GetDirectBufferCapacity(byteBuffer);
    if (!bufferAddr || bufferCapacity < 64) return JNI_FALSE;

    bool success = model->loadModelFromMemory(reinterpret_cast<const uint8_t*>(bufferAddr),
                                              static_cast<size_t>(bufferCapacity));
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeUnload(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle != 0) {
        auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);
        model->unload();
    }
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeIsLoaded(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle == 0) return JNI_FALSE;
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);
    return model->isLoaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeTokenize(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jstring text, jintArray outTokens, jint maxTokens) {
    if (handle == 0 || text == nullptr || outTokens == nullptr || maxTokens <= 0) return 0;
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    const char* utf8Chars = env->GetStringUTFChars(text, nullptr);
    if (!utf8Chars) return 0;
    jsize strLen = env->GetStringUTFLength(text);

    jint* tokenBuf = env->GetIntArrayElements(outTokens, nullptr);
    if (!tokenBuf) {
        env->ReleaseStringUTFChars(text, utf8Chars);
        return 0;
    }

    int count = model->tokenize(utf8Chars, static_cast<size_t>(strLen),
                                reinterpret_cast<int32_t*>(tokenBuf), maxTokens);

    env->ReleaseIntArrayElements(outTokens, tokenBuf, 0);
    env->ReleaseStringUTFChars(text, utf8Chars);
    return count;
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeTokenizeTail(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jstring text, jintArray outTokens, jint maxTokens) {
    if (handle == 0 || text == nullptr || outTokens == nullptr || maxTokens <= 0) return 0;
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    const char* utf8Chars = env->GetStringUTFChars(text, nullptr);
    if (!utf8Chars) return 0;
    jsize strLen = env->GetStringUTFLength(text);

    jint* tokenBuf = env->GetIntArrayElements(outTokens, nullptr);
    if (!tokenBuf) {
        env->ReleaseStringUTFChars(text, utf8Chars);
        return 0;
    }

    int count = model->tokenizeTail(utf8Chars, static_cast<size_t>(strLen),
                                    reinterpret_cast<int32_t*>(tokenBuf), maxTokens);

    env->ReleaseIntArrayElements(outTokens, tokenBuf, 0);
    env->ReleaseStringUTFChars(text, utf8Chars);
    return count;
}

JNIEXPORT jboolean JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeForward(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jintArray contextTokens, jint numTokens, jfloatArray outHidden) {
    if (handle == 0 || contextTokens == nullptr || numTokens <= 0 || outHidden == nullptr) return JNI_FALSE;
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    jsize hiddenLen = env->GetArrayLength(outHidden);
    if (hiddenLen < model->getModelDim()) return JNI_FALSE;

    jsize tokensLen = env->GetArrayLength(contextTokens);
    if (tokensLen < numTokens) return JNI_FALSE;

    jint* tokens = env->GetIntArrayElements(contextTokens, nullptr);
    jfloat* hidden = env->GetFloatArrayElements(outHidden, nullptr);
    if (!tokens || !hidden) {
        if (tokens) env->ReleaseIntArrayElements(contextTokens, tokens, JNI_ABORT);
        if (hidden) env->ReleaseFloatArrayElements(outHidden, hidden, JNI_ABORT);
        return JNI_FALSE;
    }

    bool success = model->forward(reinterpret_cast<const int32_t*>(tokens), numTokens, hidden);

    env->ReleaseIntArrayElements(contextTokens, tokens, JNI_ABORT);
    env->ReleaseFloatArrayElements(outHidden, hidden, 0);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeScoreCandidates(
        JNIEnv* env, jobject /* thisObj */, jlong handle, jfloatArray hT, jintArray candidateIds, jint numCandidates, jfloatArray outLogits) {
    if (handle == 0 || hT == nullptr || candidateIds == nullptr || outLogits == nullptr || numCandidates <= 0) return;
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    jsize hTLen = env->GetArrayLength(hT);
    jsize candLen = env->GetArrayLength(candidateIds);
    jsize logitsLen = env->GetArrayLength(outLogits);
    if (hTLen < model->getModelDim() || candLen < numCandidates || logitsLen < numCandidates) return;

    jfloat* hTPtr = env->GetFloatArrayElements(hT, nullptr);
    jint* candPtr = env->GetIntArrayElements(candidateIds, nullptr);
    jfloat* logitsPtr = env->GetFloatArrayElements(outLogits, nullptr);

    if (hTPtr && candPtr && logitsPtr) {
        model->scoreCandidates(hTPtr, reinterpret_cast<const int32_t*>(candPtr), numCandidates, logitsPtr);
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
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    jsize hTLen = env->GetArrayLength(hT);
    jsize candLen = env->GetArrayLength(candidateIds);
    jsize topTokensLen = env->GetArrayLength(outTopTokens);
    jsize topScoresLen = env->GetArrayLength(outTopScores);
    if (hTLen < model->getModelDim() || candLen < numCandidates || topTokensLen < k || topScoresLen < k) return 0;

    jfloat* hTPtr = env->GetFloatArrayElements(hT, nullptr);
    jint* candPtr = env->GetIntArrayElements(candidateIds, nullptr);
    jint* topTokensPtr = env->GetIntArrayElements(outTopTokens, nullptr);
    jfloat* topScoresPtr = env->GetFloatArrayElements(outTopScores, nullptr);

    int count = 0;
    if (hTPtr && candPtr && topTokensPtr && topScoresPtr) {
        count = model->scoreTopK(hTPtr, reinterpret_cast<const int32_t*>(candPtr), numCandidates, k,
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
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);

    const auto& starts = model->getWordStartTokenIds();
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
    auto* model = reinterpret_cast<MicroTransformerModelCpp*>(handle);
    std::string text = model->getTokenText(tokenId);
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeGetVocabSize(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle == 0) return 0;
    return reinterpret_cast<MicroTransformerModelCpp*>(handle)->getVocabSize();
}

JNIEXPORT jint JNICALL
Java_rkr_simplekeyboard_inputmethod_latin_dict_neural_MicroTransformerModel_nativeGetModelDim(
        JNIEnv* /* env */, jobject /* thisObj */, jlong handle) {
    if (handle == 0) return 0;
    return reinterpret_cast<MicroTransformerModelCpp*>(handle)->getModelDim();
}

} // extern "C"
