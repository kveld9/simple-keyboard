#include "micro_frontier_engine.h"
#include <fstream>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <queue>
#include <iostream>

#if defined(__aarch64__) || defined(__ARM_NEON)
#include <arm_neon.h>
#define USE_NEON 1

static inline float neon_reduce_add_f32(float32x4_t v) {
#if defined(__aarch64__)
    return vaddvq_f32(v);
#else
    float32x2_t s = vadd_f32(vget_low_f32(v), vget_high_f32(v));
    return vget_lane_f32(vpadd_f32(s, s), 0);
#endif
}
#elif defined(__x86_64__) || defined(_M_X64)
#include <immintrin.h>
#define USE_AVX 1
#endif


namespace micro_frontier {

MicroFrontierEngine::MicroFrontierEngine() = default;
MicroFrontierEngine::~MicroFrontierEngine() { unload(); }

void MicroFrontierEngine::unload() {
    mIsLoaded = false;
    mVocabSize = 0;
    mDModel = 0;
    mNHeads = 0;
    mNLayers = 0;
    mEmbLatent.clear();
    mProjW.clear();
    mLayers.clear();
    mStates.clear();
    mBpeVocab.clear();
    mBpeTrieRoot.reset();
    mWordStartTokenIds.clear();
}

float MicroFrontierEngine::sigmoid(float x) {
    return 1.0f / (1.0f + std::exp(-x));
}

void MicroFrontierEngine::rmsNorm(const float* in, const float* gamma, float* out, int dim) {
#if defined(USE_NEON)
    float32x4_t sum_v = vdupq_n_f32(0.0f);
    for (int i = 0; i < dim; i += 4) {
        float32x4_t v = vld1q_f32(in + i);
        sum_v = vmlaq_f32(sum_v, v, v);
    }
    float sum_sq = neon_reduce_add_f32(sum_v);
    float inv_rms = 1.0f / std::sqrt((sum_sq / dim) + 1e-6f);
    float32x4_t inv_v = vdupq_n_f32(inv_rms);
    for (int i = 0; i < dim; i += 4) {
        float32x4_t v = vld1q_f32(in + i);
        float32x4_t g = vld1q_f32(gamma + i);
        vst1q_f32(out + i, vmulq_f32(vmulq_f32(v, inv_v), g));
    }
#elif defined(USE_AVX)
    __m128 sum_v = _mm_setzero_ps();
    for (int i = 0; i < dim; i += 4) {
        __m128 v = _mm_loadu_ps(in + i);
        sum_v = _mm_add_ps(sum_v, _mm_mul_ps(v, v));
    }
    float s[4];
    _mm_storeu_ps(s, sum_v);
    float sum_sq = s[0] + s[1] + s[2] + s[3];
    float inv_rms = 1.0f / std::sqrt((sum_sq / dim) + 1e-6f);
    __m128 inv_v = _mm_set1_ps(inv_rms);
    for (int i = 0; i < dim; i += 4) {
        __m128 v = _mm_loadu_ps(in + i);
        __m128 g = _mm_loadu_ps(gamma + i);
        _mm_storeu_ps(out + i, _mm_mul_ps(_mm_mul_ps(v, inv_v), g));
    }
#else
    float sum_sq = 0.0f;
    for (int i = 0; i < dim; ++i) sum_sq += in[i] * in[i];
    float inv_rms = 1.0f / std::sqrt((sum_sq / dim) + 1e-6f);
    for (int i = 0; i < dim; ++i) out[i] = in[i] * inv_rms * gamma[i];
#endif
}

void MicroFrontierEngine::gelu(float* x, int dim) {
    for (int i = 0; i < dim; ++i) {
        float val = x[i];
        x[i] = 0.5f * val * (1.0f + std::erf(val * 0.70710678118f));
    }
}

#if defined(USE_NEON)
inline float neon_reduce_add(float32x4_t v) {
#if defined(__aarch64__)
    return vaddvq_f32(v);
#else
    float32x2_t sum2 = vadd_f32(vget_low_f32(v), vget_high_f32(v));
    return vget_lane_f32(vpadd_f32(sum2, sum2), 0);
#endif
}
#endif

// 4-Way Unrolled High-Performance NEON Matvec
void MicroFrontierEngine::matvecTernary(const TernaryMatrix& mat, const float* in, float* out) {
    const int8_t* W = reinterpret_cast<const int8_t*>(mat.packed_weights.data());
    int M = static_cast<int>(mat.rows);
    int K = static_cast<int>(mat.cols);
    float gamma = mat.gamma;

#if defined(USE_NEON)
    int j = 0;
    for (; j + 3 < M; j += 4) {
        const int8_t* w0 = W + j * K;
        const int8_t* w1 = W + (j + 1) * K;
        const int8_t* w2 = W + (j + 2) * K;
        const int8_t* w3 = W + (j + 3) * K;

        float32x4_t acc0 = vdupq_n_f32(0.0f);
        float32x4_t acc1 = vdupq_n_f32(0.0f);
        float32x4_t acc2 = vdupq_n_f32(0.0f);
        float32x4_t acc3 = vdupq_n_f32(0.0f);

        for (int k = 0; k < K; k += 8) {
            float32x4_t x0 = vld1q_f32(in + k);
            float32x4_t x1 = vld1q_f32(in + k + 4);

            int8x8_t wb0 = vld1_s8(w0 + k);
            int16x8_t w16_0 = vmovl_s8(wb0);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16_0))), x0);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16_0))), x1);

            int8x8_t wb1 = vld1_s8(w1 + k);
            int16x8_t w16_1 = vmovl_s8(wb1);
            acc1 = vmlaq_f32(acc1, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16_1))), x0);
            acc1 = vmlaq_f32(acc1, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16_1))), x1);

            int8x8_t wb2 = vld1_s8(w2 + k);
            int16x8_t w16_2 = vmovl_s8(wb2);
            acc2 = vmlaq_f32(acc2, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16_2))), x0);
            acc2 = vmlaq_f32(acc2, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16_2))), x1);

            int8x8_t wb3 = vld1_s8(w3 + k);
            int16x8_t w16_3 = vmovl_s8(wb3);
            acc3 = vmlaq_f32(acc3, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16_3))), x0);
            acc3 = vmlaq_f32(acc3, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16_3))), x1);
        }

        out[j + 0] = neon_reduce_add(acc0) * gamma;
        out[j + 1] = neon_reduce_add(acc1) * gamma;
        out[j + 2] = neon_reduce_add(acc2) * gamma;
        out[j + 3] = neon_reduce_add(acc3) * gamma;
    }
    for (; j < M; ++j) {
        const int8_t* w0 = W + j * K;
        float32x4_t acc = vdupq_n_f32(0.0f);
        for (int k = 0; k < K; k += 8) {
            float32x4_t x0 = vld1q_f32(in + k);
            float32x4_t x1 = vld1q_f32(in + k + 4);
            int8x8_t wb0 = vld1_s8(w0 + k);
            int16x8_t w16_0 = vmovl_s8(wb0);
            acc = vmlaq_f32(acc, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16_0))), x0);
            acc = vmlaq_f32(acc, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16_0))), x1);
        }
        out[j] = neon_reduce_add(acc) * gamma;
    }
#elif defined(USE_AVX)
    for (int j = 0; j < M; ++j) {
        const int8_t* row_w = W + j * K;
        __m128 acc = _mm_setzero_ps();
        for (int c = 0; c < K; c += 4) {
            __m128 v_in = _mm_loadu_ps(in + c);
            __m128 v_w = _mm_set_ps(static_cast<float>(row_w[c + 3]), static_cast<float>(row_w[c + 2]),
                                   static_cast<float>(row_w[c + 1]), static_cast<float>(row_w[c + 0]));
            acc = _mm_add_ps(acc, _mm_mul_ps(v_in, v_w));
        }
        float s[4];
        _mm_storeu_ps(s, acc);
        out[j] = (s[0] + s[1] + s[2] + s[3]) * gamma;
    }
#else
    for (int j = 0; j < M; ++j) {
        const int8_t* row_w = W + j * K;
        float sum = 0.0f;
        for (int c = 0; c < K; ++c) sum += in[c] * static_cast<float>(row_w[c]);
        out[j] = sum * gamma;
    }
#endif
}

void MicroFrontierEngine::resetRecurrentState() {
    mStates.resize(mNLayers);
    for (int l = 0; l < mNLayers; ++l) {
        mStates[l].state_A.assign(mNHeads * 8, 0.0f);
        mStates[l].state_B.assign(mNHeads * 4, 0.0f);
    }
}

void MicroFrontierEngine::buildBpeTrie() {
    mBpeTrieRoot = std::make_unique<TrieNode>();
    mWordStartTokenIds.clear();

    for (int32_t id = 0; id < mVocabSize; ++id) {
        if (id >= static_cast<int32_t>(mBpeVocab.size())) break;
        const std::string& piece = mBpeVocab[id];
        if (piece.empty()) continue;

        // Reemplazar \u2581 (0xE2 0x96 0x81) por ' '
        std::string clean;
        for (size_t i = 0; i < piece.size(); ) {
            if (i + 2 < piece.size() &&
                static_cast<uint8_t>(piece[i]) == 0xE2 &&
                static_cast<uint8_t>(piece[i+1]) == 0x96 &&
                static_cast<uint8_t>(piece[i+2]) == 0x81) {
                clean += ' ';
                i += 3;
                if (mWordStartTokenIds.empty() || mWordStartTokenIds.back() != id) {
                    mWordStartTokenIds.push_back(id);
                }
            } else {
                clean += static_cast<char>(std::tolower(static_cast<unsigned char>(piece[i])));
                i++;
            }
        }

        if (clean.empty()) continue;

        TrieNode* curr = mBpeTrieRoot.get();
        for (char c : clean) {
            curr = curr->getOrCreateChild(c);
        }
        curr->tokenId = id;
    }
}

bool MicroFrontierEngine::loadModel(const std::string& filePath) {
    std::ifstream f(filePath, std::ios::binary);
    if (!f.is_open()) return false;
    std::vector<uint8_t> buffer((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
    return loadModelFromMemory(buffer.data(), buffer.size());
}

bool MicroFrontierEngine::loadModelFromMemory(const uint8_t* data, size_t size) {
    if (size < 64) return false;
    const uint8_t* ptr = data;

    uint32_t magic = 0, version = 0;
    std::memcpy(&magic, ptr, 4);
    std::memcpy(&version, ptr + 4, 4);

    if (magic != MAGIC_TRF3 && magic != MAGIC_TRF3_ALT) return false;
    if (version != 3) return false;

    std::memcpy(&mVocabSize, ptr + 8, 4);
    std::memcpy(&mDModel, ptr + 12, 4);
    std::memcpy(&mNHeads, ptr + 16, 4);
    std::memcpy(&mNLayers, ptr + 20, 4);
    std::memcpy(&mDK, ptr + 24, 4);
    std::memcpy(&mDRank, ptr + 28, 4);
    std::memcpy(&mMaxSeqLen, ptr + 32, 4);

    uint32_t offBpe = 0, offEmb = 0, offProj = 0, offLayers = 0;
    std::memcpy(&offBpe, ptr + 36, 4);
    std::memcpy(&offEmb, ptr + 40, 4);
    std::memcpy(&offProj, ptr + 44, 4);
    std::memcpy(&offLayers, ptr + 48, 4);

    ptr = data + 64;

    if (offBpe > 0 && offBpe < size) {
        const uint8_t* bpePtr = data + offBpe;
        uint32_t bpeCount = 0;
        std::memcpy(&bpeCount, bpePtr, 4); bpePtr += 4;
        std::vector<uint32_t> offsets(bpeCount);
        std::memcpy(offsets.data(), bpePtr, bpeCount * 4); bpePtr += bpeCount * 4;
        const char* strPool = reinterpret_cast<const char*>(bpePtr);

        mBpeVocab.resize(bpeCount);
        for (uint32_t i = 0; i < bpeCount; ++i) {
            mBpeVocab[i] = std::string(strPool + offsets[i]);
        }
        buildBpeTrie();
    }

    if (offEmb > 0 && offEmb < size) ptr = data + offEmb;

    std::memcpy(&mEmbScale, ptr, sizeof(float)); ptr += sizeof(float);
    size_t embBytes = mVocabSize * mDRank;
    mEmbLatent.resize(embBytes);
    std::memcpy(mEmbLatent.data(), ptr, embBytes); ptr += embBytes;

    if (offProj > 0 && offProj < size) ptr = data + offProj;

    size_t projFloats = mDModel * mDRank;
    mProjW.resize(projFloats);
    std::memcpy(mProjW.data(), ptr, projFloats * sizeof(float)); ptr += projFloats * sizeof(float);

    if (offLayers > 0 && offLayers < size) ptr = data + offLayers;

    mLayers.resize(mNLayers);
    auto read_and_unpack_ternary = [&](TernaryMatrix& mat, uint32_t rows, uint32_t cols) {
        mat.rows = rows; mat.cols = cols;
        std::memcpy(&mat.gamma, ptr, 4); ptr += 4;
        size_t n_weights = rows * cols;
        size_t n_packed_bytes = (n_weights + 3) / 4;
        mat.packed_weights.resize(n_weights);
        int8_t* dest = reinterpret_cast<int8_t*>(mat.packed_weights.data());

        size_t w_idx = 0;
        for (size_t b = 0; b < n_packed_bytes; ++b) {
            uint8_t byte_val = *ptr++;
            for (int bit = 0; bit < 4; ++bit) {
                if (w_idx >= n_weights) break;
                uint8_t code = (byte_val >> (bit * 2)) & 3;
                dest[w_idx++] = (code == 1) ? 1 : ((code == 3) ? -1 : 0);
            }
        }
    };

    for (int l = 0; l < mNLayers; ++l) {
        if (offLayers > 0) {
            uintptr_t cur_off = ptr - data;
            uintptr_t aligned_off = (cur_off + 63) & ~63ULL;
            ptr = data + aligned_off;
        }
        auto& lay = mLayers[l];
        lay.norm1_gamma.resize(mDModel);
        std::memcpy(lay.norm1_gamma.data(), ptr, mDModel * 4); ptr += mDModel * 4;

        lay.decay_A.resize(mNHeads * 64);
        std::memcpy(lay.decay_A.data(), ptr, mNHeads * 64 * 4); ptr += mNHeads * 64 * 4;
        lay.decay_B.resize(mNHeads * 16);
        std::memcpy(lay.decay_B.data(), ptr, mNHeads * 16 * 4); ptr += mNHeads * 16 * 4;

        lay.decay_A_h.resize(mNHeads);
        for (int h = 0; h < mNHeads; ++h) {
            float sumA = 0.0f;
            for (int k = 0; k < 64; ++k) sumA += lay.decay_A[h * 64 + k];
            lay.decay_A_h[h] = sumA / 64.0f;
        }

        lay.decay_B_h.resize(mNHeads);
        for (int h = 0; h < mNHeads; ++h) {
            float sumB = 0.0f;
            for (int k = 0; k < 16; ++k) sumB += lay.decay_B[h * 16 + k];
            lay.decay_B_h[h] = sumB / 16.0f;
        }

        read_and_unpack_ternary(lay.k_proj, mDModel, mDModel);
        read_and_unpack_ternary(lay.v_proj, mDModel, mDModel);
        read_and_unpack_ternary(lay.r_proj, mDModel, mDModel);
        read_and_unpack_ternary(lay.out_proj, mDModel, mDModel);

        lay.norm2_gamma.resize(mDModel);
        std::memcpy(lay.norm2_gamma.data(), ptr, mDModel * 4); ptr += mDModel * 4;

        read_and_unpack_ternary(lay.mlp_in, mDModel * 4, mDModel);
        read_and_unpack_ternary(lay.mlp_out, mDModel, mDModel * 4);
    }

    mFinalNormGamma.resize(mDModel);
    std::memcpy(mFinalNormGamma.data(), ptr, mDModel * 4);

    mX.resize(mDModel);
    mNormBuf.resize(mDModel);
    mK.resize(mDModel); mV.resize(mDModel); mR.resize(mDModel); mRecOut.resize(mDModel);
    mMlpInBuf.resize(mDModel * 4); mMlpOutBuf.resize(mDModel);
    mHLatent.resize(mDRank);

    resetRecurrentState();
    mIsLoaded = true;
    return true;
}

int MicroFrontierEngine::tokenize(const char* text, size_t textLen, int32_t* outTokens, int maxTokens) {
    if (!mIsLoaded || !mBpeTrieRoot || text == nullptr || textLen == 0 || maxTokens <= 0) return 0;
    
    // Normalizar texto y agregar espacio virtual inicial si no empieza con espacio
    std::string normText;
    bool virtualSpace = (text[0] != ' ' && static_cast<uint8_t>(text[0]) != 0xE2);
    if (virtualSpace) normText += ' ';

    for (size_t i = 0; i < textLen; ++i) {
        normText += static_cast<char>(std::tolower(static_cast<unsigned char>(text[i])));
    }

    int tokenCount = 0;
    size_t pos = 0;
    size_t len = normText.size();

    while (pos < len && tokenCount < maxTokens) {
        const TrieNode* curr = mBpeTrieRoot.get();
        int32_t bestTokenId = -1;
        size_t bestMatchLen = 0;
        size_t matchLen = 0;

        while (pos + matchLen < len) {
            char c = normText[pos + matchLen];
            curr = curr->getChild(c);
            if (!curr) break;
            matchLen++;
            if (curr->tokenId >= 0) {
                bestTokenId = curr->tokenId;
                bestMatchLen = matchLen;
            }
        }

        if (bestTokenId >= 0 && bestMatchLen > 0) {
            outTokens[tokenCount++] = bestTokenId;
            pos += bestMatchLen;
        } else {
            outTokens[tokenCount++] = UNK_TOKEN_ID;
            pos++;
        }
    }
    return tokenCount;
}

int MicroFrontierEngine::tokenizeTail(const char* text, size_t textLen, int32_t* outTokens, int maxTokens) {
    std::vector<int32_t> allTokens(std::max(128, maxTokens * 2));
    int total = tokenize(text, textLen, allTokens.data(), static_cast<int>(allTokens.size()));
    if (total <= 0) return 0;

    int count = std::min(total, maxTokens);
    int start = total - count;
    for (int i = 0; i < count; ++i) {
        outTokens[i] = allTokens[start + i];
    }
    return count;
}

bool MicroFrontierEngine::forwardStep(int32_t tokenId, float* outHidden) {
    if (!mIsLoaded || outHidden == nullptr) return false;
    if (tokenId < 0 || tokenId >= mVocabSize) tokenId = UNK_TOKEN_ID;

    // 1. SVD Embedding Lookup
    const int8_t* emb_row = &mEmbLatent[tokenId * mDRank];
    for (int r = 0; r < mDRank; ++r) {
        mHLatent[r] = static_cast<float>(emb_row[r]) * mEmbScale;
    }

    // 2. Project SVD to D_Model
    for (int i = 0; i < mDModel; ++i) {
        float sum = 0.0f;
        const float* proj_row = &mProjW[i * mDRank];
        for (int r = 0; r < mDRank; ++r) sum += proj_row[r] * mHLatent[r];
        mX[i] = sum;
    }

    // 3. Layer Forward
    for (int l = 0; l < mNLayers; ++l) {
        const auto& lay = mLayers[l];
        auto& st = mStates[l];

        // Norm1
        rmsNorm(mX.data(), lay.norm1_gamma.data(), mNormBuf.data(), mDModel);

        // Projections (SIMD)
        matvecTernary(lay.k_proj, mNormBuf.data(), mK.data());
        matvecTernary(lay.v_proj, mNormBuf.data(), mV.data());
        matvecTernary(lay.r_proj, mNormBuf.data(), mR.data());
        for (int i = 0; i < mDModel; ++i) mR[i] = sigmoid(mR[i]);

        // K-Cheb Recurrent Step exact PyTorch
        for (int h = 0; h < mNHeads; ++h) {
            int off = h * mDK;
            float* sA = &st.state_A[h * 8];
            float* sB = &st.state_B[h * 4];
            float decA = lay.decay_A_h[h];
            float decB = lay.decay_B_h[h];

            for (int i = 0; i < 8; ++i) {
                sA[i] = sA[i] * decA + mK[off + i] * mV[off + i];
            }
            for (int i = 0; i < 4; ++i) {
                sB[i] = sB[i] * decB + mK[off + 8 + i] * mV[off + 8 + i];
            }

            for (int d = 0; d < mDK; ++d) {
                float kron_val = sA[d % 8] + sB[d % 4];
                float x_cheb = mK[off + d] * mV[off + d];
                float cheb_out = 2.0f * (x_cheb * x_cheb) - 1.0f;
                mNormBuf[off + d] = (kron_val + cheb_out) * mR[off + d];
            }
        }

        matvecTernary(lay.out_proj, mNormBuf.data(), mRecOut.data());
        for (int i = 0; i < mDModel; ++i) mX[i] += mRecOut[i];

        rmsNorm(mX.data(), lay.norm2_gamma.data(), mNormBuf.data(), mDModel);
        matvecTernary(lay.mlp_in, mNormBuf.data(), mMlpInBuf.data());
        gelu(mMlpInBuf.data(), mDModel * 4);
        matvecTernary(lay.mlp_out, mMlpInBuf.data(), mMlpOutBuf.data());
        for (int i = 0; i < mDModel; ++i) mX[i] += mMlpOutBuf[i];
    }

    rmsNorm(mX.data(), mFinalNormGamma.data(), outHidden, mDModel);
    return true;
}

bool MicroFrontierEngine::forward(const int32_t* contextTokens, int numTokens, float* outHidden) {
    if (!mIsLoaded || contextTokens == nullptr || numTokens <= 0 || outHidden == nullptr) return false;

    resetRecurrentState();

    for (int t = 0; t < numTokens; ++t) {
        forwardStep(contextTokens[t], outHidden);
    }
    return true;
}

void MicroFrontierEngine::scoreCandidates(const float* hT, const int32_t* candidateIds, int numCandidates, float* outLogits) {
    if (!mIsLoaded || hT == nullptr || candidateIds == nullptr || outLogits == nullptr || numCandidates <= 0) return;

    // 1. Project hT to SVD Latent Space
    for (int r = 0; r < mDRank; ++r) {
        float sum = 0.0f;
        for (int i = 0; i < mDModel; ++i) sum += mProjW[i * mDRank + r] * hT[i];
        mHLatent[r] = sum * mEmbScale;
    }

    // 2. Score Candidates using fast SVD dot product
    for (int c = 0; c < numCandidates; ++c) {
        int32_t id = candidateIds[c];
        if (id < 0 || id >= mVocabSize) {
            outLogits[c] = -1e9f;
            continue;
        }
        const int8_t* emb_row = &mEmbLatent[id * mDRank];
        float dot = 0.0f;
        for (int r = 0; r < mDRank; ++r) {
            dot += static_cast<float>(emb_row[r]) * mHLatent[r];
        }
        outLogits[c] = dot;
    }
}

int MicroFrontierEngine::scoreTopK(const float* hT, const int32_t* candidateIds, int numCandidates, int k,
                                   int32_t* outTopTokens, float* outTopScores) {
    if (!mIsLoaded || numCandidates <= 0 || k <= 0) return 0;
    std::vector<float> logits(numCandidates);
    scoreCandidates(hT, candidateIds, numCandidates, logits.data());

    using Pair = std::pair<float, int32_t>;
    std::priority_queue<Pair, std::vector<Pair>, std::greater<Pair>> minHeap;

    for (int i = 0; i < numCandidates; ++i) {
        float s = logits[i];
        int32_t tok = candidateIds[i];
        if (static_cast<int>(minHeap.size()) < k) {
            minHeap.push({s, tok});
        } else if (s > minHeap.top().first) {
            minHeap.pop();
            minHeap.push({s, tok});
        }
    }

    int count = static_cast<int>(minHeap.size());
    for (int i = count - 1; i >= 0; --i) {
        outTopScores[i] = minHeap.top().first;
        outTopTokens[i] = minHeap.top().second;
        minHeap.pop();
    }
    return count;
}

std::string MicroFrontierEngine::getTokenText(int32_t tokenId) const {
    if (tokenId >= 0 && tokenId < static_cast<int32_t>(mBpeVocab.size())) {
        return mBpeVocab[tokenId];
    }
    return "";
}

} // namespace micro_frontier
