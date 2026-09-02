#include "micro_transformer.h"

#include <fstream>
#include <iostream>
#include <cmath>
#include <queue>
#include <sstream>
#include <cctype>

#if defined(__ARM_NEON) || defined(__aarch64__)
#include <arm_neon.h>
#define USE_NEON 1
#elif defined(__SSE4_1__) || defined(__x86_64__) || defined(_M_X64)
#include <immintrin.h>
#define USE_SSE 1
#endif

namespace micro_transformer {

namespace {

inline std::string normalizeTextUtf8(const char* text, size_t len) {
    std::string norm;
    norm.reserve(len + 2);
    bool lastWasSpace = true;
    size_t i = 0;
    while (i < len) {
        uint8_t c0 = static_cast<uint8_t>(text[i]);
        if (c0 < 0x80) { // ASCII
            if ((c0 >= '0' && c0 <= '9') || (c0 >= 'a' && c0 <= 'z')) {
                norm.push_back(static_cast<char>(c0));
                lastWasSpace = false;
                i++;
            } else if (c0 >= 'A' && c0 <= 'Z') {
                norm.push_back(static_cast<char>(c0 + 32));
                lastWasSpace = false;
                i++;
            } else {
                if (!lastWasSpace) {
                    norm.push_back(' ');
                    lastWasSpace = true;
                }
                i++;
            }
        } else if ((c0 & 0xE0) == 0xC0 && i + 1 < len) { // 2-byte UTF-8
            uint8_t c1 = static_cast<uint8_t>(text[i + 1]);
            // Lowercase Spanish/Latin uppercase accented characters
            if (c0 == 0xC3 && c1 >= 0x80 && c1 <= 0x9E) {
                c1 += 0x20;
            }
            norm.push_back(static_cast<char>(c0));
            norm.push_back(static_cast<char>(c1));
            lastWasSpace = false;
            i += 2;
        } else if ((c0 & 0xF0) == 0xE0 && i + 2 < len) { // 3-byte UTF-8
            uint8_t c1 = static_cast<uint8_t>(text[i + 1]);
            uint8_t c2 = static_cast<uint8_t>(text[i + 2]);
            if (c0 == 0xE2 && c1 == 0x96 && c2 == 0x81) { // \u2581 WORD_START_CHAR
                if (!lastWasSpace) {
                    norm.push_back(' ');
                    lastWasSpace = true;
                }
            } else {
                norm.push_back(static_cast<char>(c0));
                norm.push_back(static_cast<char>(c1));
                norm.push_back(static_cast<char>(c2));
                lastWasSpace = false;
            }
            i += 3;
        } else {
            i++;
        }
    }
    while (!norm.empty() && norm.back() == ' ') {
        norm.pop_back();
    }
    return norm;
}

// Optimized 4-row blocked Ternary Matrix-Vector multiplication: y = W * x
void matvec_ternary(const int8_t* __restrict__ W, const float* __restrict__ x, float* __restrict__ y, int M, int K) {
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
            float32x4_t x0 = vld1q_f32(x + k);
            float32x4_t x1 = vld1q_f32(x + k + 4);

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
#if defined(__aarch64__)
        y[j]     = vaddvq_f32(acc0);
        y[j + 1] = vaddvq_f32(acc1);
        y[j + 2] = vaddvq_f32(acc2);
        y[j + 3] = vaddvq_f32(acc3);
#else
        auto hsum_neon = [](float32x4_t v) -> float {
            float32x2_t r = vadd_f32(vget_low_f32(v), vget_high_f32(v));
            return vget_lane_f32(vpadd_f32(r, r), 0);
        };
        y[j]     = hsum_neon(acc0);
        y[j + 1] = hsum_neon(acc1);
        y[j + 2] = hsum_neon(acc2);
        y[j + 3] = hsum_neon(acc3);
#endif
    }
    for (; j < M; ++j) {
        const int8_t* w = W + j * K;
        float32x4_t acc0 = vdupq_n_f32(0.0f);
        for (int k = 0; k < K; k += 8) {
            float32x4_t x0 = vld1q_f32(x + k);
            float32x4_t x1 = vld1q_f32(x + k + 4);
            int8x8_t wb = vld1_s8(w + k);
            int16x8_t w16 = vmovl_s8(wb);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16))), x0);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16))), x1);
        }
#if defined(__aarch64__)
        y[j] = vaddvq_f32(acc0);
#else
        float32x2_t r = vadd_f32(vget_low_f32(acc0), vget_high_f32(acc0));
        y[j] = vget_lane_f32(vpadd_f32(r, r), 0);
#endif
    }
#elif defined(USE_SSE)
    int j = 0;
    for (; j + 3 < M; j += 4) {
        const int8_t* w0 = W + j * K;
        const int8_t* w1 = W + (j + 1) * K;
        const int8_t* w2 = W + (j + 2) * K;
        const int8_t* w3 = W + (j + 3) * K;

        __m128 acc0 = _mm_setzero_ps();
        __m128 acc1 = _mm_setzero_ps();
        __m128 acc2 = _mm_setzero_ps();
        __m128 acc3 = _mm_setzero_ps();

        for (int k = 0; k < K; k += 8) {
            __m128 vx0 = _mm_loadu_ps(x + k);
            __m128 vx1 = _mm_loadu_ps(x + k + 4);

            __m128i r0 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w0 + k));
            __m128 f0_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r0));
            __m128 f0_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r0, 4)));
            acc0 = _mm_add_ps(acc0, _mm_add_ps(_mm_mul_ps(f0_0, vx0), _mm_mul_ps(f0_1, vx1)));

            __m128i r1 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w1 + k));
            __m128 f1_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r1));
            __m128 f1_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r1, 4)));
            acc1 = _mm_add_ps(acc1, _mm_add_ps(_mm_mul_ps(f1_0, vx0), _mm_mul_ps(f1_1, vx1)));

            __m128i r2 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w2 + k));
            __m128 f2_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r2));
            __m128 f2_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r2, 4)));
            acc2 = _mm_add_ps(acc2, _mm_add_ps(_mm_mul_ps(f2_0, vx0), _mm_mul_ps(f2_1, vx1)));

            __m128i r3 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w3 + k));
            __m128 f3_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r3));
            __m128 f3_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r3, 4)));
            acc3 = _mm_add_ps(acc3, _mm_add_ps(_mm_mul_ps(f3_0, vx0), _mm_mul_ps(f3_1, vx1)));
        }

        auto hsum128 = [](__m128 v) -> float {
            v = _mm_hadd_ps(v, v);
            v = _mm_hadd_ps(v, v);
            return _mm_cvtss_f32(v);
        };

        y[j]     = hsum128(acc0);
        y[j + 1] = hsum128(acc1);
        y[j + 2] = hsum128(acc2);
        y[j + 3] = hsum128(acc3);
    }
    for (; j < M; ++j) {
        const int8_t* w = W + j * K;
        __m128 acc0 = _mm_setzero_ps();
        for (int k = 0; k < K; k += 8) {
            __m128 vx0 = _mm_loadu_ps(x + k);
            __m128 vx1 = _mm_loadu_ps(x + k + 4);
            __m128i raw = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w + k));
            __m128 f0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(raw));
            __m128 f1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(raw, 4)));
            acc0 = _mm_add_ps(acc0, _mm_add_ps(_mm_mul_ps(f0, vx0), _mm_mul_ps(f1, vx1)));
        }
        acc0 = _mm_hadd_ps(acc0, acc0);
        acc0 = _mm_hadd_ps(acc0, acc0);
        y[j] = _mm_cvtss_f32(acc0);
    }
#else
    for (int j = 0; j < M; ++j) {
        const int8_t* w = W + j * K;
        float s0 = 0.0f, s1 = 0.0f, s2 = 0.0f, s3 = 0.0f;
        for (int k = 0; k < K; k += 4) {
            s0 += w[k] * x[k];
            s1 += w[k + 1] * x[k + 1];
            s2 += w[k + 2] * x[k + 2];
            s3 += w[k + 3] * x[k + 3];
        }
        y[j] = (s0 + s1) + (s2 + s3);
    }
#endif
}

// Fast 4-row blocked Ternary Matrix-Vector multiplication with scale and residual add: y += scale * (W * x)
void matvec_ternary_accum(const int8_t* __restrict__ W, const float* __restrict__ x, float* __restrict__ y,
                          float scale, int M, int K) {
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
            float32x4_t x0 = vld1q_f32(x + k);
            float32x4_t x1 = vld1q_f32(x + k + 4);

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
#if defined(__aarch64__)
        y[j]     += vaddvq_f32(acc0) * scale;
        y[j + 1] += vaddvq_f32(acc1) * scale;
        y[j + 2] += vaddvq_f32(acc2) * scale;
        y[j + 3] += vaddvq_f32(acc3) * scale;
#else
        auto hsum_neon = [](float32x4_t v) -> float {
            float32x2_t r = vadd_f32(vget_low_f32(v), vget_high_f32(v));
            return vget_lane_f32(vpadd_f32(r, r), 0);
        };
        y[j]     += hsum_neon(acc0) * scale;
        y[j + 1] += hsum_neon(acc1) * scale;
        y[j + 2] += hsum_neon(acc2) * scale;
        y[j + 3] += hsum_neon(acc3) * scale;
#endif
    }
    for (; j < M; ++j) {
        const int8_t* w = W + j * K;
        float32x4_t acc0 = vdupq_n_f32(0.0f);
        for (int k = 0; k < K; k += 8) {
            float32x4_t x0 = vld1q_f32(x + k);
            float32x4_t x1 = vld1q_f32(x + k + 4);
            int8x8_t wb = vld1_s8(w + k);
            int16x8_t w16 = vmovl_s8(wb);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16))), x0);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16))), x1);
        }
#if defined(__aarch64__)
        y[j] += vaddvq_f32(acc0) * scale;
#else
        float32x2_t r = vadd_f32(vget_low_f32(acc0), vget_high_f32(acc0));
        y[j] += vget_lane_f32(vpadd_f32(r, r), 0) * scale;
#endif
    }
#elif defined(USE_SSE)
    int j = 0;
    for (; j + 3 < M; j += 4) {
        const int8_t* w0 = W + j * K;
        const int8_t* w1 = W + (j + 1) * K;
        const int8_t* w2 = W + (j + 2) * K;
        const int8_t* w3 = W + (j + 3) * K;

        __m128 acc0 = _mm_setzero_ps();
        __m128 acc1 = _mm_setzero_ps();
        __m128 acc2 = _mm_setzero_ps();
        __m128 acc3 = _mm_setzero_ps();

        for (int k = 0; k < K; k += 8) {
            __m128 vx0 = _mm_loadu_ps(x + k);
            __m128 vx1 = _mm_loadu_ps(x + k + 4);

            __m128i r0 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w0 + k));
            __m128 f0_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r0));
            __m128 f0_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r0, 4)));
            acc0 = _mm_add_ps(acc0, _mm_add_ps(_mm_mul_ps(f0_0, vx0), _mm_mul_ps(f0_1, vx1)));

            __m128i r1 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w1 + k));
            __m128 f1_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r1));
            __m128 f1_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r1, 4)));
            acc1 = _mm_add_ps(acc1, _mm_add_ps(_mm_mul_ps(f1_0, vx0), _mm_mul_ps(f1_1, vx1)));

            __m128i r2 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w2 + k));
            __m128 f2_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r2));
            __m128 f2_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r2, 4)));
            acc2 = _mm_add_ps(acc2, _mm_add_ps(_mm_mul_ps(f2_0, vx0), _mm_mul_ps(f2_1, vx1)));

            __m128i r3 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w3 + k));
            __m128 f3_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r3));
            __m128 f3_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r3, 4)));
            acc3 = _mm_add_ps(acc3, _mm_add_ps(_mm_mul_ps(f3_0, vx0), _mm_mul_ps(f3_1, vx1)));
        }

        auto hsum128 = [](__m128 v) -> float {
            v = _mm_hadd_ps(v, v);
            v = _mm_hadd_ps(v, v);
            return _mm_cvtss_f32(v);
        };

        y[j]     += hsum128(acc0) * scale;
        y[j + 1] += hsum128(acc1) * scale;
        y[j + 2] += hsum128(acc2) * scale;
        y[j + 3] += hsum128(acc3) * scale;
    }
    for (; j < M; ++j) {
        const int8_t* w = W + j * K;
        __m128 acc0 = _mm_setzero_ps();
        for (int k = 0; k < K; k += 8) {
            __m128 vx0 = _mm_loadu_ps(x + k);
            __m128 vx1 = _mm_loadu_ps(x + k + 4);
            __m128i raw = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(w + k));
            __m128 f0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(raw));
            __m128 f1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(raw, 4)));
            acc0 = _mm_add_ps(acc0, _mm_add_ps(_mm_mul_ps(f0, vx0), _mm_mul_ps(f1, vx1)));
        }
        acc0 = _mm_hadd_ps(acc0, acc0);
        acc0 = _mm_hadd_ps(acc0, acc0);
        y[j] += _mm_cvtss_f32(acc0) * scale;
    }
#else
    for (int j = 0; j < M; ++j) {
        const int8_t* w = W + j * K;
        float s0 = 0.0f, s1 = 0.0f, s2 = 0.0f, s3 = 0.0f;
        for (int k = 0; k < K; k += 4) {
            s0 += w[k] * x[k];
            s1 += w[k + 1] * x[k + 1];
            s2 += w[k + 2] * x[k + 2];
            s3 += w[k + 3] * x[k + 3];
        }
        y[j] += ((s0 + s1) + (s2 + s3)) * scale;
    }
#endif
}

} // anonymous namespace

MicroTransformerModelCpp::MicroTransformerModelCpp() = default;
MicroTransformerModelCpp::~MicroTransformerModelCpp() {
    unload();
}

static void clearTrieIterative(std::unique_ptr<TrieNode>& root) {
    if (!root) return;
    std::vector<std::unique_ptr<TrieNode>> stack;
    stack.push_back(std::move(root));
    while (!stack.empty()) {
        auto curr = std::move(stack.back());
        stack.pop_back();
        if (curr) {
            for (auto& child : curr->childNodes) {
                if (child) {
                    stack.push_back(std::move(child));
                }
            }
            curr->childNodes.clear();
        }
    }
}

void MicroTransformerModelCpp::unload() {
    mIsLoaded = false;
    mVocabSize = 0;
    mDModel = 0;
    mDFf = 0;
    mNHeads = 0;
    mNLayers = 0;

    mEmbeddings.clear();
    mPosEmb.clear();
    mQkvW.clear();
    mProjW.clear();
    mMlpUpW.clear();
    mMlpDownW.clear();
    mGamma1Fused.clear();
    mGamma2Fused.clear();
    mScaleProj.clear();
    mScaleDown.clear();

    mBpeVocab.clear();
    mIsBadCandidateTable.clear();
    clearTrieIterative(mBpeTrieRoot);
    mWordStartTokenIds.clear();

    mX.clear();
    mNormed.clear();
    mQKV.clear();
    mAttnOut.clear();
    mMlpHid.clear();
    mMlpOut.clear();
    mAttnWeights.clear();

    mCacheHidden.clear();
    std::memset(mCacheHashes, 0, sizeof(mCacheHashes));
    std::memset(mCacheLengths, 0, sizeof(mCacheLengths));
    std::memset(mCacheTokens, 0, sizeof(mCacheTokens));
    mCacheNextSlot = 0;
}

void MicroTransformerModelCpp::unpackTernaryWeights(const uint8_t* packed, int8_t* dest, int count) {
    int packedBytes = (count + 3) / 4;
    int destIdx = 0;
    for (int b = 0; b < packedBytes; ++b) {
        uint8_t val = packed[b];
        for (int k = 0; k < 4 && destIdx < count; ++k) {
            uint8_t code = (val >> (k * 2)) & 0x03;
            if (code == 0x01) {
                dest[destIdx++] = 1;
            } else if (code == 0x03) {
                dest[destIdx++] = -1;
            } else {
                dest[destIdx++] = 0;
            }
        }
    }
}

bool MicroTransformerModelCpp::loadModel(const std::string& filePath) {
    std::ifstream file(filePath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        std::cerr << "MicroTransformerModelCpp::loadModel: Could not open file " << filePath << std::endl;
        return false;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (size < 64 || size > 50 * 1024 * 1024) {
        std::cerr << "MicroTransformerModelCpp::loadModel: Invalid file size: " << size << std::endl;
        return false;
    }

    std::vector<uint8_t> buffer(size);
    if (!file.read(reinterpret_cast<char*>(buffer.data()), size)) {
        std::cerr << "MicroTransformerModelCpp::loadModel: Failed to read file" << std::endl;
        return false;
    }

    return loadModelFromMemory(buffer.data(), buffer.size());
}

bool MicroTransformerModelCpp::loadModelFromMemory(const uint8_t* data, size_t fileSize) {
    if (!data || fileSize < 64) {
        return false;
    }

    unload();

    // 1. Header parsing (64 bytes)
    int32_t magic = *reinterpret_cast<const int32_t*>(data + 0x00);
    if (magic != MAGIC_TRF2 && magic != MAGIC_TRF2_ALT &&
        !(data[0] == 'T' && data[1] == 'R' && data[2] == 'F' && data[3] == '2')) {
        std::cerr << "MicroTransformerModelCpp: Invalid magic number: " << std::hex << magic << std::endl;
        return false;
    }

    int32_t version = *reinterpret_cast<const int32_t*>(data + 0x04);
    if (version != 2) {
        std::cerr << "MicroTransformerModelCpp: Unsupported version: " << version << std::endl;
        return false;
    }

    mVocabSize = *reinterpret_cast<const int32_t*>(data + 0x08);
    mDModel    = *reinterpret_cast<const int32_t*>(data + 0x0C);
    mNHeads    = *reinterpret_cast<const int32_t*>(data + 0x10);
    mNLayers   = *reinterpret_cast<const int32_t*>(data + 0x14);

    if (mVocabSize <= 0 || mVocabSize > 128000 || mDModel <= 0 || mDModel > 4096 ||
        mNHeads <= 0 || mNLayers <= 0 || (mDModel % mNHeads) != 0 || (mDModel % 4) != 0) {
        std::cerr << "MicroTransformerModelCpp: Invalid hyperparameters" << std::endl;
        return false;
    }

    int32_t offBpe    = *reinterpret_cast<const int32_t*>(data + 0x18);
    int32_t offEmb    = *reinterpret_cast<const int32_t*>(data + 0x1C);
    int32_t offPos    = *reinterpret_cast<const int32_t*>(data + 0x20);
    int32_t offLayer0 = *reinterpret_cast<const int32_t*>(data + 0x24);
    mScaleEmb = *reinterpret_cast<const float*>(data + 0x28);
    mScalePos = *reinterpret_cast<const float*>(data + 0x2C);
    mScaleDot = *reinterpret_cast<const float*>(data + 0x30);
    mScaleEmbDot = mScaleEmb * mScaleDot;
    mInvD = 1.0f / static_cast<float>(mDModel);
    mScaleAttn = 1.0f / std::sqrt(static_cast<float>(mDModel / mNHeads));

    // 2. Read BPE Table
    if (offBpe < 64 || static_cast<size_t>(offBpe) >= fileSize) return false;
    int32_t bpeVocabSize = *reinterpret_cast<const int32_t*>(data + offBpe);
    int numBpe = std::min(bpeVocabSize, mVocabSize);

    const int32_t* offsets = reinterpret_cast<const int32_t*>(data + offBpe + 4);
    size_t stringPoolStart = offBpe + 4 + bpeVocabSize * 4;

    mBpeVocab.resize(mVocabSize);
    mIsBadCandidateTable.assign(mVocabSize, 0);

    for (int i = 0; i < numBpe; ++i) {
        size_t strPos = stringPoolStart + offsets[i];
        if (strPos >= fileSize) {
            mBpeVocab[i] = "";
            continue;
        }
        std::string piece;
        const char* strPtr = reinterpret_cast<const char*>(data + strPos);
        while (strPos < fileSize && *strPtr != '\0') {
            if (static_cast<uint8_t>(strPtr[0]) == 0xE2 &&
                strPos + 2 < fileSize &&
                static_cast<uint8_t>(strPtr[1]) == 0x96 &&
                static_cast<uint8_t>(strPtr[2]) == 0x81) {
                piece.push_back(' ');
                strPtr += 3;
                strPos += 3;
            } else {
                piece.push_back(*strPtr);
                strPtr++;
                strPos++;
            }
        }
        mBpeVocab[i] = piece;
    }

    // Precompute bad candidate table
    for (int i = 0; i < mVocabSize; ++i) {
        if (i == 0 || i == UNK_TOKEN_ID) {
            mIsBadCandidateTable[i] = 1;
        } else if (mBpeVocab[i].empty()) {
            mIsBadCandidateTable[i] = 1;
        } else if (mBpeVocab[i].length() >= 2 && mBpeVocab[i].front() == '<' && mBpeVocab[i].back() == '>') {
            mIsBadCandidateTable[i] = 1;
        }
    }

    buildBpeTrie();

    // 3. Read Embeddings (int8_t)
    if (offEmb < 64 || static_cast<size_t>(offEmb + mVocabSize * mDModel) > fileSize) return false;
    mEmbeddings.resize(mVocabSize * mDModel);
    std::memcpy(mEmbeddings.data(), data + offEmb, mVocabSize * mDModel);

    // Precompute word starts
    mWordStartTokenIds.clear();
    for (int i = 2; i < mVocabSize; ++i) {
        const std::string& piece = mBpeVocab[i];
        if (piece.length() > 1 && piece[0] == ' ') {
            bool hasLetter = false;
            for (size_t k = 1; k < piece.length(); ++k) {
                uint8_t uc = static_cast<uint8_t>(piece[k]);
                if (std::isalnum(uc) || uc >= 0x80) {
                    hasLetter = true;
                    break;
                }
            }
            if (hasLetter) {
                mWordStartTokenIds.push_back(i);
            }
        }
    }

    // 4. Positional Embeddings
    if (offPos < 64 || static_cast<size_t>(offPos + MAX_SEQ_LEN * mDModel) > fileSize) return false;
    mPosEmb.resize(MAX_SEQ_LEN * mDModel);
    const int8_t* posBytes = reinterpret_cast<const int8_t*>(data + offPos);
    for (int i = 0; i < MAX_SEQ_LEN * mDModel; ++i) {
        mPosEmb[i] = static_cast<float>(posBytes[i]) * mScalePos;
    }

    // 5. Transformer Layers
    int qkvPacked = (3 * mDModel * mDModel + 3) / 4;
    int projPacked = (mDModel * mDModel + 3) / 4;
    int rawBytes2x = qkvPacked + projPacked + ((2 * mDModel * mDModel + 3) / 4) +
                     ((mDModel * 2 * mDModel + 3) / 4) + (mDModel * 4) + (mDModel * 4) + 4 + 4;
    int stride2x = (rawBytes2x + 63) & ~63;
    mDFf = (offLayer0 + (mNLayers - 1) * stride2x + rawBytes2x <= static_cast<int>(fileSize)) ? (2 * mDModel) : mDModel;

    int mlpUpPacked = (mDFf * mDModel + 3) / 4;
    int mlpDownPacked = (mDModel * mDFf + 3) / 4;
    int rawLayerBytes = qkvPacked + projPacked + mlpUpPacked + mlpDownPacked + (mDModel * 4) + (mDModel * 4) + 4 + 4;
    int layerStride = (rawLayerBytes + 63) & ~63;

    mQkvW.resize(mNLayers * (3 * mDModel) * mDModel);
    mProjW.resize(mNLayers * mDModel * mDModel);
    mMlpUpW.resize(mNLayers * mDFf * mDModel);
    mMlpDownW.resize(mNLayers * mDModel * mDFf);
    mGamma1Fused.resize(mNLayers * mDModel);
    mGamma2Fused.resize(mNLayers * mDModel);
    mScaleProj.resize(mNLayers);
    mScaleDown.resize(mNLayers);

    int qkvCount = (3 * mDModel) * mDModel;
    int projCount = mDModel * mDModel;
    int mlpUpCount = mDFf * mDModel;
    int mlpDownCount = mDModel * mDFf;

    for (int l = 0; l < mNLayers; ++l) {
        size_t layerStart = offLayer0 + l * layerStride;
        if (layerStart + rawLayerBytes > fileSize) return false;

        const uint8_t* ptr = data + layerStart;

        // 1. QKV
        unpackTernaryWeights(ptr, mQkvW.data() + l * qkvCount, qkvCount);
        ptr += qkvPacked;

        // 2. Proj
        unpackTernaryWeights(ptr, mProjW.data() + l * projCount, projCount);
        ptr += projPacked;

        // 3. MLP Up
        unpackTernaryWeights(ptr, mMlpUpW.data() + l * mlpUpCount, mlpUpCount);
        ptr += mlpUpPacked;

        // 4. MLP Down
        unpackTernaryWeights(ptr, mMlpDownW.data() + l * mlpDownCount, mlpDownCount);
        ptr += mlpDownPacked;

        // 5. Gamma1
        std::memcpy(mGamma1Fused.data() + l * mDModel, ptr, mDModel * sizeof(float));
        ptr += mDModel * 4;

        // 6. Gamma2
        std::memcpy(mGamma2Fused.data() + l * mDModel, ptr, mDModel * sizeof(float));
        ptr += mDModel * 4;

        // 7. Scales
        mScaleProj[l] = *reinterpret_cast<const float*>(ptr);
        ptr += 4;
        mScaleDown[l] = *reinterpret_cast<const float*>(ptr);
        ptr += 4;
    }

    // 6. Allocate Intermediate Buffers
    mX.resize(MAX_SEQ_LEN * mDModel);
    mNormed.resize(MAX_SEQ_LEN * mDModel);
    mQKV.resize(MAX_SEQ_LEN * (3 * mDModel));
    mAttnOut.resize(MAX_SEQ_LEN * mDModel);
    mMlpHid.resize(MAX_SEQ_LEN * mDFf);
    mMlpOut.resize(MAX_SEQ_LEN * mDModel);
    mAttnWeights.resize(mNHeads * MAX_SEQ_LEN * MAX_SEQ_LEN);

    mCacheHidden.resize(HT_CACHE_SIZE * mDModel);
    std::memset(mCacheHashes, 0, sizeof(mCacheHashes));
    std::memset(mCacheLengths, 0, sizeof(mCacheLengths));
    std::memset(mCacheTokens, 0, sizeof(mCacheTokens));
    mCacheNextSlot = 0;

    mIsLoaded = true;
    return true;
}

void MicroTransformerModelCpp::buildBpeTrie() {
    mBpeTrieRoot = std::make_unique<TrieNode>();
    for (int id = 0; id < mVocabSize; ++id) {
        const std::string& piece = mBpeVocab[id];
        if (piece.empty()) continue;

        TrieNode* current = mBpeTrieRoot.get();
        for (char c : piece) {
            current = current->getOrCreateChild(c);
        }
        current->tokenId = id;
    }
}

int MicroTransformerModelCpp::tokenize(const char* text, size_t textLen, int32_t* outTokens, int maxTokens) {
    if (!mIsLoaded || !text || textLen == 0 || !outTokens || maxTokens <= 0) {
        return 0;
    }

    std::string normText = normalizeTextUtf8(text, textLen);
    if (normText.empty()) return 0;

    bool hasVirtualStart = (normText[0] != ' ');
    size_t virtualLen = hasVirtualStart ? normText.length() + 1 : normText.length();

    int count = 0;
    size_t pos = 0;

    while (pos < virtualLen && count < maxTokens) {
        int bestLen = 0;
        int bestId = UNK_TOKEN_ID;

        if (mBpeTrieRoot) {
            const TrieNode* node = mBpeTrieRoot.get();
            for (size_t i = pos; i < virtualLen; ++i) {
                char c = hasVirtualStart ? ((i == 0) ? ' ' : normText[i - 1]) : normText[i];
                const TrieNode* child = node->getChild(c);
                if (!child) break;
                node = child;
                if (node->tokenId != -1) {
                    bestLen = (i - pos) + 1;
                    bestId = node->tokenId;
                }
            }
        }

        if (bestLen == 0) {
            bestLen = 1;
            bestId = UNK_TOKEN_ID;
        }

        outTokens[count++] = bestId;
        pos += bestLen;
    }

    return count;
}

int MicroTransformerModelCpp::tokenizeTail(const char* text, size_t textLen, int32_t* outTokens, int maxTokens) {
    if (!mIsLoaded || !text || textLen == 0 || !outTokens || maxTokens <= 0) return 0;

    int32_t allTokens[256];
    int total = tokenize(text, textLen, allTokens, 256);
    if (total <= 0) return 0;

    int keep = std::min(total, maxTokens);
    int start = total - keep;
    for (int i = 0; i < keep; ++i) {
        outTokens[i] = allTokens[start + i];
    }
    return keep;
}

bool MicroTransformerModelCpp::forward(const int32_t* contextTokens, int numTokens, float* outHidden) {
    if (!mIsLoaded || !contextTokens || numTokens <= 0 || !outHidden) {
        return false;
    }

    int actualStart = std::max(0, numTokens - MAX_SEQ_LEN);
    int consecutiveUnk = 0;
    for (int t = actualStart; t < numTokens; ++t) {
        if (contextTokens[t] == UNK_TOKEN_ID) {
            consecutiveUnk++;
            if (consecutiveUnk >= 2) {
                actualStart = t;
            }
        } else {
            consecutiveUnk = 0;
        }
    }

    const int start = actualStart;
    const int T = numTokens - start;
    const int D = mDModel;
    const float invD = mInvD;
    const int H = mNHeads;
    const int dk = D / H;
    const int dFf = mDFf;

    // LRU Cache Check
    uint64_t contextHash = 1125899906842597ULL;
    for (int t = 0; t < T; ++t) {
        int32_t tok = contextTokens[start + t];
        if (tok < 0 || tok >= mVocabSize) tok = UNK_TOKEN_ID;
        contextHash = contextHash * 31ULL + static_cast<uint64_t>(tok);
    }

    for (int i = 0; i < HT_CACHE_SIZE; ++i) {
        if (mCacheHashes[i] == contextHash && mCacheLengths[i] == T) {
            bool match = true;
            for (int t = 0; t < T; ++t) {
                int32_t tok = contextTokens[start + t];
                if (tok < 0 || tok >= mVocabSize) tok = UNK_TOKEN_ID;
                if (mCacheTokens[i][t] != tok) {
                    match = false;
                    break;
                }
            }
            if (match) {
                std::memcpy(outHidden, mCacheHidden.data() + i * D, D * sizeof(float));
                return true; // Cache Hit!
            }
        }
    }

    // 1. Token Embeddings + Pos Embeddings
    const float scaleEmb = mScaleEmb;
    for (int t = 0; t < T; ++t) {
        int32_t tok = contextTokens[start + t];
        if (tok < 0 || tok >= mVocabSize) tok = UNK_TOKEN_ID;

        const int8_t* embPtr = mEmbeddings.data() + tok * D;
        const float* posPtr = mPosEmb.data() + t * D;
        float* xPtr = mX.data() + t * D;

#if defined(USE_SSE)
        for (int d = 0; d < D; d += 4) {
            __m128 ve = _mm_set_ps(static_cast<float>(embPtr[d + 3]),
                                   static_cast<float>(embPtr[d + 2]),
                                   static_cast<float>(embPtr[d + 1]),
                                   static_cast<float>(embPtr[d + 0]));
            __m128 vp = _mm_loadu_ps(posPtr + d);
            __m128 vx = _mm_add_ps(_mm_mul_ps(ve, _mm_set1_ps(scaleEmb)), vp);
            _mm_storeu_ps(xPtr + d, vx);
        }
#else
        for (int d = 0; d < D; ++d) {
            xPtr[d] = static_cast<float>(embPtr[d]) * scaleEmb + posPtr[d];
        }
#endif
    }

    // 2. Transformer Layers
    int outDimQKV = 3 * D;
    for (int layer = 0; layer < mNLayers; ++layer) {
        // 2a. RMSNorm pre-attention with fused gamma
        const float* gamma1 = mGamma1Fused.data() + layer * D;
        for (int t = 0; t < T; ++t) {
            float* xPtr = mX.data() + t * D;
            float* nPtr = mNormed.data() + t * D;

            float sumSq = 0.0f;
#if defined(USE_SSE)
            __m128 accSq0 = _mm_setzero_ps();
            __m128 accSq1 = _mm_setzero_ps();
            for (int d = 0; d < D; d += 8) {
                __m128 vx0 = _mm_loadu_ps(xPtr + d);
                __m128 vx1 = _mm_loadu_ps(xPtr + d + 4);
                accSq0 = _mm_add_ps(accSq0, _mm_mul_ps(vx0, vx0));
                accSq1 = _mm_add_ps(accSq1, _mm_mul_ps(vx1, vx1));
            }
            __m128 accSq = _mm_add_ps(accSq0, accSq1);
            accSq = _mm_hadd_ps(accSq, accSq);
            accSq = _mm_hadd_ps(accSq, accSq);
            sumSq = _mm_cvtss_f32(accSq);
#else
            for (int d = 0; d < D; ++d) {
                sumSq += xPtr[d] * xPtr[d];
            }
#endif
            float invRms = 1.0f / std::sqrt(sumSq * invD + 1e-5f);

#if defined(USE_SSE)
            __m128 vInvRms = _mm_set1_ps(invRms);
            for (int d = 0; d < D; d += 4) {
                __m128 vx = _mm_loadu_ps(xPtr + d);
                __m128 vg = _mm_loadu_ps(gamma1 + d);
                __m128 vn = _mm_mul_ps(_mm_mul_ps(vx, vInvRms), vg);
                _mm_storeu_ps(nPtr + d, vn);
            }
#else
            for (int d = 0; d < D; ++d) {
                nPtr[d] = xPtr[d] * invRms * gamma1[d];
            }
#endif
        }

        // 2b. QKV Projection: mQKV[t, :] = mNormed[t, :] * qkv_weight^T
        const int8_t* qkvWLayer = mQkvW.data() + layer * outDimQKV * D;
        for (int t = 0; t < T; ++t) {
            matvec_ternary(qkvWLayer, mNormed.data() + t * D, mQKV.data() + t * outDimQKV, outDimQKV, D);
        }

        // 2c. Causal Multi-Head Self-Attention
        std::memset(mAttnOut.data(), 0, T * D * sizeof(float));
        const float scaleAttn = mScaleAttn;

        for (int h = 0; h < H; ++h) {
            int qOff = h * dk;
            int kOff = D + h * dk;
            int vOff = 2 * D + h * dk;

            for (int i = 0; i < T; ++i) {
                const float* qPtr = mQKV.data() + i * outDimQKV + qOff;
                float maxVal = -1e30f;

                // Dot product Q_i * K_j for j <= i
                for (int j = 0; j <= i; ++j) {
                    const float* kPtr = mQKV.data() + j * outDimQKV + kOff;
                    float dot = 0.0f;
#if defined(USE_SSE)
                    __m128 accDot = _mm_setzero_ps();
                    for (int d = 0; d < dk; d += 4) {
                        __m128 vq = _mm_loadu_ps(qPtr + d);
                        __m128 vk = _mm_loadu_ps(kPtr + d);
                        accDot = _mm_add_ps(accDot, _mm_mul_ps(vq, vk));
                    }
                    accDot = _mm_hadd_ps(accDot, accDot);
                    accDot = _mm_hadd_ps(accDot, accDot);
                    dot = _mm_cvtss_f32(accDot);
#else
                    for (int d = 0; d < dk; ++d) {
                        dot += qPtr[d] * kPtr[d];
                    }
#endif
                    dot *= scaleAttn;
                    int weightIdx = (h * MAX_SEQ_LEN + i) * MAX_SEQ_LEN + j;
                    mAttnWeights[weightIdx] = dot;
                    if (dot > maxVal) maxVal = dot;
                }

                // Softmax
                float sumExp = 0.0f;
                for (int j = 0; j <= i; ++j) {
                    int weightIdx = (h * MAX_SEQ_LEN + i) * MAX_SEQ_LEN + j;
                    float val = std::exp(mAttnWeights[weightIdx] - maxVal);
                    mAttnWeights[weightIdx] = val;
                    sumExp += val;
                }
                float invSum = (sumExp > 0.0f) ? (1.0f / sumExp) : 0.0f;

                // Weighted sum of Values -> attn_out[i, h*dk + d]
                float* outPtr = mAttnOut.data() + i * D + h * dk;
                for (int j = 0; j <= i; ++j) {
                    int weightIdx = (h * MAX_SEQ_LEN + i) * MAX_SEQ_LEN + j;
                    float w = mAttnWeights[weightIdx] * invSum;
                    const float* vPtr = mQKV.data() + j * outDimQKV + vOff;

#if defined(USE_SSE)
                    __m128 vw = _mm_set1_ps(w);
                    for (int d = 0; d < dk; d += 4) {
                        __m128 vo = _mm_loadu_ps(outPtr + d);
                        __m128 vv = _mm_loadu_ps(vPtr + d);
                        vo = _mm_add_ps(vo, _mm_mul_ps(vw, vv));
                        _mm_storeu_ps(outPtr + d, vo);
                    }
#else
                    for (int d = 0; d < dk; ++d) {
                        outPtr[d] += w * vPtr[d];
                    }
#endif
                }
            }
        }

        // 2d. Output Projection + Residual: mX += s_proj * (Proj * mAttnOut)
        const float sProj = mScaleProj[layer];
        const int8_t* projWLayer = mProjW.data() + layer * D * D;
        for (int t = 0; t < T; ++t) {
            matvec_ternary_accum(projWLayer, mAttnOut.data() + t * D, mX.data() + t * D, sProj, D, D);
        }

        // 2e. RMSNorm pre-MLP with fused gamma
        const float* gamma2 = mGamma2Fused.data() + layer * D;
        for (int t = 0; t < T; ++t) {
            float* xPtr = mX.data() + t * D;
            float* nPtr = mNormed.data() + t * D;

            float sumSq = 0.0f;
#if defined(USE_SSE)
            __m128 accSq0 = _mm_setzero_ps();
            __m128 accSq1 = _mm_setzero_ps();
            for (int d = 0; d < D; d += 8) {
                __m128 vx0 = _mm_loadu_ps(xPtr + d);
                __m128 vx1 = _mm_loadu_ps(xPtr + d + 4);
                accSq0 = _mm_add_ps(accSq0, _mm_mul_ps(vx0, vx0));
                accSq1 = _mm_add_ps(accSq1, _mm_mul_ps(vx1, vx1));
            }
            __m128 accSq = _mm_add_ps(accSq0, accSq1);
            accSq = _mm_hadd_ps(accSq, accSq);
            accSq = _mm_hadd_ps(accSq, accSq);
            sumSq = _mm_cvtss_f32(accSq);
#else
            for (int d = 0; d < D; ++d) {
                sumSq += xPtr[d] * xPtr[d];
            }
#endif
            float invRms = 1.0f / std::sqrt(sumSq * invD + 1e-5f);

#if defined(USE_SSE)
            __m128 vInvRms = _mm_set1_ps(invRms);
            for (int d = 0; d < D; d += 4) {
                __m128 vx = _mm_loadu_ps(xPtr + d);
                __m128 vg = _mm_loadu_ps(gamma2 + d);
                __m128 vn = _mm_mul_ps(_mm_mul_ps(vx, vInvRms), vg);
                _mm_storeu_ps(nPtr + d, vn);
            }
#else
            for (int d = 0; d < D; ++d) {
                nPtr[d] = xPtr[d] * invRms * gamma2[d];
            }
#endif
        }

        // 2f. MLP Forward: hid = ReLU(mlp_up * normed)
        const int8_t* mlpUpWLayer = mMlpUpW.data() + layer * dFf * D;
        for (int t = 0; t < T; ++t) {
            float* hidPtr = mMlpHid.data() + t * dFf;
            matvec_ternary(mlpUpWLayer, mNormed.data() + t * D, hidPtr, dFf, D);
            // ReLU
            for (int j = 0; j < dFf; ++j) {
                if (hidPtr[j] < 0.0f) hidPtr[j] = 0.0f;
            }
        }

        // 2g. MLP Down + Residual: mX += s_down * (mlp_down * hid)
        const float sDown = mScaleDown[layer];
        const int8_t* mlpDownWLayer = mMlpDownW.data() + layer * D * dFf;
        for (int t = 0; t < T; ++t) {
            matvec_ternary_accum(mlpDownWLayer, mMlpHid.data() + t * dFf, mX.data() + t * D, sDown, D, dFf);
        }
    }

    // 3. Final RMSNorm on the last token (t = T - 1) -> outHidden
    const float* xLast = mX.data() + (T - 1) * D;
    float sumSq = 0.0f;
#if defined(USE_SSE)
    __m128 accSq0 = _mm_setzero_ps();
    __m128 accSq1 = _mm_setzero_ps();
    for (int d = 0; d < D; d += 8) {
        __m128 vx0 = _mm_loadu_ps(xLast + d);
        __m128 vx1 = _mm_loadu_ps(xLast + d + 4);
        accSq0 = _mm_add_ps(accSq0, _mm_mul_ps(vx0, vx0));
        accSq1 = _mm_add_ps(accSq1, _mm_mul_ps(vx1, vx1));
    }
    __m128 accSq = _mm_add_ps(accSq0, accSq1);
    accSq = _mm_hadd_ps(accSq, accSq);
    accSq = _mm_hadd_ps(accSq, accSq);
    sumSq = _mm_cvtss_f32(accSq);
#else
    for (int d = 0; d < D; ++d) {
        sumSq += xLast[d] * xLast[d];
    }
#endif
    float invRms = 1.0f / std::sqrt(sumSq * invD + 1e-5f);

#if defined(USE_SSE)
    __m128 vInvRms = _mm_set1_ps(invRms);
    for (int d = 0; d < D; d += 4) {
        __m128 vx = _mm_loadu_ps(xLast + d);
        _mm_storeu_ps(outHidden + d, _mm_mul_ps(vx, vInvRms));
    }
#else
    for (int d = 0; d < D; ++d) {
        outHidden[d] = xLast[d] * invRms;
    }
#endif

    // Cache hidden state
    int slot = mCacheNextSlot;
    std::memcpy(mCacheHidden.data() + slot * D, outHidden, D * sizeof(float));
    mCacheHashes[slot] = contextHash;
    mCacheLengths[slot] = T;
    for (int t = 0; t < T; ++t) {
        int32_t tok = contextTokens[start + t];
        if (tok < 0 || tok >= mVocabSize) tok = UNK_TOKEN_ID;
        mCacheTokens[slot][t] = tok;
    }
    mCacheNextSlot = (slot + 1) % HT_CACHE_SIZE;

    return true;
}

void MicroTransformerModelCpp::scoreCandidates(const float* hT, const int32_t* candidateIds, int numCandidates, float* outLogits) {
    if (!mIsLoaded || !hT || !candidateIds || !outLogits || numCandidates <= 0) {
        return;
    }

    const int D = mDModel;
    const float scale = mScaleEmbDot;

#if defined(USE_NEON)
    int c = 0;
    for (; c + 3 < numCandidates; c += 4) {
        int32_t c0 = candidateIds[c];
        int32_t c1 = candidateIds[c + 1];
        int32_t c2 = candidateIds[c + 2];
        int32_t c3 = candidateIds[c + 3];

        bool bad0 = isBadCandidate(c0);
        bool bad1 = isBadCandidate(c1);
        bool bad2 = isBadCandidate(c2);
        bool bad3 = isBadCandidate(c3);

        const int8_t* p0 = bad0 ? nullptr : (mEmbeddings.data() + c0 * D);
        const int8_t* p1 = bad1 ? nullptr : (mEmbeddings.data() + c1 * D);
        const int8_t* p2 = bad2 ? nullptr : (mEmbeddings.data() + c2 * D);
        const int8_t* p3 = bad3 ? nullptr : (mEmbeddings.data() + c3 * D);

        float32x4_t acc0 = vdupq_n_f32(0.0f);
        float32x4_t acc1 = vdupq_n_f32(0.0f);
        float32x4_t acc2 = vdupq_n_f32(0.0f);
        float32x4_t acc3 = vdupq_n_f32(0.0f);

        for (int d = 0; d < D; d += 8) {
            float32x4_t vh0 = vld1q_f32(hT + d);
            float32x4_t vh1 = vld1q_f32(hT + d + 4);

            if (p0) {
                int8x8_t wb = vld1_s8(p0 + d);
                int16x8_t w16 = vmovl_s8(wb);
                acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16))), vh0);
                acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16))), vh1);
            }
            if (p1) {
                int8x8_t wb = vld1_s8(p1 + d);
                int16x8_t w16 = vmovl_s8(wb);
                acc1 = vmlaq_f32(acc1, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16))), vh0);
                acc1 = vmlaq_f32(acc1, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16))), vh1);
            }
            if (p2) {
                int8x8_t wb = vld1_s8(p2 + d);
                int16x8_t w16 = vmovl_s8(wb);
                acc2 = vmlaq_f32(acc2, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16))), vh0);
                acc2 = vmlaq_f32(acc2, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16))), vh1);
            }
            if (p3) {
                int8x8_t wb = vld1_s8(p3 + d);
                int16x8_t w16 = vmovl_s8(wb);
                acc3 = vmlaq_f32(acc3, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16))), vh0);
                acc3 = vmlaq_f32(acc3, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16))), vh1);
            }
        }

#if defined(__aarch64__)
        outLogits[c]     = bad0 ? -1e30f : (vaddvq_f32(acc0) * scale);
        outLogits[c + 1] = bad1 ? -1e30f : (vaddvq_f32(acc1) * scale);
        outLogits[c + 2] = bad2 ? -1e30f : (vaddvq_f32(acc2) * scale);
        outLogits[c + 3] = bad3 ? -1e30f : (vaddvq_f32(acc3) * scale);
#else
        auto hsum_neon = [](float32x4_t v) -> float {
            float32x2_t r = vadd_f32(vget_low_f32(v), vget_high_f32(v));
            return vget_lane_f32(vpadd_f32(r, r), 0);
        };
        outLogits[c]     = bad0 ? -1e30f : (hsum_neon(acc0) * scale);
        outLogits[c + 1] = bad1 ? -1e30f : (hsum_neon(acc1) * scale);
        outLogits[c + 2] = bad2 ? -1e30f : (hsum_neon(acc2) * scale);
        outLogits[c + 3] = bad3 ? -1e30f : (hsum_neon(acc3) * scale);
#endif
    }
    for (; c < numCandidates; ++c) {
        int32_t candId = candidateIds[c];
        if (isBadCandidate(candId)) {
            outLogits[c] = -1e30f;
            continue;
        }
        const int8_t* embPtr = mEmbeddings.data() + candId * D;
        float32x4_t acc0 = vdupq_n_f32(0.0f);
        for (int d = 0; d < D; d += 8) {
            float32x4_t vh0 = vld1q_f32(hT + d);
            float32x4_t vh1 = vld1q_f32(hT + d + 4);
            int8x8_t wb = vld1_s8(embPtr + d);
            int16x8_t w16 = vmovl_s8(wb);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_low_s16(w16))), vh0);
            acc0 = vmlaq_f32(acc0, vcvtq_f32_s32(vmovl_s16(vget_high_s16(w16))), vh1);
        }
#if defined(__aarch64__)
        outLogits[c] = vaddvq_f32(acc0) * scale;
#else
        float32x2_t r = vadd_f32(vget_low_f32(acc0), vget_high_f32(acc0));
        outLogits[c] = vget_lane_f32(vpadd_f32(r, r), 0) * scale;
#endif
    }
#elif defined(USE_SSE)
    auto hsum128 = [](__m128 v) -> float {
        v = _mm_hadd_ps(v, v);
        v = _mm_hadd_ps(v, v);
        return _mm_cvtss_f32(v);
    };

    int c = 0;
    for (; c + 3 < numCandidates; c += 4) {
        int32_t c0 = candidateIds[c];
        int32_t c1 = candidateIds[c + 1];
        int32_t c2 = candidateIds[c + 2];
        int32_t c3 = candidateIds[c + 3];

        bool bad0 = isBadCandidate(c0);
        bool bad1 = isBadCandidate(c1);
        bool bad2 = isBadCandidate(c2);
        bool bad3 = isBadCandidate(c3);

        const int8_t* p0 = bad0 ? nullptr : (mEmbeddings.data() + c0 * D);
        const int8_t* p1 = bad1 ? nullptr : (mEmbeddings.data() + c1 * D);
        const int8_t* p2 = bad2 ? nullptr : (mEmbeddings.data() + c2 * D);
        const int8_t* p3 = bad3 ? nullptr : (mEmbeddings.data() + c3 * D);

        __m128 acc0 = _mm_setzero_ps();
        __m128 acc1 = _mm_setzero_ps();
        __m128 acc2 = _mm_setzero_ps();
        __m128 acc3 = _mm_setzero_ps();

        for (int d = 0; d < D; d += 8) {
            __m128 vh0 = _mm_loadu_ps(hT + d);
            __m128 vh1 = _mm_loadu_ps(hT + d + 4);

            if (p0) {
                __m128i r0 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(p0 + d));
                __m128 f0_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r0));
                __m128 f0_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r0, 4)));
                acc0 = _mm_add_ps(acc0, _mm_add_ps(_mm_mul_ps(vh0, f0_0), _mm_mul_ps(vh1, f0_1)));
            }
            if (p1) {
                __m128i r1 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(p1 + d));
                __m128 f1_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r1));
                __m128 f1_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r1, 4)));
                acc1 = _mm_add_ps(acc1, _mm_add_ps(_mm_mul_ps(vh0, f1_0), _mm_mul_ps(vh1, f1_1)));
            }
            if (p2) {
                __m128i r2 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(p2 + d));
                __m128 f2_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r2));
                __m128 f2_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r2, 4)));
                acc2 = _mm_add_ps(acc2, _mm_add_ps(_mm_mul_ps(vh0, f2_0), _mm_mul_ps(vh1, f2_1)));
            }
            if (p3) {
                __m128i r3 = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(p3 + d));
                __m128 f3_0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(r3));
                __m128 f3_1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(r3, 4)));
                acc3 = _mm_add_ps(acc3, _mm_add_ps(_mm_mul_ps(vh0, f3_0), _mm_mul_ps(vh1, f3_1)));
            }
        }

        outLogits[c]     = bad0 ? -1e30f : (hsum128(acc0) * scale);
        outLogits[c + 1] = bad1 ? -1e30f : (hsum128(acc1) * scale);
        outLogits[c + 2] = bad2 ? -1e30f : (hsum128(acc2) * scale);
        outLogits[c + 3] = bad3 ? -1e30f : (hsum128(acc3) * scale);
    }
    for (; c < numCandidates; ++c) {
        int32_t candId = candidateIds[c];
        if (isBadCandidate(candId)) {
            outLogits[c] = -1e30f;
            continue;
        }
        const int8_t* embPtr = mEmbeddings.data() + candId * D;
        __m128 acc = _mm_setzero_ps();
        for (int d = 0; d < D; d += 8) {
            __m128 vh0 = _mm_loadu_ps(hT + d);
            __m128 vh1 = _mm_loadu_ps(hT + d + 4);
            __m128i raw = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(embPtr + d));
            __m128 f0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(raw));
            __m128 f1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(raw, 4)));
            acc = _mm_add_ps(acc, _mm_add_ps(_mm_mul_ps(vh0, f0), _mm_mul_ps(vh1, f1)));
        }
        outLogits[c] = hsum128(acc) * scale;
    }
#else
    for (int c = 0; c < numCandidates; ++c) {
        int32_t candId = candidateIds[c];
        if (isBadCandidate(candId)) {
            outLogits[c] = -1e30f;
            continue;
        }
        const int8_t* embPtr = mEmbeddings.data() + candId * D;
        float dot0 = 0.0f, dot1 = 0.0f, dot2 = 0.0f, dot3 = 0.0f;
        for (int d = 0; d < D; d += 4) {
            dot0 += hT[d] * embPtr[d];
            dot1 += hT[d + 1] * embPtr[d + 1];
            dot2 += hT[d + 2] * embPtr[d + 2];
            dot3 += hT[d + 3] * embPtr[d + 3];
        }
        outLogits[c] = ((dot0 + dot1) + (dot2 + dot3)) * scale;
    }
#endif
}

int MicroTransformerModelCpp::scoreTopK(const float* hT, const int32_t* candidateIds, int numCandidates, int k,
                                         int32_t* outTopTokens, float* outTopScores) {
    if (!mIsLoaded || !hT || !candidateIds || numCandidates <= 0 || k <= 0) {
        return 0;
    }

    struct Cand {
        int32_t id;
        float score;
        bool operator>(const Cand& other) const { return score > other.score; }
    };

    std::priority_queue<Cand, std::vector<Cand>, std::greater<Cand>> minHeap;

    const int D = mDModel;
    const float scale = mScaleEmbDot;

#if defined(USE_SSE)
    auto hsum128 = [](__m128 v) -> float {
        v = _mm_hadd_ps(v, v);
        v = _mm_hadd_ps(v, v);
        return _mm_cvtss_f32(v);
    };

    for (int c = 0; c < numCandidates; ++c) {
        int32_t candId = candidateIds[c];
        if (isBadCandidate(candId)) continue;

        const int8_t* embPtr = mEmbeddings.data() + candId * D;
        __m128 acc = _mm_setzero_ps();
        for (int d = 0; d < D; d += 8) {
            __m128 vh0 = _mm_loadu_ps(hT + d);
            __m128 vh1 = _mm_loadu_ps(hT + d + 4);
            __m128i raw = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(embPtr + d));
            __m128 f0 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(raw));
            __m128 f1 = _mm_cvtepi32_ps(_mm_cvtepi8_epi32(_mm_srli_si128(raw, 4)));
            acc = _mm_add_ps(acc, _mm_add_ps(_mm_mul_ps(vh0, f0), _mm_mul_ps(vh1, f1)));
        }
        float score = hsum128(acc) * scale;

        if (static_cast<int>(minHeap.size()) < k) {
            minHeap.push({candId, score});
        } else if (score > minHeap.top().score) {
            minHeap.pop();
            minHeap.push({candId, score});
        }
    }
#else
    for (int c = 0; c < numCandidates; ++c) {
        int32_t candId = candidateIds[c];
        if (isBadCandidate(candId)) continue;

        const int8_t* embPtr = mEmbeddings.data() + candId * D;
        float dot0 = 0.0f, dot1 = 0.0f, dot2 = 0.0f, dot3 = 0.0f;
        for (int d = 0; d < D; d += 4) {
            dot0 += hT[d] * embPtr[d];
            dot1 += hT[d + 1] * embPtr[d + 1];
            dot2 += hT[d + 2] * embPtr[d + 2];
            dot3 += hT[d + 3] * embPtr[d + 3];
        }
        float score = ((dot0 + dot1) + (dot2 + dot3)) * scale;

        if (static_cast<int>(minHeap.size()) < k) {
            minHeap.push({candId, score});
        } else if (score > minHeap.top().score) {
            minHeap.pop();
            minHeap.push({candId, score});
        }
    }
#endif

    int resultCount = static_cast<int>(minHeap.size());
    for (int i = resultCount - 1; i >= 0; --i) {
        outTopTokens[i] = minHeap.top().id;
        outTopScores[i] = minHeap.top().score;
        minHeap.pop();
    }
    return resultCount;
}

std::string MicroTransformerModelCpp::getTokenText(int32_t tokenId) const {
    if (!mIsLoaded || tokenId < 0 || static_cast<size_t>(tokenId) >= mBpeVocab.size()) {
        return "";
    }
    return mBpeVocab[tokenId];
}

} // namespace micro_transformer
