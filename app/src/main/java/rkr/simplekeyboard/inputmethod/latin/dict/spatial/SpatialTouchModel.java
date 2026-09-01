package rkr.simplekeyboard.inputmethod.latin.dict.spatial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import rkr.simplekeyboard.inputmethod.keyboard.Key;
import rkr.simplekeyboard.inputmethod.keyboard.Keyboard;

public class SpatialTouchModel {

    public static final int MAX_KEYS = 64;
    public static final int MAX_NEIGHBORS = 16;
    public static final int MAX_CANDIDATES = 16;

    public static class SpatialKey {
        public char codePoint;
        public float centerX;
        public float centerY;
        public float width;
        public float height;
        public float invTwoSigmaX2;
        public float invTwoSigmaY2;
        public float maxDx;
        public float maxDy;
        public final int[] neighborIndices = new int[MAX_NEIGHBORS];
        public int neighborCount = 0;

        public void init(char codePoint, float centerX, float centerY, float width, float height) {
            this.codePoint = codePoint;
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;

            float sigmaX = width * 0.40f;
            float sigmaY = height * 0.45f;
            this.invTwoSigmaX2 = (sigmaX > 0) ? (1.0f / (2.0f * sigmaX * sigmaX)) : 0.0f;
            this.invTwoSigmaY2 = (sigmaY > 0) ? (1.0f / (2.0f * sigmaY * sigmaY)) : 0.0f;
            this.maxDx = width * 1.4f;
            this.maxDy = height * 1.4f;
            this.neighborCount = 0;
        }
    }

    private final SpatialKey[] mKeys = new SpatialKey[MAX_KEYS];
    private int mKeyCount = 0;

    // Fast lookup table for ASCII / Latin chars to key index
    private final int[] mCodeToKeyIndex = new int[65536];

    // Reusable candidate pool and result list for 0-allocation calls
    private final SpatialCandidate[] mPreallocatedCandidates = new SpatialCandidate[MAX_CANDIDATES];
    private final List<SpatialCandidate> mScratchCandidateList = new ArrayList<>(MAX_CANDIDATES);
    private final char[] mScratchChars = new char[MAX_CANDIDATES];
    private final float[] mScratchProbs = new float[MAX_CANDIDATES];
    private final float[] mScratchLogProbs = new float[MAX_CANDIDATES];

    public SpatialTouchModel() {
        for (int i = 0; i < MAX_KEYS; i++) {
            mKeys[i] = new SpatialKey();
        }
        for (int i = 0; i < MAX_CANDIDATES; i++) {
            mPreallocatedCandidates[i] = new SpatialCandidate();
        }
        Arrays.fill(mCodeToKeyIndex, -1);
    }

    public synchronized void setKeyboard(Keyboard keyboard) {
        mKeyCount = 0;
        Arrays.fill(mCodeToKeyIndex, -1);
        if (keyboard == null) {
            return;
        }
        for (Key key : keyboard.getSortedKeys()) {
            int code = key.getCode();
            // Only consider alphabetic keys
            if (Character.isLetter(code)) {
                if (mKeyCount < MAX_KEYS) {
                    mKeys[mKeyCount].init((char) code, key.getCenterX(), key.getCenterY(), key.getWidth(), key.getHeight());
                    mKeyCount++;
                }
            }
        }
        recomputeAdjacency();
    }

    public synchronized void addKey(char codePoint, float centerX, float centerY, float width, float height) {
        if (mKeyCount < MAX_KEYS) {
            mKeys[mKeyCount].init(codePoint, centerX, centerY, width, height);
            mKeyCount++;
            recomputeAdjacency();
        }
    }

    private void recomputeAdjacency() {
        Arrays.fill(mCodeToKeyIndex, -1);
        for (int i = 0; i < mKeyCount; i++) {
            SpatialKey key = mKeys[i];
            if (key.codePoint < mCodeToKeyIndex.length) {
                mCodeToKeyIndex[key.codePoint] = i;
                mCodeToKeyIndex[Character.toLowerCase(key.codePoint)] = i;
                mCodeToKeyIndex[Character.toUpperCase(key.codePoint)] = i;
            }
            key.neighborCount = 0;
        }

        for (int i = 0; i < mKeyCount; i++) {
            SpatialKey keyI = mKeys[i];
            for (int j = 0; j < mKeyCount; j++) {
                if (i == j) continue;
                SpatialKey keyJ = mKeys[j];
                float dx = Math.abs(keyJ.centerX - keyI.centerX);
                float dy = Math.abs(keyJ.centerY - keyI.centerY);
                float thresholdX = Math.max(keyI.width, keyJ.width) * 1.6f;
                float thresholdY = Math.max(keyI.height, keyJ.height) * 1.6f;
                if (dx <= thresholdX && dy <= thresholdY) {
                    if (keyI.neighborCount < keyI.neighborIndices.length) {
                        keyI.neighborIndices[keyI.neighborCount++] = j;
                    }
                }
            }
        }
    }

    public synchronized int getCandidatesForTouch(float touchX, float touchY, int fallbackCode,
            char[] outChars, float[] outProbs, float[] outLogProbs, int maxCandidates) {
        if (outChars == null || outProbs == null || outLogProbs == null || maxCandidates <= 0) {
            return 0;
        }
        int count = 0;

        int rootIdx = (fallbackCode >= 0 && fallbackCode < mCodeToKeyIndex.length) ? mCodeToKeyIndex[fallbackCode] : -1;
        if (rootIdx >= 0) {
            count = evaluateKey(mKeys[rootIdx], touchX, touchY, outChars, outProbs, outLogProbs, count, maxCandidates);
            SpatialKey rootKey = mKeys[rootIdx];
            for (int n = 0; n < rootKey.neighborCount && count < maxCandidates; n++) {
                int neighborIdx = rootKey.neighborIndices[n];
                count = evaluateKey(mKeys[neighborIdx], touchX, touchY, outChars, outProbs, outLogProbs, count, maxCandidates);
            }
        } else {
            for (int i = 0; i < mKeyCount && count < maxCandidates; i++) {
                count = evaluateKey(mKeys[i], touchX, touchY, outChars, outProbs, outLogProbs, count, maxCandidates);
            }
        }

        if (count == 0 && fallbackCode > 0 && Character.isLetter(fallbackCode)) {
            outChars[0] = (char) fallbackCode;
            outProbs[0] = 1.0f;
            outLogProbs[0] = 0.0f;
            count = 1;
        }

        // Sort descending by probability in-place (Insertion sort - zero allocations)
        for (int i = 1; i < count; i++) {
            char c = outChars[i];
            float p = outProbs[i];
            float lp = outLogProbs[i];
            int j = i - 1;
            while (j >= 0 && outProbs[j] < p) {
                outChars[j + 1] = outChars[j];
                outProbs[j + 1] = outProbs[j];
                outLogProbs[j + 1] = outLogProbs[j];
                j--;
            }
            outChars[j + 1] = c;
            outProbs[j + 1] = p;
            outLogProbs[j + 1] = lp;
        }

        return count;
    }

    private static int evaluateKey(SpatialKey key, float touchX, float touchY,
            char[] outChars, float[] outProbs, float[] outLogProbs, int currentCount, int maxCandidates) {
        if (currentCount >= maxCandidates) {
            return currentCount;
        }
        float dx = touchX - key.centerX;
        float dy = touchY - key.centerY;

        if (Math.abs(dx) > key.maxDx || Math.abs(dy) > key.maxDy) {
            return currentCount;
        }
        if (key.invTwoSigmaX2 <= 0 || key.invTwoSigmaY2 <= 0) {
            return currentCount;
        }

        float expPart = -((dx * dx * key.invTwoSigmaX2) + (dy * dy * key.invTwoSigmaY2));

        // ln(0.05) is approximately -2.99573227355f
        if (expPart > -2.99573227355f) {
            float prob = (float) Math.exp(expPart);
            outChars[currentCount] = key.codePoint;
            outProbs[currentCount] = prob;
            outLogProbs[currentCount] = expPart;
            return currentCount + 1;
        }
        return currentCount;
    }

    public synchronized List<SpatialCandidate> getCandidatesForTouch(float touchX, float touchY, int fallbackCode) {
        mScratchCandidateList.clear();
        int count = getCandidatesForTouch(touchX, touchY, fallbackCode, mScratchChars, mScratchProbs, mScratchLogProbs, MAX_CANDIDATES);
        for (int i = 0; i < count; i++) {
            mPreallocatedCandidates[i].set(mScratchChars[i], mScratchProbs[i], mScratchLogProbs[i]);
            mScratchCandidateList.add(mPreallocatedCandidates[i]);
        }
        return mScratchCandidateList;
    }
}

