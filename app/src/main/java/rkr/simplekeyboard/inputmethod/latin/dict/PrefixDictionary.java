package rkr.simplekeyboard.inputmethod.latin.dict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

/**
 * Lightweight in-memory Trie dictionary with Accent-Folding, Physical Proximity scoring,
 * Long Word correction bonuses, and Bigram context support.
 */
public final class PrefixDictionary {
    public static final int BASE_LEARNED_FREQUENCY = 250;

    private static final char[] EMPTY_CHARS = new char[0];
    private static final TrieNode[] EMPTY_CHILDREN = new TrieNode[0];
    private static final String[] EMPTY_WORDS = new String[0];
    private static final short[] EMPTY_FREQS = new short[0];

    private static final class TrieNode {
        char[] keys = EMPTY_CHARS;
        TrieNode[] children = EMPTY_CHILDREN;
        String[] words = EMPTY_WORDS;
        short[] freqs = EMPTY_FREQS;

        TrieNode getChild(final char c) {
            final char[] k = keys;
            for (int i = 0; i < k.length; i++) {
                if (k[i] == c) {
                    return children[i];
                }
            }
            return null;
        }

        TrieNode getOrCreateChild(final char c) {
            final char[] k = keys;
            for (int i = 0; i < k.length; i++) {
                if (k[i] == c) {
                    return children[i];
                }
            }
            final int len = k.length;
            final char[] newK = new char[len + 1];
            final TrieNode[] newC = new TrieNode[len + 1];
            System.arraycopy(k, 0, newK, 0, len);
            System.arraycopy(children, 0, newC, 0, len);
            newK[len] = c;
            final TrieNode child = new TrieNode();
            newC[len] = child;
            this.keys = newK;
            this.children = newC;
            return child;
        }

        boolean addWord(final String word, final int freq) {
            final short sFreq = (short) Math.min(Short.MAX_VALUE, Math.max(1, freq));
            final String[] w = words;
            for (int i = 0; i < w.length; i++) {
                if (w[i].equalsIgnoreCase(word)) {
                    if (sFreq > freqs[i]) {
                        freqs[i] = sFreq;
                        sortWords();
                    }
                    return false;
                }
            }
            final int len = w.length;
            final String[] newW = new String[len + 1];
            final short[] newF = new short[len + 1];
            System.arraycopy(w, 0, newW, 0, len);
            System.arraycopy(freqs, 0, newF, 0, len);
            newW[len] = word;
            newF[len] = sFreq;
            this.words = newW;
            this.freqs = newF;
            sortWords();
            return true;
        }

        private void sortWords() {
            for (int i = 1; i < words.length; i++) {
                final String w = words[i];
                final short f = freqs[i];
                int j = i - 1;
                while (j >= 0 && freqs[j] < f) {
                    words[j + 1] = words[j];
                    freqs[j + 1] = freqs[j];
                    j--;
                }
                words[j + 1] = w;
                freqs[j + 1] = f;
            }
        }
    }

    public static final class ScoredWord implements Comparable<ScoredWord> {
        public final String word;
        public final int frequency;
        public final float score;

        public ScoredWord(String word, int frequency, float score) {
            this.word = word;
            this.frequency = frequency;
            this.score = score;
        }

        @Override
        public int compareTo(ScoredWord other) {
            return Float.compare(other.score, this.score);
        }
    }

    private TrieNode mRoot = new TrieNode();
    private int mWordCount = 0;
    private float mAutoCorrectionThreshold = 1.0f;
    private final Map<String, Map<String, Short>> mTrigrams = new HashMap<>();
    private final Map<String, Map<String, Short>> mBigrams = new HashMap<>();
    private final List<ScoredWord> mTopWords = new ArrayList<>();

    public PrefixDictionary() {
    }

    public synchronized void setAutoCorrectionThreshold(final float threshold) {
        this.mAutoCorrectionThreshold = threshold;
    }

    public synchronized float getAutoCorrectionThreshold() {
        return mAutoCorrectionThreshold;
    }

    public synchronized void copyFrom(final PrefixDictionary other) {
        if (other != null) {
            synchronized (other) {
                this.mRoot = other.mRoot;
                this.mWordCount = other.mWordCount;
                this.mAutoCorrectionThreshold = other.mAutoCorrectionThreshold;
                this.mTrigrams.clear();
                for (Map.Entry<String, Map<String, Short>> entry : other.mTrigrams.entrySet()) {
                    this.mTrigrams.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
                this.mBigrams.clear();
                for (Map.Entry<String, Map<String, Short>> entry : other.mBigrams.entrySet()) {
                    this.mBigrams.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
                this.mTopWords.clear();
                this.mTopWords.addAll(other.mTopWords);
            }
        }
    }

    public static String stripAccents(final String s) {
        return StringUtils.stripAccents(s);
    }

    /**
     * Calculates the weighted Damerau-Levenshtein distance between two strings,
     * incorporating physical keyboard key proximity.
     */
    public static float computeWeightedDistance(final String s1, final String s2) {
        if (s1 == null && s2 == null) return 0.0f;
        if (s1 == null || s1.isEmpty()) return s2 == null ? 0.0f : s2.length();
        if (s2 == null || s2.isEmpty()) return s1.length();

        final int n = s1.length();
        final int m = s2.length();
        final float[][] d = new float[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= n; i++) {
            final char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                final char c2 = s2.charAt(j - 1);
                final float cost = (c1 == c2) ? 0.0f : ProximityKeyMap.getDistanceWeight(c1, c2);

                float min = Math.min(d[i - 1][j] + 1.0f, d[i][j - 1] + 1.0f);
                min = Math.min(min, d[i - 1][j - 1] + cost);

                if (i > 1 && j > 1 && c1 == s2.charAt(j - 2) && s1.charAt(i - 2) == c2) {
                    min = Math.min(min, d[i - 2][j - 2] + 0.5f);
                }
                d[i][j] = min;
            }
        }
        return d[n][m];
    }

    /**
     * Bonus for long words (>6 characters) inspired by LeanType so typos in longer words
     * are not unfairly penalized.
     */
    public static float getLongWordCorrectionBonus(final String typed, final String candidate) {
        if (typed == null || candidate == null) {
            return 0.0f;
        }
        final int maxLen = Math.max(typed.length(), candidate.length());
        if (maxLen > 6) {
            return (maxLen - 6) * 5.0f;
        }
        return 0.0f;
    }

    /**
     * Calculates normalized candidate score based on physical key proximity,
     * base dictionary frequency, and length bonus.
     */
    public static float calcNormalizedScore(final String typed, final String candidate, final int candidateFreq) {
        if (typed == null || candidate == null) {
            return 0.0f;
        }
        final String normTyped = stripAccents(typed.toLowerCase());
        final String normCandidate = stripAccents(candidate.toLowerCase());

        if (normTyped.equals(normCandidate)) {
            return candidateFreq + 1000.0f;
        }

        final float dist = computeWeightedDistance(normTyped, normCandidate);
        final int maxLen = Math.max(normTyped.length(), normCandidate.length());
        final float relativeDist = dist / Math.max(1, maxLen);
        final float lengthBonus = getLongWordCorrectionBonus(normTyped, normCandidate);

        return (candidateFreq * (1.0f - (relativeDist * 0.5f))) - (dist * 25.0f) + lengthBonus;
    }

    public synchronized void insert(final String word, final int frequency) {
        if (word == null || word.isEmpty()) {
            return;
        }
        final String normalized = stripAccents(word.toLowerCase());
        TrieNode current = mRoot;
        for (int i = 0; i < normalized.length(); i++) {
            final char ch = normalized.charAt(i);
            current = current.getOrCreateChild(ch);
        }
        if (current.addWord(word, frequency)) {
            mWordCount++;
        }
        updateTopWord(word, frequency);
    }

    private void updateTopWord(final String word, final int freq) {
        if (word == null || word.isEmpty()) return;
        for (int i = 0; i < mTopWords.size(); i++) {
            if (mTopWords.get(i).word.equalsIgnoreCase(word)) {
                if (freq > mTopWords.get(i).frequency) {
                    mTopWords.remove(i);
                    break;
                } else {
                    return;
                }
            }
        }
        if (mTopWords.size() < 50 || freq > mTopWords.get(mTopWords.size() - 1).frequency) {
            int insertIdx = Collections.binarySearch(mTopWords, new ScoredWord(word, freq, freq));
            if (insertIdx < 0) {
                insertIdx = -insertIdx - 1;
            }
            mTopWords.add(insertIdx, new ScoredWord(word, freq, freq));
            if (mTopWords.size() > 50) {
                mTopWords.remove(mTopWords.size() - 1);
            }
        }
    }

    public synchronized void setTrigram(final String w1, final String w2, final String word, final int freq) {
        if (w1 == null || w2 == null || word == null || w1.isEmpty() || w2.isEmpty() || word.isEmpty()) {
            return;
        }
        final String normW1 = stripAccents(w1.toLowerCase());
        final String normW2 = stripAccents(w2.toLowerCase());
        final String normWord = stripAccents(word.toLowerCase());
        final String key = normW1 + " " + normW2;
        Map<String, Short> nextMap = mTrigrams.get(key);
        if (nextMap == null) {
            nextMap = new HashMap<>();
            mTrigrams.put(key, nextMap);
        }
        short currentFreq = nextMap.containsKey(normWord) ? nextMap.get(normWord) : 0;
        short newFreq = (short) Math.min(Short.MAX_VALUE, currentFreq > 0 ? currentFreq + 25 : Math.max(1, freq));
        nextMap.put(normWord, newFreq);
    }

    public synchronized int getTrigramFrequency(final String w1, final String w2, final String word) {
        if (w1 == null || w2 == null || word == null || w1.isEmpty() || w2.isEmpty() || word.isEmpty()) {
            return 0;
        }
        final String normW1 = stripAccents(w1.toLowerCase());
        final String normW2 = stripAccents(w2.toLowerCase());
        final String normWord = stripAccents(word.toLowerCase());
        final String key = normW1 + " " + normW2;
        final Map<String, Short> nextMap = mTrigrams.get(key);
        if (nextMap == null) {
            return 0;
        }
        final Short freq = nextMap.get(normWord);
        return freq != null ? (freq & 0xFFFF) : 0;
    }

    public synchronized void setBigram(final String prevWord, final String word, final int freq) {
        if (prevWord == null || word == null || prevWord.isEmpty() || word.isEmpty()) {
            return;
        }
        final String normPrev = stripAccents(prevWord.toLowerCase());
        final String normWord = stripAccents(word.toLowerCase());
        Map<String, Short> nextMap = mBigrams.get(normPrev);
        if (nextMap == null) {
            nextMap = new HashMap<>();
            mBigrams.put(normPrev, nextMap);
        }
        short currentFreq = nextMap.containsKey(normWord) ? nextMap.get(normWord) : 0;
        short newFreq = (short) Math.min(Short.MAX_VALUE, currentFreq > 0 ? currentFreq + 25 : Math.max(1, freq));
        nextMap.put(normWord, newFreq);
    }

    public synchronized int getBigramFrequency(final String prevWord, final String word) {
        if (prevWord == null || word == null || prevWord.isEmpty() || word.isEmpty()) {
            return 0;
        }
        final String normPrev = stripAccents(prevWord.toLowerCase());
        final String normWord = stripAccents(word.toLowerCase());
        final Map<String, Short> nextMap = mBigrams.get(normPrev);
        if (nextMap == null) {
            return 0;
        }
        final Short freq = nextMap.get(normWord);
        return freq != null ? (freq & 0xFFFF) : 0;
    }

    public synchronized int getWordFrequency(final String word) {
        if (word == null || word.isEmpty()) return 0;
        final String norm = stripAccents(word.toLowerCase());
        TrieNode current = mRoot;
        for (int i = 0; i < norm.length(); i++) {
            current = current.getChild(norm.charAt(i));
            if (current == null) return 0;
        }
        for (int i = 0; i < current.words.length; i++) {
            if (current.words[i].equalsIgnoreCase(word)) {
                return current.freqs[i] & 0xFFFF;
            }
        }
        return 0;
    }

    public synchronized List<CharSequence> getSuggestions(final String prefix, final int maxCount) {
        return getSuggestions(prefix, maxCount, null, null);
    }

    public synchronized List<CharSequence> getSuggestions(final String prefix, final int maxCount, final String prevWord) {
        return getSuggestions(prefix, maxCount, null, prevWord);
    }

    public synchronized List<CharSequence> getSuggestions(final String prefix, final int maxCount, final String w1, final String w2) {
        if (prefix == null || prefix.trim().isEmpty() || maxCount <= 0) {
            return Collections.emptyList();
        }

        final String trimmed = prefix.trim();
        final String normPrefix = stripAccents(trimmed.toLowerCase());
        final TrieNode current = findPrefixNode(normPrefix);
        if (current == null) {
            return Collections.emptyList();
        }

        final List<ScoredWord> rawWords = new ArrayList<>();
        collectWords(current, rawWords, 40);

        final List<ScoredWord> scoredWords = scorePrefixWords(rawWords, normPrefix, w1, w2);
        return formatSuggestions(scoredWords, trimmed, maxCount);
    }

    private TrieNode findPrefixNode(final String normPrefix) {
        TrieNode current = mRoot;
        for (int i = 0; i < normPrefix.length(); i++) {
            current = current.getChild(normPrefix.charAt(i));
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private List<ScoredWord> scorePrefixWords(final List<ScoredWord> rawWords, final String normPrefix, final String w1, final String w2) {
        final List<ScoredWord> scoredWords = new ArrayList<>(rawWords.size());
        for (ScoredWord sw : rawWords) {
            final float score = calcPrefixWordScore(sw, normPrefix, w1, w2);
            scoredWords.add(new ScoredWord(sw.word, sw.frequency, score));
        }
        Collections.sort(scoredWords);
        return scoredWords;
    }

    private float calcPrefixWordScore(final ScoredWord sw, final String normPrefix, final String w1, final String w2) {
        float score = sw.frequency;
        if (stripAccents(sw.word.toLowerCase()).equals(normPrefix)) {
            score += 500.0f;
        }
        return score + getPrefixContextBonus(w1, w2, sw.word);
    }

    private float getPrefixContextBonus(final String w1, final String w2, final String word) {
        final int triFreq = getTrigramFrequency(w1, w2, word);
        final int biFreq = getBigramFrequency(w2, word);
        if (triFreq > 0) {
            return 800.0f + (triFreq * 3.0f) + (biFreq * 1.0f);
        } else if (biFreq > 0) {
            return 400.0f + (biFreq * 2.0f);
        }
        return 0.0f;
    }

    private List<CharSequence> formatSuggestions(final List<ScoredWord> scoredWords, final String originalPrefix, final int maxCount) {
        final List<CharSequence> results = new ArrayList<>();
        final Set<String> added = new HashSet<>();
        for (ScoredWord sw : scoredWords) {
            if (results.size() >= maxCount) {
                break;
            }
            final String formatted = applyCasing(originalPrefix, sw.word);
            if (added.add(formatted.toLowerCase())) {
                results.add(formatted);
            }
        }
        return results;
    }

    private void collectWords(final TrieNode node, final List<ScoredWord> accumulator, final int maxLimit) {
        for (int i = 0; i < node.words.length; i++) {
            accumulator.add(new ScoredWord(node.words[i], node.freqs[i], node.freqs[i]));
            if (accumulator.size() >= maxLimit) {
                return;
            }
        }
        for (TrieNode child : node.children) {
            collectWords(child, accumulator, maxLimit);
            if (accumulator.size() >= maxLimit) {
                return;
            }
        }
    }

    public static boolean hasDigits(final String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUrlOrEmailSymbol(final String s) {
        if (s == null) return false;
        return s.indexOf('@') >= 0 || s.indexOf('.') >= 0;
    }

    public static boolean hasIntermediateUpperCase(final String s) {
        if (s == null || s.length() <= 1) {
            return false;
        }
        if (isAllUpperCase(s)) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (Character.isUpperCase(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldSkipAutoCorrection(final String word) {
        if (word == null || word.isEmpty()) {
            return true;
        }
        if (hasDigits(word)) {
            return true;
        }
        if (hasUrlOrEmailSymbol(word)) {
            return true;
        }
        if (hasIntermediateUpperCase(word)) {
            return true;
        }
        return false;
    }

    public static boolean isAllUpperCase(final String s) {
        return StringUtils.isAllUpperCase(s);
    }

    public static String applyCasing(final String typed, final String suggestion) {
        return StringUtils.applyCasing(typed, suggestion);
    }

    public synchronized boolean containsWord(final String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        final String lower = word.toLowerCase();
        final String norm = stripAccents(lower);
        TrieNode current = mRoot;
        for (int i = 0; i < norm.length(); i++) {
            current = current.getChild(norm.charAt(i));
            if (current == null) {
                return false;
            }
        }
        for (String w : current.words) {
            if (w.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    public synchronized CharSequence getExactNormalizedCorrection(final String word) {
        if (word == null || word.isEmpty() || shouldSkipAutoCorrection(word)) {
            return null;
        }
        final String lower = word.toLowerCase();
        final String norm = stripAccents(lower);
        TrieNode current = mRoot;
        for (int i = 0; i < norm.length(); i++) {
            current = current.getChild(norm.charAt(i));
            if (current == null) {
                return null;
            }
        }
        if (current.words.length > 0) {
            final String best = current.words[0];
            if (best.equalsIgnoreCase(word)) {
                return null;
            }
            return applyCasing(word, best);
        }
        return null;
    }

    public synchronized CharSequence getBestCorrection(final String word) {
        return getBestCorrection(word, null, null);
    }

    public synchronized CharSequence getBestCorrection(final String word, final String prevWord) {
        return getBestCorrection(word, null, prevWord);
    }

    public synchronized CharSequence getBestCorrection(final String word, final String w1, final String w2) {
        if (isSkipCorrection(word)) {
            return null;
        }

        // 1. Instant O(L) exact normalized match (e.g. "autocorreccion" -> "autocorrección")
        final CharSequence exactNorm = getExactNormalizedCorrection(word);
        if (exactNorm != null) {
            return exactNorm;
        }

        final boolean isWordValid = containsWord(word);
        if (shouldSkipValidWord(isWordValid, w2)) {
            return null;
        }

        return getBestFuzzyCorrection(word, w1, w2, isWordValid);
    }

    private boolean isSkipCorrection(final String word) {
        return mAutoCorrectionThreshold <= 0.0f || word == null || word.length() <= 1 || shouldSkipAutoCorrection(word);
    }

    private boolean shouldSkipValidWord(final boolean isWordValid, final String prevWord) {
        return isWordValid && (prevWord == null || prevWord.isEmpty());
    }

    private CharSequence getBestFuzzyCorrection(final String word, final String w1, final String w2, final boolean isWordValid) {
        final String norm = stripAccents(word.toLowerCase());
        final ScoredWord best = findBestFuzzyCandidate(norm, w1, w2);
        if (best == null || !isValidCorrection(best, word, norm, w1, w2, isWordValid)) {
            return null;
        }
        return applyCasing(word, best.word);
    }

    private ScoredWord findBestFuzzyCandidate(final String norm, final String w1, final String w2) {
        final List<ScoredWord> candidates = searchAndScoreFuzzyCandidates(norm, w1, w2);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<ScoredWord> searchAndScoreFuzzyCandidates(final String norm, final String w1, final String w2) {
        final int maxDistance = getMaxFuzzyDistance(norm.length());
        final List<ScoredWord> rawCandidates = new ArrayList<>();
        searchFuzzy(mRoot, new StringBuilder(), norm, 0, maxDistance, rawCandidates);
        if (rawCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        return scoreFuzzyCandidates(rawCandidates, norm, w1, w2);
    }

    private int getMaxFuzzyDistance(final int normLen) {
        return (mAutoCorrectionThreshold >= 3.0f && normLen >= 5) ? 2 : 1;
    }

    private List<ScoredWord> scoreFuzzyCandidates(final List<ScoredWord> rawCandidates, final String norm, final String w1, final String w2) {
        final List<ScoredWord> candidates = new ArrayList<>(rawCandidates.size());
        for (ScoredWord cw : rawCandidates) {
            final float score = calcFuzzyCandidateScore(norm, cw.word, cw.frequency, w1, w2);
            candidates.add(new ScoredWord(cw.word, cw.frequency, score));
        }
        Collections.sort(candidates);
        return candidates;
    }

    private float calcFuzzyCandidateScore(final String norm, final String candidateWord, final int candidateFreq, final String w1, final String w2) {
        float score = calcNormalizedScore(norm, candidateWord, candidateFreq);
        return score + getFuzzyContextBonus(w1, w2, candidateWord);
    }

    private float getFuzzyContextBonus(final String w1, final String w2, final String word) {
        final int triFreq = getTrigramFrequency(w1, w2, word);
        final int biFreq = getBigramFrequency(w2, word);
        if (triFreq > 0) {
            return 600.0f + (triFreq * 2.5f);
        } else if (biFreq > 0) {
            return 300.0f + (biFreq * 1.5f);
        }
        return 0.0f;
    }

    private boolean isValidCorrection(final ScoredWord best, final String word, final String norm, final String w1, final String w2, final boolean isWordValid) {
        if (best.score < getMinCandidateScore()) {
            return false;
        }
        if (isWordValid && !isSuperiorToValidWord(best.score, word, norm, w1, w2)) {
            return false;
        }
        return true;
    }

    private float getMinCandidateScore() {
        if (mAutoCorrectionThreshold >= 3.0f) {
            return -60.0f;
        }
        if (mAutoCorrectionThreshold >= 2.0f) {
            return -25.0f;
        }
        return 0.0f;
    }

    private float getValidWordDelta() {
        if (mAutoCorrectionThreshold >= 3.0f) {
            return 60.0f;
        }
        if (mAutoCorrectionThreshold >= 2.0f) {
            return 120.0f;
        }
        return 200.0f;
    }

    private boolean isSuperiorToValidWord(final float bestScore, final String word, final String norm, final String w1, final String w2) {
        final int selfFreq = getWordFrequency(word);
        float selfScore = calcNormalizedScore(norm, norm, selfFreq) + getFuzzyContextBonus(w1, w2, word);
        return bestScore >= selfScore + getValidWordDelta();
    }

    public synchronized List<CharSequence> getFuzzySuggestions(final String word, final int maxCount) {
        return getFuzzySuggestions(word, maxCount, null, null);
    }

    public synchronized List<CharSequence> getFuzzySuggestions(final String word, final int maxCount, final String prevWord) {
        return getFuzzySuggestions(word, maxCount, null, prevWord);
    }

    public synchronized List<CharSequence> getFuzzySuggestions(final String word, final int maxCount, final String w1, final String w2) {
        if (word == null || word.length() <= 1 || maxCount <= 0) {
            return Collections.emptyList();
        }
        final String norm = stripAccents(word.toLowerCase());
        final List<ScoredWord> candidates = searchAndScoreFuzzyCandidates(norm, w1, w2);
        return formatSuggestions(candidates, word, maxCount);
    }

    private void searchFuzzy(final TrieNode node, final StringBuilder currentPath,
                             final String target, final int targetIdx, final int remainingDistance,
                             final List<ScoredWord> candidates) {
        if (remainingDistance < 0) {
            return;
        }
        recordExactLengthMatches(node, target, targetIdx, candidates);

        // 1. Deletion from target (extra character typed by user)
        if (targetIdx < target.length() && remainingDistance > 0) {
            searchFuzzy(node, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);
        }

        for (int i = 0; i < node.keys.length; i++) {
            exploreFuzzyBranch(node.children[i], node.keys[i], currentPath, target, targetIdx, remainingDistance, candidates);
        }
    }

    private void recordExactLengthMatches(final TrieNode node, final String target, final int targetIdx, final List<ScoredWord> candidates) {
        if (targetIdx == target.length() && node.words.length > 0) {
            for (int i = 0; i < node.words.length; i++) {
                candidates.add(new ScoredWord(node.words[i], node.freqs[i], node.freqs[i]));
            }
        }
    }

    private void exploreFuzzyBranch(final TrieNode child, final char ch, final StringBuilder currentPath,
                                    final String target, final int targetIdx, final int remainingDistance,
                                    final List<ScoredWord> candidates) {
        currentPath.append(ch);
        if (targetIdx < target.length()) {
            exploreMatchOrEdit(child, ch, currentPath, target, targetIdx, remainingDistance, candidates);
        }
        if (remainingDistance > 0) {
            searchFuzzy(child, currentPath, target, targetIdx, remainingDistance - 1, candidates);
        }
        currentPath.setLength(currentPath.length() - 1);
    }

    private void exploreMatchOrEdit(final TrieNode child, final char ch, final StringBuilder currentPath,
                                    final String target, final int targetIdx, final int remainingDistance,
                                    final List<ScoredWord> candidates) {
        if (target.charAt(targetIdx) == ch) {
            searchFuzzy(child, currentPath, target, targetIdx + 1, remainingDistance, candidates);
        } else if (remainingDistance > 0) {
            searchFuzzy(child, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);
            exploreTransposition(child, ch, currentPath, target, targetIdx, remainingDistance, candidates);
        }
    }

    private void exploreTransposition(final TrieNode child, final char ch, final StringBuilder currentPath,
                                      final String target, final int targetIdx, final int remainingDistance,
                                      final List<ScoredWord> candidates) {
        if (targetIdx + 1 >= target.length() || target.charAt(targetIdx + 1) != ch) {
            return;
        }
        final char nextTargetChar = target.charAt(targetIdx);
        final TrieNode transChild = child.getChild(nextTargetChar);
        if (transChild != null) {
            currentPath.append(nextTargetChar);
            searchFuzzy(transChild, currentPath, target, targetIdx + 2, remainingDistance - 1, candidates);
            currentPath.setLength(currentPath.length() - 1);
        }
    }

    public synchronized String getCanonicalWord(final String word) {
        if (word == null || word.isEmpty()) return word;
        final String norm = stripAccents(word.toLowerCase());
        TrieNode current = mRoot;
        for (int i = 0; i < norm.length(); i++) {
            current = current.getChild(norm.charAt(i));
            if (current == null) return word;
        }
        if (current.words.length > 0) {
            return current.words[0];
        }
        return word;
    }

    public synchronized List<CharSequence> getNextWordPredictions(final String prevWord, final int limit) {
        return getNextWordPredictions(null, prevWord, limit);
    }

    public synchronized List<CharSequence> getNextWordPredictions(final String w1, final String w2, final int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        final List<CharSequence> results = new ArrayList<>(limit);
        final Set<String> added = new HashSet<>();

        // 1. Trigram Lookup (w1 + w2 -> w3)
        if (w1 != null && !w1.trim().isEmpty() && w2 != null && !w2.trim().isEmpty()) {
            final String normW1 = stripAccents(w1.trim().toLowerCase());
            final String normW2 = stripAccents(w2.trim().toLowerCase());
            final String key = normW1 + " " + normW2;
            final Map<String, Short> nextMap = mTrigrams.get(key);
            if (nextMap != null && !nextMap.isEmpty()) {
                final List<Map.Entry<String, Short>> sortedEntries = new ArrayList<>(nextMap.entrySet());
                Collections.sort(sortedEntries, (a, b) -> Integer.compare(b.getValue() & 0xFFFF, a.getValue() & 0xFFFF));
                for (Map.Entry<String, Short> entry : sortedEntries) {
                    final String candidate = getCanonicalWord(entry.getKey());
                    if (added.add(candidate.toLowerCase())) {
                        results.add(candidate);
                        if (results.size() >= limit) {
                            return results;
                        }
                    }
                }
            }
        }

        // 2. Bigram Lookup Backoff (w2 -> w3)
        if (w2 != null && !w2.trim().isEmpty()) {
            final String normW2 = stripAccents(w2.trim().toLowerCase());
            final Map<String, Short> nextMap = mBigrams.get(normW2);
            if (nextMap != null && !nextMap.isEmpty()) {
                final List<Map.Entry<String, Short>> sortedEntries = new ArrayList<>(nextMap.entrySet());
                Collections.sort(sortedEntries, (a, b) -> Integer.compare(b.getValue() & 0xFFFF, a.getValue() & 0xFFFF));
                for (Map.Entry<String, Short> entry : sortedEntries) {
                    final String candidate = getCanonicalWord(entry.getKey());
                    if (added.add(candidate.toLowerCase())) {
                        results.add(candidate);
                        if (results.size() >= limit) {
                            return results;
                        }
                    }
                }
            }
        }

        // 3. Fallback to top frequent words from dictionary if not enough n-grams
        if (results.size() < limit) {
            for (ScoredWord sw : mTopWords) {
                final String candidate = sw.word;
                if (added.add(candidate.toLowerCase())) {
                    results.add(candidate);
                    if (results.size() >= limit) {
                        break;
                    }
                }
            }
        }

        // 4. Final fallback to common frequent words if dictionary is small/empty
        if (results.size() < limit) {
            final String[] defaultFallback = {"the", "to", "and", "a", "in", "is", "it", "you", "that", "he", "que", "de", "no", "la", "el", "es", "en"};
            for (String w : defaultFallback) {
                if (added.add(w.toLowerCase())) {
                    results.add(w);
                    if (results.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return results;
    }

    public synchronized int getWordCount() {
        return mWordCount;
    }

    public synchronized void clear() {
        mRoot = new TrieNode();
        mWordCount = 0;
        mTrigrams.clear();
        mBigrams.clear();
        mTopWords.clear();
    }
}

