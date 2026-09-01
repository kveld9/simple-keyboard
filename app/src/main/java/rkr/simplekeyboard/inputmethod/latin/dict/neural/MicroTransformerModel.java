package rkr.simplekeyboard.inputmethod.latin.dict.neural;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Pure Java multiplication-free inference engine for the MicroBitNet (1.58-bit Ternary) Transformer.
 * Loads TRF2 binary weights, unpacks 2-bit ternary weights {-1, 0, +1}, and executes
 * a 2-layer causal BitNet Transformer forward pass using purely integer additions/subtractions
 * and zero hot-path allocations.
 */
public final class MicroTransformerModel {

    private static final String TAG = "MicroTransformerModel";

    public static final int MAGIC_TRF2 = 0x54524632; // "TRF2" (Ternary 1.58-bit BitNet format)
    public static final int MAGIC_TRF2_ALT = 0x32465254; // ASCII "TRF2" LE
    public static final int MAX_SEQ_LEN = 16;
    public static final int UNK_TOKEN_ID = 1;
    public static final char WORD_START_CHAR = '\u2581'; // Lower One Eighth Block

    // Model Hyperparameters
    private int mVocabSize = 0;
    private int mDModel = 0;
    private int mNHeads = 0;
    private int mNLayers = 0;
    private float mScaleEmb = 1.0f;
    private float mScalePos = 1.0f;
    private float mScaleDot = 1.0f;

    // Weights (Dequantized to float32)
    // Weights (Ternary weights stored as byte {-1, 0, 1})
    private float[] mEmbeddings;       // vocab_size * d_model
    private float[] mPosEmb;           // MAX_SEQ_LEN * d_model
    private byte[][] mQkvW;            // [n_layers][(3 * d_model) * d_model]
    private byte[][] mProjW;           // [n_layers][d_model * d_model]
    private byte[][] mMlpUpW;          // [n_layers][d_ff * d_model]
    private byte[][] mMlpDownW;        // [n_layers][d_model * d_ff]
    private float[][] mGamma1Fused;    // [n_layers][d_model]
    private float[][] mGamma2Fused;    // [n_layers][d_model]
    private float[] mScaleProj;        // [n_layers]
    private float[] mScaleDown;        // [n_layers]

    // BPE Vocabulary & Trie
    private String[] mBpeVocab;
    private TrieNode mBpeTrieRoot;

    // Hyperparameter constants precomputed for fast forward pass
    private float mInvD = 1.0f / 64.0f;
    private float mScaleAttn = 0.25f;

    // Pre-allocated intermediate buffers for 0-allocation forward pass
    private final int[] mScratchTokenIds = new int[MAX_SEQ_LEN];
    private float[] mX;                // MAX_SEQ_LEN * d_model
    private float[] mNormed;           // MAX_SEQ_LEN * d_model
    private float[] mQKV;              // MAX_SEQ_LEN * (3 * d_model)
    private float[] mAttnOut;          // MAX_SEQ_LEN * d_model
    private float[] mMlpHid;           // MAX_SEQ_LEN * d_ff
    private float[] mMlpOut;           // MAX_SEQ_LEN * d_model
    private float[] mHT;               // d_model
    private float[] mAttnWeights;      // n_heads * MAX_SEQ_LEN * MAX_SEQ_LEN

    private boolean mIsLoaded = false;

    private static final class TrieNode {
        int tokenId = -1;
        char[] childChars;
        TrieNode[] childNodes;

        TrieNode getChild(char c) {
            if (childChars == null) {
                return null;
            }
            int len = childChars.length;
            for (int i = 0; i < len; i++) {
                if (childChars[i] == c) {
                    return childNodes[i];
                }
            }
            return null;
        }

        void addChild(char c, TrieNode node) {
            if (childChars == null) {
                childChars = new char[]{c};
                childNodes = new TrieNode[]{node};
                return;
            }
            int len = childChars.length;
            char[] newChars = Arrays.copyOf(childChars, len + 1);
            newChars[len] = c;
            TrieNode[] newNodes = Arrays.copyOf(childNodes, len + 1);
            newNodes[len] = node;
            childChars = newChars;
            childNodes = newNodes;
        }
    }

    public MicroTransformerModel() {
    }

    /**
     * Loads the model from a TRF2 (1.58-bit Ternary) binary file.
     *
     * @param modelFile The file containing the TRF2 weights and vocabulary.
     * @return true if the model was successfully loaded, false otherwise.
     */
    public synchronized boolean loadModel(File modelFile) {
        if (modelFile == null || !modelFile.exists() || !modelFile.canRead()) {
            Log.e(TAG, "loadModel: Invalid or unreadable file: " + modelFile);
            return false;
        }

        unload();

        try (FileInputStream fis = new FileInputStream(modelFile);
             FileChannel channel = fis.getChannel()) {

            long fileSize = channel.size();
            if (fileSize < 64) {
                Log.e(TAG, "loadModel: File size too small (" + fileSize + " bytes)");
                return false;
            }

            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // 1. Read Header (64 bytes)
            int magic = buffer.getInt(0x00);
            boolean validMagic = (magic == MAGIC_TRF2 || magic == MAGIC_TRF2_ALT ||
                    (buffer.get(0) == 'T' && buffer.get(1) == 'R' && buffer.get(2) == 'F' && buffer.get(3) == '2'));
            if (!validMagic) {
                Log.e(TAG, String.format("loadModel: Invalid magic number (expected TRF2): 0x%08X", magic));
                return false;
            }

            int version = buffer.getInt(0x04);
            if (version != 2) {
                Log.e(TAG, "loadModel: Unsupported version (expected 2 for TRF2): " + version);
                return false;
            }

            mVocabSize = buffer.getInt(0x08);
            mDModel = buffer.getInt(0x0C);
            mNHeads = buffer.getInt(0x10);
            mNLayers = buffer.getInt(0x14);
            int offBpe = buffer.getInt(0x18);
            int offEmb = buffer.getInt(0x1C);
            int offPos = buffer.getInt(0x20);
            int offLayer0 = buffer.getInt(0x24);
            mScaleEmb = buffer.getFloat(0x28);
            mScalePos = buffer.getFloat(0x2C);
            mScaleDot = buffer.getFloat(0x30);

            if (mVocabSize <= 0 || mDModel <= 0 || mNHeads <= 0 || mNLayers <= 0 || (mDModel % mNHeads) != 0) {
                Log.e(TAG, "loadModel: Invalid header dimensions: vocab=" + mVocabSize +
                        ", d_model=" + mDModel + ", n_heads=" + mNHeads + ", n_layers=" + mNLayers);
                return false;
            }

            mInvD = 1.0f / (float) mDModel;
            mScaleAttn = (float) (1.0 / Math.sqrt((double) mDModel / mNHeads));
            int dFf = 2 * mDModel;

            // 2. Read BPE Table at offBpe
            if (offBpe < 64 || offBpe >= fileSize) {
                Log.e(TAG, "loadModel: Invalid off_bpe: " + offBpe);
                return false;
            }
            buffer.position(offBpe);
            int bpeVocabSize = buffer.getInt();
            if (bpeVocabSize != mVocabSize) {
                Log.w(TAG, "loadModel: BPE vocab size (" + bpeVocabSize + ") differs from header vocab (" + mVocabSize + ")");
            }

            int numBpeTokens = Math.min(bpeVocabSize, mVocabSize);
            int[] offsets = new int[numBpeTokens];
            for (int i = 0; i < numBpeTokens; i++) {
                offsets[i] = buffer.getInt();
            }

            int stringPoolStart = offBpe + 4 + bpeVocabSize * 4;
            mBpeVocab = new String[mVocabSize];
            ByteArrayOutputStream baos = new ByteArrayOutputStream(64);

            for (int i = 0; i < numBpeTokens; i++) {
                int strPos = stringPoolStart + offsets[i];
                if (strPos < 0 || strPos >= fileSize) {
                    Log.w(TAG, "loadModel: BPE token " + i + " string offset out of bounds: " + strPos);
                    mBpeVocab[i] = "";
                    continue;
                }
                buffer.position(strPos);
                baos.reset();
                byte b;
                while (buffer.hasRemaining() && (b = buffer.get()) != 0) {
                    baos.write(b);
                }
                mBpeVocab[i] = new String(baos.toByteArray(), StandardCharsets.UTF_8).replace(WORD_START_CHAR, ' ');
            }
            for (int i = numBpeTokens; i < mVocabSize; i++) {
                mBpeVocab[i] = "";
            }

            buildBpeTrie();

            // 3. Read Embeddings at offEmb (vocab_size * d_model bytes INT8)
            if (offEmb < 64 || offEmb >= fileSize) {
                Log.e(TAG, "loadModel: Invalid off_emb: " + offEmb);
                return false;
            }
            buffer.position(offEmb);
            int embTotal = mVocabSize * mDModel;
            mEmbeddings = new float[embTotal];
            for (int i = 0; i < embTotal; i++) {
                mEmbeddings[i] = ((float) buffer.get()) * mScaleEmb;
            }

            // 4. Read Positional Embeddings at offPos (16 * d_model bytes INT8)
            if (offPos < 64 || offPos >= fileSize) {
                Log.e(TAG, "loadModel: Invalid off_pos: " + offPos);
                return false;
            }
            buffer.position(offPos);
            int posTotal = MAX_SEQ_LEN * mDModel;
            mPosEmb = new float[posTotal];
            for (int i = 0; i < posTotal; i++) {
                mPosEmb[i] = ((float) buffer.get()) * mScalePos;
            }

            // 5. Read Transformer Layers from offLayer0
            if (offLayer0 < 64 || offLayer0 >= fileSize) {
                Log.e(TAG, "loadModel: Invalid off_layer0: " + offLayer0);
                return false;
            }

            mQkvW = new byte[mNLayers][(3 * mDModel) * mDModel];
            mProjW = new byte[mNLayers][mDModel * mDModel];
            mMlpUpW = new byte[mNLayers][dFf * mDModel];
            mMlpDownW = new byte[mNLayers][mDModel * dFf];
            mGamma1Fused = new float[mNLayers][mDModel];
            mGamma2Fused = new float[mNLayers][mDModel];
            mScaleProj = new float[mNLayers];
            mScaleDown = new float[mNLayers];

            int qkvPackedBytes = ((3 * mDModel) * mDModel + 3) / 4;
            int projPackedBytes = (mDModel * mDModel + 3) / 4;
            int mlpUpPackedBytes = (dFf * mDModel + 3) / 4;
            int mlpDownPackedBytes = (mDModel * dFf + 3) / 4;
            int rawLayerBytes = qkvPackedBytes + projPackedBytes + mlpUpPackedBytes + mlpDownPackedBytes +
                    (mDModel * 4) + (mDModel * 4) + 4 + 4;
            int layerStride = (rawLayerBytes + 63) & ~63;

            for (int l = 0; l < mNLayers; l++) {
                int layerStart = offLayer0 + l * layerStride;
                if (layerStart + rawLayerBytes > fileSize) {
                    Log.e(TAG, "loadModel: Layer " + l + " exceeds file bounds: " + layerStart);
                    return false;
                }
                buffer.position(layerStart);

                // 1. QKV weights (Ternary 2-bit packed)
                int qkvCount = (3 * mDModel) * mDModel;
                readTernaryWeights(buffer, mQkvW[l], qkvCount);

                // 2. Proj weights (Ternary 2-bit packed)
                int projCount = mDModel * mDModel;
                readTernaryWeights(buffer, mProjW[l], projCount);

                // 3. MLP up weights (Ternary 2-bit packed)
                int mlpUpCount = dFf * mDModel;
                readTernaryWeights(buffer, mMlpUpW[l], mlpUpCount);

                // 4. MLP down weights (Ternary 2-bit packed)
                int mlpDownCount = mDModel * dFf;
                readTernaryWeights(buffer, mMlpDownW[l], mlpDownCount);

                // 5. Gamma1 fused: D floats
                for (int i = 0; i < mDModel; i++) {
                    mGamma1Fused[l][i] = buffer.getFloat();
                }

                // 6. Gamma2 fused: D floats
                for (int i = 0; i < mDModel; i++) {
                    mGamma2Fused[l][i] = buffer.getFloat();
                }

                // 7. Scale Proj & Scale Down (1 float each)
                mScaleProj[l] = buffer.getFloat();
                mScaleDown[l] = buffer.getFloat();
            }

            // 6. Pre-allocate intermediate buffers for 0-allocation forward execution
            mX = new float[MAX_SEQ_LEN * mDModel];
            mNormed = new float[MAX_SEQ_LEN * mDModel];
            mQKV = new float[MAX_SEQ_LEN * (3 * mDModel)];
            mAttnOut = new float[MAX_SEQ_LEN * mDModel];
            mMlpHid = new float[MAX_SEQ_LEN * dFf];
            mMlpOut = new float[MAX_SEQ_LEN * mDModel];
            mHT = new float[mDModel];
            mAttnWeights = new float[mNHeads * MAX_SEQ_LEN * MAX_SEQ_LEN];

            mIsLoaded = true;
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "loadModel: Failed to load model file: " + modelFile, e);
            unload();
            return false;
        }
    }

    private static void readTernaryWeights(MappedByteBuffer buffer, byte[] dest, int count) {
        // Unpack 4 ternary {-1, 0, +1} weights per byte
        int packedBytes = (count + 3) / 4;
        int destIdx = 0;
        for (int b = 0; b < packedBytes; b++) {
            int val = buffer.get() & 0xFF;
            for (int k = 0; k < 4 && destIdx < count; k++) {
                int code = (val >> (k * 2)) & 0x03;
                if (code == 0x01) {
                    dest[destIdx++] = (byte) 1;
                } else if (code == 0x03) {
                    dest[destIdx++] = (byte) -1;
                } else {
                    dest[destIdx++] = (byte) 0;
                }
            }
        }
    }

    private void buildBpeTrie() {
        mBpeTrieRoot = new TrieNode();
        if (mBpeVocab == null) {
            return;
        }
        for (int id = 0; id < mVocabSize; id++) {
            String piece = mBpeVocab[id];
            if (piece == null || piece.isEmpty()) {
                continue;
            }
            TrieNode current = mBpeTrieRoot;
            int len = piece.length();
            for (int i = 0; i < len; i++) {
                char c = piece.charAt(i);
                TrieNode child = current.getChild(c);
                if (child == null) {
                    child = new TrieNode();
                    current.addChild(c, child);
                }
                current = child;
            }
            current.tokenId = id;
        }
    }

    /**
     * Checks if the model is currently loaded and ready for inference.
     */
    public boolean isLoaded() {
        return mIsLoaded;
    }

    /**
     * Releases all memory buffers and model weights.
     */
    public synchronized void unload() {
        mIsLoaded = false;
        mVocabSize = 0;
        mDModel = 0;
        mNHeads = 0;
        mNLayers = 0;
        mScaleEmb = 1.0f;
        mScalePos = 1.0f;
        mScaleDot = 1.0f;

        mEmbeddings = null;
        mPosEmb = null;
        mQkvW = null;
        mProjW = null;
        mMlpUpW = null;
        mMlpDownW = null;
        mGamma1Fused = null;
        mGamma2Fused = null;
        mScaleProj = null;
        mScaleDown = null;

        mBpeVocab = null;
        mBpeTrieRoot = null;

        mX = null;
        mNormed = null;
        mQKV = null;
        mAttnOut = null;
        mMlpHid = null;
        mMlpOut = null;
        mHT = null;
        mAttnWeights = null;
    }

    private static char normalizeCharForTrie(char c) {
        if (c == WORD_START_CHAR || Character.isWhitespace(c)) {
            return ' ';
        }
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        switch (c) {
            case 'Á': return 'á';
            case 'É': return 'é';
            case 'Í': return 'í';
            case 'Ó': return 'ó';
            case 'Ú': return 'ú';
            case 'Ü': return 'ü';
            case 'Ñ': return 'ñ';
            default: return c;
        }
    }

    /**
     * Tokenizes a text into BPE token IDs writing directly to outTokens without String allocations.
     *
     * @param text The input text to tokenize.
     * @param outTokens Destination array for token IDs.
     * @param outOffset Starting offset in outTokens.
     * @param maxTokens Maximum number of tokens to write.
     * @return Number of tokens written.
     */
    public synchronized int tokenize(CharSequence text, int[] outTokens, int outOffset, int maxTokens) {
        if (!mIsLoaded) {
            Log.w(TAG, "tokenize: Model not loaded");
            return 0;
        }
        if (text == null || text.length() == 0 || outTokens == null || maxTokens <= 0 || outOffset < 0 || outOffset >= outTokens.length) {
            return 0;
        }

        final int textLen = text.length();
        final char firstChar = text.charAt(0);
        final boolean hasVirtualStart = (firstChar != ' ' && firstChar != WORD_START_CHAR);
        final int virtualLen = hasVirtualStart ? textLen + 1 : textLen;

        final int limit = Math.min(maxTokens, outTokens.length - outOffset);
        int count = 0;
        int pos = 0;

        while (pos < virtualLen && count < limit) {
            int bestLen = 0;
            int bestId = UNK_TOKEN_ID;

            if (mBpeTrieRoot != null) {
                TrieNode node = mBpeTrieRoot;
                for (int i = pos; i < virtualLen; i++) {
                    char rawChar = hasVirtualStart ? ((i == 0) ? ' ' : text.charAt(i - 1)) : text.charAt(i);
                    char c = normalizeCharForTrie(rawChar);

                    TrieNode child = node.getChild(c);
                    if (child == null) {
                        break;
                    }
                    node = child;
                    if (node.tokenId != -1) {
                        bestLen = (i - pos) + 1;
                        bestId = node.tokenId;
                    }
                }
            }

            if (bestLen == 0) {
                bestLen = 1;
                bestId = UNK_TOKEN_ID;
            }
            outTokens[outOffset + count++] = bestId;
            pos += bestLen;
        }

        return count;
    }

    public synchronized int tokenize(CharSequence text, int[] outTokens, int maxTokens) {
        return tokenize(text, outTokens, 0, maxTokens);
    }

    private final int[] mTailRing = new int[256];

    /**
     * Tokenizes text and stores only the trailing tokens (the most recent context),
     * scanning through the full sequence with a ring buffer to avoid allocations.
     *
     * @param text The input sequence.
     * @param outTokens Array where the tail tokens will be written.
     * @param maxTokens Maximum number of tail tokens to return.
     * @return Number of tokens written to outTokens.
     */
    public synchronized int tokenizeTail(CharSequence text, int[] outTokens, int maxTokens) {
        if (!mIsLoaded || text == null || maxTokens <= 0 || outTokens == null) return 0;
        int len = text.length();
        while (len > 0 && Character.isWhitespace(text.charAt(len - 1))) {
            len--;
        }
        if (len <= 0) return 0;

        // Limit scanning window to the last 160 characters (plenty for 16 tokens) to avoid scanning whole documents
        final int charWindow = 160;
        final int windowStart = Math.max(0, len - charWindow);
        final CharSequence scanText = (windowStart > 0) ? text.subSequence(windowStart, len) : text;
        final int scanLen = scanText.length();

        final int ringCap = mTailRing.length;
        final int targetKeep = Math.min(maxTokens, Math.min(ringCap, outTokens.length));

        final char firstChar = scanText.charAt(0);
        final boolean hasVirtualStart = (firstChar != ' ' && firstChar != WORD_START_CHAR);
        final int virtualLen = hasVirtualStart ? (scanLen + 1) : scanLen;

        int pos = 0;
        int count = 0;

        while (pos < virtualLen) {
            int bestLen = 0;
            int bestId = UNK_TOKEN_ID;

            if (mBpeTrieRoot != null) {
                TrieNode node = mBpeTrieRoot;
                for (int i = pos; i < virtualLen; i++) {
                    char rawChar = hasVirtualStart ? ((i == 0) ? ' ' : scanText.charAt(i - 1)) : scanText.charAt(i);
                    char c = normalizeCharForTrie(rawChar);

                    TrieNode child = node.getChild(c);
                    if (child == null) {
                        break;
                    }
                    node = child;
                    if (node.tokenId != -1) {
                        bestLen = (i - pos) + 1;
                        bestId = node.tokenId;
                    }
                }
            }

            if (bestLen == 0) {
                bestLen = 1;
                bestId = UNK_TOKEN_ID;
            }

            mTailRing[count % ringCap] = bestId;
            count++;
            pos += bestLen;
        }

        int keep = Math.min(count, targetKeep);
        if (keep <= 0) return 0;

        int start = (count - keep) % ringCap;
        for (int i = 0; i < keep; i++) {
            outTokens[i] = mTailRing[(start + i) % ringCap];
        }
        return keep;
    }

    /**
     * Tokenizes a text string into BPE token IDs using greedy longest match.
     *
     * @param text The input string to tokenize.
     * @param maxTokens Maximum number of tokens to return.
     * @return Array of token IDs of length at most maxTokens.
     */
    public synchronized int[] tokenize(String text, int maxTokens) {
        if (!mIsLoaded || text == null || maxTokens <= 0) {
            return new int[0];
        }
        int[] buffer = new int[maxTokens];
        int count = tokenize(text, buffer, 0, maxTokens);
        return Arrays.copyOf(buffer, count);
    }

    /**
     * Executes the forward pass of the micro-transformer and writes the final hidden state vector
     * h_T into outHidden.
     *
     * @param contextTokens Array of BPE token IDs.
     * @param numTokens Number of valid tokens in contextTokens.
     * @param outHidden Output buffer for h_T (size at least d_model).
     * @return True if successful, false on error.
     */
    public synchronized boolean forward(int[] contextTokens, int numTokens, float[] outHidden) {
        if (!mIsLoaded) {
            Log.w(TAG, "forward: Model not loaded");
            return false;
        }
        if (contextTokens == null || numTokens <= 0 || outHidden == null || outHidden.length < mDModel) {
            Log.w(TAG, "forward: Invalid arguments (numTokens=" + numTokens + ")");
            return false;
        }

        final int start = Math.max(0, numTokens - MAX_SEQ_LEN);
        final int T = numTokens - start;
        final int D = mDModel;
        final float invD = 1.0f / (float) D;
        final int H = mNHeads;
        final int dk = D / H;
        final int dFf = 2 * D;

        // 1. Token Embeddings + Positional Embeddings (taking tail of contextTokens)
        for (int t = 0; t < T; t++) {
            int tok = contextTokens[start + t];
            if (tok < 0 || tok >= mVocabSize) {
                tok = UNK_TOKEN_ID;
            }
            int embOffset = tok * D;
            int posOffset = t * D;
            int xOffset = t * D;
            for (int d = 0; d < D; d++) {
                mX[xOffset + d] = mEmbeddings[embOffset + d] + mPosEmb[posOffset + d];
            }
        }

        // 2. Transformer layers
        for (int layer = 0; layer < mNLayers; layer++) {
            // 2a. RMSNorm pre-attention with fused gamma
            float[] gamma1 = mGamma1Fused[layer];
            for (int t = 0; t < T; t++) {
                int offset = t * D;
                float sumSq = 0.0f;
                for (int d = 0; d < D; d++) {
                    float val = mX[offset + d];
                    sumSq += val * val;
                }
                float invRms = (float) (1.0 / Math.sqrt(sumSq * invD + 1e-5f));
                for (int d = 0; d < D; d++) {
                    mNormed[offset + d] = mX[offset + d] * invRms * gamma1[d];
                }
            }

            // 2b. QKV projection: mQKV[t, :] = mNormed[t, :] * qkv_weight^T (Ternary additions/subtractions)
            // qkv_weight has shape (3*D, D), row-major, elements in {-1, 0, +1}
            byte[] qkvW = mQkvW[layer];
            int outDimQKV = 3 * D;
            for (int t = 0; t < T; t++) {
                int normedOffset = t * D;
                int qkvRowOffset = t * outDimQKV;
                for (int j = 0; j < outDimQKV; j++) {
                    int wOffset = j * D;
                    float sum = 0.0f;
                    for (int k = 0; k < D; k++) {
                        byte w = qkvW[wOffset + k];
                        if (w == 1) {
                            sum += mNormed[normedOffset + k];
                        } else if (w == -1) {
                            sum -= mNormed[normedOffset + k];
                        }
                    }
                    mQKV[qkvRowOffset + j] = sum;
                }
            }

            // 2c. Causal Multi-Head Self-Attention
            Arrays.fill(mAttnOut, 0, T * D, 0.0f);
            final float scaleAttn = mScaleAttn;

            for (int h = 0; h < H; h++) {
                int qOff = h * dk;
                int kOff = D + h * dk;
                int vOff = 2 * D + h * dk;

                for (int i = 0; i < T; i++) {
                    int qRowOffset = i * outDimQKV + qOff;
                    float maxVal = -Float.MAX_VALUE;

                    // Compute attention scores for j <= i
                    for (int j = 0; j <= i; j++) {
                        int kRowOffset = j * outDimQKV + kOff;
                        float dot = 0.0f;
                        for (int d = 0; d < dk; d++) {
                            dot += mQKV[qRowOffset + d] * mQKV[kRowOffset + d];
                        }
                        dot *= scaleAttn;
                        int weightIdx = (h * MAX_SEQ_LEN + i) * MAX_SEQ_LEN + j;
                        mAttnWeights[weightIdx] = dot;
                        if (dot > maxVal) {
                            maxVal = dot;
                        }
                    }

                    // Softmax
                    float sumExp = 0.0f;
                    for (int j = 0; j <= i; j++) {
                        int weightIdx = (h * MAX_SEQ_LEN + i) * MAX_SEQ_LEN + j;
                        float val = (float) Math.exp(mAttnWeights[weightIdx] - maxVal);
                        mAttnWeights[weightIdx] = val;
                        sumExp += val;
                    }
                    float invSum = (sumExp > 0.0f) ? (1.0f / sumExp) : 0.0f;

                    // Weighted sum of values -> output[i, h*dk + d]
                    int outRowOffset = i * D + h * dk;
                    for (int j = 0; j <= i; j++) {
                        int weightIdx = (h * MAX_SEQ_LEN + i) * MAX_SEQ_LEN + j;
                        float w = mAttnWeights[weightIdx] * invSum;
                        int vRowOffset = j * outDimQKV + vOff;
                        for (int d = 0; d < dk; d++) {
                            mAttnOut[outRowOffset + d] += w * mQKV[vRowOffset + d];
                        }
                    }
                }
            }

            // 2d. Output projection + Residual: mX += (mAttnOut * proj_weight^T) * s_proj
            // proj_weight has shape (D, D), row-major, elements in {-1, 0, +1}
            byte[] projW = mProjW[layer];
            final float sProj = (mScaleProj != null && layer < mScaleProj.length) ? mScaleProj[layer] : 1.0f;
            for (int t = 0; t < T; t++) {
                int attnOffset = t * D;
                int xOffset = t * D;
                for (int j = 0; j < D; j++) {
                    int wOffset = j * D;
                    float sum = 0.0f;
                    for (int k = 0; k < D; k++) {
                        byte w = projW[wOffset + k];
                        if (w == 1) {
                            sum += mAttnOut[attnOffset + k];
                        } else if (w == -1) {
                            sum -= mAttnOut[attnOffset + k];
                        }
                    }
                    mX[xOffset + j] += sum * sProj;
                }
            }

            // 2e. RMSNorm pre-MLP with fused gamma
            float[] gamma2 = mGamma2Fused[layer];
            for (int t = 0; t < T; t++) {
                int offset = t * D;
                float sumSq = 0.0f;
                for (int d = 0; d < D; d++) {
                    float val = mX[offset + d];
                    sumSq += val * val;
                }
                float invRms = (float) (1.0 / Math.sqrt(sumSq * invD + 1e-5f));
                for (int d = 0; d < D; d++) {
                    mNormed[offset + d] = mX[offset + d] * invRms * gamma2[d];
                }
            }

            // 2f. MLP Forward:
            // up = ReLU(mNormed * mlp_up^T) (shape: T x d_ff, Ternary additions/subtractions)
            // mlp_up has shape (d_ff, D), elements in {-1, 0, +1}
            byte[] mlpUpW = mMlpUpW[layer];
            for (int t = 0; t < T; t++) {
                int normedOffset = t * D;
                int hidOffset = t * dFf;
                for (int j = 0; j < dFf; j++) {
                    int wOffset = j * D;
                    float sum = 0.0f;
                    for (int k = 0; k < D; k++) {
                        byte w = mlpUpW[wOffset + k];
                        if (w == 1) {
                            sum += mNormed[normedOffset + k];
                        } else if (w == -1) {
                            sum -= mNormed[normedOffset + k];
                        }
                    }
                    mMlpHid[hidOffset + j] = (sum > 0.0f) ? sum : 0.0f; // ReLU
                }
            }

            // down = mMlpHid * mlp_down^T (shape: T x D, Ternary additions/subtractions)
            // mlp_down has shape (D, d_ff), elements in {-1, 0, +1}
            // Residual: mX += down * s_down
            byte[] mlpDownW = mMlpDownW[layer];
            final float sDown = (mScaleDown != null && layer < mScaleDown.length) ? mScaleDown[layer] : 1.0f;
            for (int t = 0; t < T; t++) {
                int hidOffset = t * dFf;
                int xOffset = t * D;
                for (int j = 0; j < D; j++) {
                    int wOffset = j * dFf;
                    float sum = 0.0f;
                    for (int k = 0; k < dFf; k++) {
                        byte w = mlpDownW[wOffset + k];
                        if (w == 1) {
                            sum += mMlpHid[hidOffset + k];
                        } else if (w == -1) {
                            sum -= mMlpHid[hidOffset + k];
                        }
                    }
                    mX[xOffset + j] += sum * sDown;
                }
            }
        }

        // 3. RMSNorm final on the last token (t = T - 1)
        int lastTokenOffset = (T - 1) * D;
        float sumSq = 0.0f;
        for (int d = 0; d < D; d++) {
            float val = mX[lastTokenOffset + d];
            sumSq += val * val;
        }
        float invRms = (float) (1.0 / Math.sqrt(sumSq * invD + 1e-5f));
        for (int d = 0; d < D; d++) {
            outHidden[d] = mX[lastTokenOffset + d] * invRms;
        }

        return true;
    }

    private boolean isBadCandidate(int tokenId) {
        if (tokenId < 0 || tokenId >= mVocabSize || tokenId == UNK_TOKEN_ID || tokenId == 0) {
            return true;
        }
        if (mBpeVocab != null && tokenId < mBpeVocab.length) {
            String s = mBpeVocab[tokenId];
            if (s == null || s.isEmpty()) return true;
            if (s.length() >= 2 && s.charAt(0) == '<' && s.charAt(s.length() - 1) == '>') {
                return true;
            }
        }
        return false;
    }

    /**
     * Scores candidate tokens by computing dot(h_T, embedding[candidateId]) * scale_dot.
     *
     * @param hT Hidden state vector of length d_model (output of forward pass).
     * @param candidateIds Array of candidate BPE token IDs.
     * @param numCandidates Number of valid candidates in candidateIds.
     * @param outLogits Output array for scores (size at least numCandidates).
     */
    public synchronized void scoreCandidates(float[] hT, int[] candidateIds, int numCandidates, float[] outLogits) {
        if (!mIsLoaded || mEmbeddings == null) {
            return;
        }
        if (hT == null || candidateIds == null || outLogits == null || numCandidates <= 0) {
            return;
        }

        int D = mDModel;
        int n = Math.min(numCandidates, Math.min(candidateIds.length, outLogits.length));
        float scale = mScaleDot;

        for (int c = 0; c < n; c++) {
            int candId = candidateIds[c];
            if (isBadCandidate(candId)) {
                outLogits[c] = -Float.MAX_VALUE;
                continue;
            }
            int embOffset = candId * D;
            float dot = 0.0f;
            for (int d = 0; d < D; d++) {
                dot += hT[d] * mEmbeddings[embOffset + d];
            }
            outLogits[c] = dot * scale;
        }
    }

    /**
     * Returns the text of a BPE token given its ID, cleaning the word-start marker.
     *
     * @param tokenId The BPE token ID.
     * @return Decoded token text with word-start marker replaced by space, or empty string on error.
     */
    public synchronized String getTokenText(int tokenId) {
        if (!mIsLoaded || mBpeVocab == null) {
            return "";
        }
        if (tokenId < 0 || tokenId >= mBpeVocab.length) {
            return "";
        }
        final String piece = mBpeVocab[tokenId];
        return (piece != null) ? piece : "";
    }

    public int getVocabSize() {
        return mVocabSize;
    }

    public int getModelDim() {
        return mDModel;
    }
}
