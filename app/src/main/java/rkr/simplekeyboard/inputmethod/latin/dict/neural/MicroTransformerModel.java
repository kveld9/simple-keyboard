package rkr.simplekeyboard.inputmethod.latin.dict.neural;

import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Direct JNI interface for the high-performance C++ MicroBitNet (1.58-bit Ternary) Transformer.
 * Executes SIMD-vectorized inference (ARM NEON / SSE / AVX) with zero Java hot-path allocations.
 */
public final class MicroTransformerModel {

    private static final String TAG = "MicroTransformerModel";

    public static final int MAGIC_TRF2 = 0x54524632;      // "TRF2" (Ternary 1.58-bit BitNet format)
    public static final int MAGIC_TRF2_ALT = 0x32465254;  // ASCII "TRF2" LE
    public static final int MAX_SEQ_LEN = 32;
    public static final int UNK_TOKEN_ID = 1;
    public static final char WORD_START_CHAR = '\u2581';  // Lower One Eighth Block

    private static volatile boolean sLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("microtransformer");
            sLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native library 'microtransformer' not found.", e);
            sLibraryLoaded = false;
        }
    }

    public static boolean isNativeAvailable() {
        return sLibraryLoaded;
    }

    private long mNativeHandle = 0;

    public MicroTransformerModel() {
        if (sLibraryLoaded) {
            mNativeHandle = nativeCreate();
        }
    }

    public synchronized boolean loadModel(File modelFile) {
        if (mNativeHandle == 0 || modelFile == null || !modelFile.exists() || !modelFile.canRead()) {
            Log.e(TAG, "loadModel: Invalid or unreadable file: " + modelFile);
            return false;
        }
        return nativeLoadModel(mNativeHandle, modelFile.getAbsolutePath());
    }

    public synchronized boolean loadModelBuffer(ByteBuffer buffer) {
        if (mNativeHandle == 0 || buffer == null) {
            return false;
        }
        return nativeLoadModelBuffer(mNativeHandle, buffer);
    }

    public synchronized void unload() {
        if (mNativeHandle != 0) {
            nativeUnload(mNativeHandle);
        }
    }

    public boolean isLoaded() {
        return mNativeHandle != 0 && nativeIsLoaded(mNativeHandle);
    }

    public int tokenize(CharSequence text, int[] outTokens, int outOffset, int maxTokens) {
        if (mNativeHandle == 0 || text == null || outTokens == null || maxTokens <= 0 || outOffset < 0 || outOffset >= outTokens.length) {
            return 0;
        }
        if (outOffset == 0) {
            return nativeTokenize(mNativeHandle, text.toString(), outTokens, maxTokens);
        }
        int[] tmp = new int[maxTokens];
        int count = nativeTokenize(mNativeHandle, text.toString(), tmp, maxTokens);
        if (count > 0) {
            System.arraycopy(tmp, 0, outTokens, outOffset, count);
        }
        return count;
    }

    public int tokenize(CharSequence text, int[] outTokens, int maxTokens) {
        return tokenize(text, outTokens, 0, maxTokens);
    }

    public int tokenizeTail(CharSequence text, int[] outTokens, int maxTokens) {
        if (mNativeHandle == 0 || text == null || outTokens == null || maxTokens <= 0) {
            return 0;
        }
        return nativeTokenizeTail(mNativeHandle, text.toString(), outTokens, maxTokens);
    }

    public int[] tokenize(String text, int maxTokens) {
        if (mNativeHandle == 0 || text == null || maxTokens <= 0) {
            return new int[0];
        }
        int[] buffer = new int[maxTokens];
        int count = tokenize(text, buffer, 0, maxTokens);
        return Arrays.copyOf(buffer, count);
    }

    public boolean forward(int[] contextTokens, int numTokens, float[] outHidden) {
        if (mNativeHandle == 0 || contextTokens == null || numTokens <= 0 || outHidden == null) {
            return false;
        }
        return nativeForward(mNativeHandle, contextTokens, numTokens, outHidden);
    }

    public void scoreCandidates(float[] hT, int[] candidateIds, int numCandidates, float[] outLogits) {
        if (mNativeHandle == 0 || hT == null || candidateIds == null || outLogits == null || numCandidates <= 0) {
            return;
        }
        nativeScoreCandidates(mNativeHandle, hT, candidateIds, numCandidates, outLogits);
    }

    public int scoreTopK(float[] hT, int[] candidateIds, int numCandidates, int k,
                         int[] outTopTokens, float[] outTopScores) {
        if (mNativeHandle == 0 || hT == null || candidateIds == null || outTopTokens == null || outTopScores == null || numCandidates <= 0 || k <= 0) {
            return 0;
        }
        return nativeScoreTopK(mNativeHandle, hT, candidateIds, numCandidates, k, outTopTokens, outTopScores);
    }

    public int[] getWordStartTokenIds() {
        if (mNativeHandle == 0) return new int[0];
        return nativeGetWordStartTokenIds(mNativeHandle);
    }

    public String getTokenText(int tokenId) {
        if (mNativeHandle == 0) return "";
        return nativeGetTokenText(mNativeHandle, tokenId);
    }

    public int getVocabSize() {
        if (mNativeHandle == 0) return 0;
        return nativeGetVocabSize(mNativeHandle);
    }

    public int getModelDim() {
        if (mNativeHandle == 0) return 0;
        return nativeGetModelDim(mNativeHandle);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mNativeHandle != 0) {
                nativeDestroy(mNativeHandle);
                mNativeHandle = 0;
            }
        } finally {
            super.finalize();
        }
    }

    // --- Native JNI Method Declarations ---
    private static native long nativeCreate();
    private static native void nativeDestroy(long handle);
    private static native boolean nativeLoadModel(long handle, String filePath);
    private static native boolean nativeLoadModelBuffer(long handle, ByteBuffer buffer);
    private static native void nativeUnload(long handle);
    private static native boolean nativeIsLoaded(long handle);
    private static native int nativeTokenize(long handle, String text, int[] outTokens, int maxTokens);
    private static native int nativeTokenizeTail(long handle, String text, int[] outTokens, int maxTokens);
    private static native boolean nativeForward(long handle, int[] contextTokens, int numTokens, float[] outHidden);
    private static native void nativeScoreCandidates(long handle, float[] hT, int[] candidateIds, int numCandidates, float[] outLogits);
    private static native int nativeScoreTopK(long handle, float[] hT, int[] candidateIds, int numCandidates, int k, int[] outTopTokens, float[] outTopScores);
    private static native int[] nativeGetWordStartTokenIds(long handle);
    private static native String nativeGetTokenText(long handle, int tokenId);
    private static native int nativeGetVocabSize(long handle);
    private static native int nativeGetModelDim(long handle);
}
