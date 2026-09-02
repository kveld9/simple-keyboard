#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <memory>
#include <array>
#include <cmath>
#include <cstring>
#include <algorithm>

namespace micro_transformer {

constexpr int MAGIC_TRF2 = 0x54524632;      // "TRF2"
constexpr int MAGIC_TRF2_ALT = 0x32465254;  // "2FRT" LE
constexpr int MAX_SEQ_LEN = 32;
constexpr int UNK_TOKEN_ID = 1;
constexpr char32_t WORD_START_CHAR = 0x2581; // ' ' (Lower One Eighth Block)
constexpr int HT_CACHE_SIZE = 8;

struct TrieNode {
    int32_t tokenId = -1;
    std::vector<char> childChars;
    std::vector<std::unique_ptr<TrieNode>> childNodes;

    TrieNode* getChild(char c) const {
        for (size_t i = 0; i < childChars.size(); ++i) {
            if (childChars[i] == c) {
                return childNodes[i].get();
            }
        }
        return nullptr;
    }

    TrieNode* getOrCreateChild(char c) {
        for (size_t i = 0; i < childChars.size(); ++i) {
            if (childChars[i] == c) {
                return childNodes[i].get();
            }
        }
        childChars.push_back(c);
        childNodes.push_back(std::make_unique<TrieNode>());
        return childNodes.back().get();
    }
};

class MicroTransformerModelCpp {
public:
    MicroTransformerModelCpp();
    ~MicroTransformerModelCpp();

    MicroTransformerModelCpp(const MicroTransformerModelCpp&) = delete;
    MicroTransformerModelCpp& operator=(const MicroTransformerModelCpp&) = delete;

    bool loadModel(const std::string& filePath);
    bool loadModelFromMemory(const uint8_t* data, size_t size);
    void unload();
    bool isLoaded() const { return mIsLoaded; }

    int tokenize(const char* text, size_t textLen, int32_t* outTokens, int maxTokens);
    int tokenizeTail(const char* text, size_t textLen, int32_t* outTokens, int maxTokens);

    bool forward(const int32_t* contextTokens, int numTokens, float* outHidden);
    void scoreCandidates(const float* hT, const int32_t* candidateIds, int numCandidates, float* outLogits);

    // Fast top-k prediction directly from hidden state
    int scoreTopK(const float* hT, const int32_t* candidateIds, int numCandidates, int k,
                  int32_t* outTopTokens, float* outTopScores);

    const std::vector<int32_t>& getWordStartTokenIds() const { return mWordStartTokenIds; }
    std::string getTokenText(int32_t tokenId) const;
    int getVocabSize() const { return mVocabSize; }
    int getModelDim() const { return mDModel; }
    int getNumLayers() const { return mNLayers; }
    int getNumHeads() const { return mNHeads; }

private:
    void buildBpeTrie();
    bool isBadCandidate(int32_t tokenId) const {
        if (tokenId < 0 || tokenId >= mVocabSize) return true;
        return mIsBadCandidateTable[tokenId] != 0;
    }
    static void unpackTernaryWeights(const uint8_t* packed, int8_t* dest, int count);

    bool mIsLoaded{false};

    // Hyperparameters
    int32_t mVocabSize{0};
    int32_t mDModel{0};
    int32_t mDFf{0};
    int32_t mNHeads{0};
    int32_t mNLayers{0};
    float mScaleEmb{1.0f};
    float mScalePos{1.0f};
    float mScaleDot{1.0f};
    float mScaleEmbDot{1.0f};
    float mInvD{0.0f};
    float mScaleAttn{0.0f};

    // Weights
    std::vector<int8_t> mEmbeddings;        // vocab_size * d_model (int8)
    std::vector<float> mPosEmb;             // MAX_SEQ_LEN * d_model (float)
    std::vector<int8_t> mQkvW;              // n_layers * (3 * d_model) * d_model
    std::vector<int8_t> mProjW;             // n_layers * d_model * d_model
    std::vector<int8_t> mMlpUpW;            // n_layers * d_ff * d_model
    std::vector<int8_t> mMlpDownW;          // n_layers * d_model * d_ff
    std::vector<float> mGamma1Fused;        // n_layers * d_model
    std::vector<float> mGamma2Fused;        // n_layers * d_model
    std::vector<float> mScaleProj;          // [n_layers]
    std::vector<float> mScaleDown;          // [n_layers]

    // BPE Vocab & Trie
    std::vector<std::string> mBpeVocab;
    std::vector<uint8_t> mIsBadCandidateTable;
    std::unique_ptr<TrieNode> mBpeTrieRoot;
    std::vector<int32_t> mWordStartTokenIds;

    // Pre-allocated intermediate scratch buffers (Zero Allocations in forward pass)
    std::vector<float> mX;           // MAX_SEQ_LEN * d_model
    std::vector<float> mNormed;      // MAX_SEQ_LEN * d_model
    std::vector<float> mQKV;         // MAX_SEQ_LEN * (3 * d_model)
    std::vector<float> mAttnOut;     // MAX_SEQ_LEN * d_model
    std::vector<float> mMlpHid;      // MAX_SEQ_LEN * d_ff
    std::vector<float> mMlpOut;      // MAX_SEQ_LEN * d_model
    std::vector<float> mAttnWeights; // n_heads * MAX_SEQ_LEN * MAX_SEQ_LEN

    // LRU Hidden State Cache
    uint64_t mCacheHashes[HT_CACHE_SIZE]{};
    int32_t mCacheLengths[HT_CACHE_SIZE]{};
    int32_t mCacheTokens[HT_CACHE_SIZE][MAX_SEQ_LEN]{};
    std::vector<float> mCacheHidden; // HT_CACHE_SIZE * d_model
    int mCacheNextSlot{0};
};

} // namespace micro_transformer
