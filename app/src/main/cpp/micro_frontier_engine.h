#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <memory>
#include <array>

namespace micro_frontier {

constexpr int MAGIC_TRF3 = 0x54524633;      // "TRF3"
constexpr int MAGIC_TRF3_ALT = 0x33465254;  // "3FRT" LE
constexpr int MAX_SEQ_LEN = 64;
constexpr int UNK_TOKEN_ID = 1;
constexpr char32_t WORD_START_CHAR = 0x2581; // ' ' (Lower One Eighth Block)
constexpr int HT_CACHE_SIZE = 8;

struct TrieNode {
    int32_t tokenId = -1;
    std::vector<char> childChars;
    std::vector<std::unique_ptr<TrieNode>> childNodes;

    TrieNode* getChild(char c) const {
        for (size_t i = 0; i < childChars.size(); ++i) {
            if (childChars[i] == c) return childNodes[i].get();
        }
        return nullptr;
    }

    TrieNode* getOrCreateChild(char c) {
        for (size_t i = 0; i < childChars.size(); ++i) {
            if (childChars[i] == c) return childNodes[i].get();
        }
        childChars.push_back(c);
        childNodes.push_back(std::make_unique<TrieNode>());
        return childNodes.back().get();
    }
};

struct TernaryMatrix {
    uint32_t rows = 0;
    uint32_t cols = 0;
    float gamma = 1.0f;
    std::vector<uint8_t> packed_weights;
};

struct KChebLayerWeights {
    std::vector<float> norm1_gamma;
    std::vector<float> decay_A; // (n_heads * 64)
    std::vector<float> decay_B; // (n_heads * 16)
    std::vector<float> decay_A_h; // (n_heads)
    std::vector<float> decay_B_h; // (n_heads)
    TernaryMatrix k_proj;
    TernaryMatrix v_proj;
    TernaryMatrix r_proj;
    TernaryMatrix out_proj;
    std::vector<float> norm2_gamma;
    TernaryMatrix mlp_in;
    TernaryMatrix mlp_out;
};

struct KChebState {
    std::vector<float> state_A; // (n_heads * 8)
    std::vector<float> state_B; // (n_heads * 4)
};

class MicroFrontierEngine {
public:
    MicroFrontierEngine();
    ~MicroFrontierEngine();

    MicroFrontierEngine(const MicroFrontierEngine&) = delete;
    MicroFrontierEngine& operator=(const MicroFrontierEngine&) = delete;

    bool loadModel(const std::string& filePath);
    bool loadModelFromMemory(const uint8_t* data, size_t size);
    void unload();
    bool isLoaded() const { return mIsLoaded; }

    int tokenize(const char* text, size_t textLen, int32_t* outTokens, int maxTokens);
    int tokenizeTail(const char* text, size_t textLen, int32_t* outTokens, int maxTokens);

    bool forward(const int32_t* contextTokens, int numTokens, float* outHidden);
    bool forwardStep(int32_t token, float* outHidden);
    void resetState() { resetRecurrentState(); }
    void scoreCandidates(const float* hT, const int32_t* candidateIds, int numCandidates, float* outLogits);
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
    void resetRecurrentState();
    static void matvecTernary(const TernaryMatrix& mat, const float* in, float* out);
    static void rmsNorm(const float* in, const float* gamma, float* out, int dim);
    static void gelu(float* x, int dim);
    static float sigmoid(float x);

    bool mIsLoaded{false};

    // Hyperparameters
    int32_t mVocabSize{0};
    int32_t mDModel{0};
    int32_t mNHeads{0};
    int32_t mNLayers{0};
    int32_t mDK{0};
    int32_t mDRank{0};
    int32_t mMaxSeqLen{64};

    // SVD Token Embeddings
    float mEmbScale{1.0f};
    std::vector<int8_t> mEmbLatent; // (vocab_size * d_rank)
    std::vector<float> mProjW;      // (d_model * d_rank)

    // Layers & Normalization
    std::vector<KChebLayerWeights> mLayers;
    std::vector<float> mFinalNormGamma;

    // Recurrent States
    std::vector<KChebState> mStates;

    // BPE Vocab & Trie
    std::vector<std::string> mBpeVocab;
    std::unique_ptr<TrieNode> mBpeTrieRoot;
    std::vector<int32_t> mWordStartTokenIds;

    // Zero-allocation scratchpad buffers
    std::vector<float> mX;
    std::vector<float> mNormBuf;
    std::vector<float> mK, mV, mR, mRecOut;
    std::vector<float> mMlpInBuf, mMlpOutBuf;
    std::vector<float> mHLatent;
};

} // namespace micro_frontier
