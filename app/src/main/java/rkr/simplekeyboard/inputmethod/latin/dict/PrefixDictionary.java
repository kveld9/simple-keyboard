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

/**
 * Lightweight in-memory Trie dictionary with Accent-Folding, Physical Proximity scoring,
 * Long Word correction bonuses, and Bigram context support.
 */
public final class PrefixDictionary {

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
            for (int i = 0; i < words.length; i++) {
                if (words[i].equalsIgnoreCase(word)) {
                    if (sFreq > freqs[i]) {
                        freqs[i] = sFreq;
                        sortWords();
                    }
                    return false;
                }
            }
            final int len = words.length;
            final String[] newW = new String[len + 1];
            final short[] newF = new short[len + 1];
            System.arraycopy(words, 0, newW, 0, len);
            System.arraycopy(freqs, 0, newF, 0, len);
            newW[len] = word;
            newF[len] = sFreq;
            this.words = newW;
            this.freqs = newF;
            sortWords();
            return true;
        }

        boolean removeWord(final String word) {
            for (int i = 0; i < words.length; i++) {
                if (words[i].equalsIgnoreCase(word)) {
                    final int len = words.length;
                    final String[] newW = new String[len - 1];
                    final short[] newF = new short[len - 1];
                    System.arraycopy(words, 0, newW, 0, i);
                    System.arraycopy(words, i + 1, newW, i, len - i - 1);
                    System.arraycopy(freqs, 0, newF, 0, i);
                    System.arraycopy(freqs, i + 1, newF, i, len - i - 1);
                    this.words = newW;
                    this.freqs = newF;
                    return true;
                }
            }
            return false;
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
    private final Map<String, Map<String, Short>> mBigrams = new HashMap<>();

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
                this.mBigrams.clear();
                for (Map.Entry<String, Map<String, Short>> entry : other.mBigrams.entrySet()) {
                    this.mBigrams.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
            }
        }
    }

    public static String stripAccents(final String s) {
        if (s == null) return "";
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case 'á': case 'à': case 'ä': case 'â': case 'ã': sb.append('a'); break;
                case 'é': case 'è': case 'ë': case 'ê': sb.append('e'); break;
                case 'í': case 'ì': case 'ï': case 'î': sb.append('i'); break;
                case 'ó': case 'ò': case 'ö': case 'ô': case 'õ': sb.append('o'); break;
                case 'ú': case 'ù': case 'ü': case 'û': sb.append('u'); break;
                case 'Á': case 'À': case 'Ä': case 'Â': case 'Ã': sb.append('A'); break;
                case 'É': case 'È': case 'Ë': case 'Ê': sb.append('E'); break;
                case 'Í': case 'Ì': case 'Ï': case 'Î': sb.append('I'); break;
                case 'Ó': case 'Ò': case 'Ö': case 'Ô': case 'Õ': sb.append('O'); break;
                case 'Ú': case 'Ù': case 'Ü': case 'Û': sb.append('U'); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
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
    }

    public synchronized void syncUserWords(final Map<String, Integer> userWords) {
        if (userWords == null || userWords.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : userWords.entrySet()) {
            insert(entry.getKey(), entry.getValue());
        }
    }

    public synchronized void syncUserBigrams(final Map<String, Map<String, Integer>> userBigrams) {
        if (userBigrams == null || userBigrams.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<String, Integer>> entry : userBigrams.entrySet()) {
            final String prev = entry.getKey();
            for (Map.Entry<String, Integer> subEntry : entry.getValue().entrySet()) {
                setBigram(prev, subEntry.getKey(), subEntry.getValue());
            }
        }
    }

    public synchronized boolean removeWord(final String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        final String normalized = stripAccents(word.toLowerCase());
        TrieNode current = mRoot;
        for (int i = 0; i < normalized.length(); i++) {
            current = current.getChild(normalized.charAt(i));
            if (current == null) {
                return false;
            }
        }
        boolean removed = current.removeWord(word);
        if (removed) {
            mWordCount--;
        }
        return removed;
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
        nextMap.put(normWord, (short) Math.min(Short.MAX_VALUE, Math.max(1, freq)));
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

    public synchronized Map<String, Integer> getBigramPredictions(final String prevWord) {
        final Map<String, Integer> results = new HashMap<>();
        if (prevWord == null || prevWord.isEmpty()) {
            return results;
        }
        final String normPrev = stripAccents(prevWord.toLowerCase());
        final Map<String, Short> nextMap = mBigrams.get(normPrev);
        if (nextMap != null) {
            for (Map.Entry<String, Short> entry : nextMap.entrySet()) {
                results.put(entry.getKey(), entry.getValue() & 0xFFFF);
            }
        }
        return results;
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

    public synchronized void loadFromStream(final InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final String[] parts = line.split("[\\s,]+");
                if (parts.length >= 3) {
                    try {
                        final String prev = parts[0];
                        final String word = parts[1];
                        final int freq = Integer.parseInt(parts[2]);
                        setBigram(prev, word, freq);
                    } catch (NumberFormatException e) {
                        insert(parts[0], 1);
                    }
                } else if (parts.length == 2) {
                    try {
                        final String word = parts[0];
                        final int freq = Integer.parseInt(parts[1]);
                        insert(word, freq);
                    } catch (NumberFormatException e) {
                        insert(parts[0], 1);
                    }
                } else if (parts.length == 1) {
                    insert(parts[0], 1);
                }
            }
        }
    }

    public synchronized List<CharSequence> getSuggestions(final String prefix, final int maxCount) {
        return getSuggestions(prefix, maxCount, null);
    }

    public synchronized List<CharSequence> getSuggestions(final String prefix, final int maxCount, final String prevWord) {
        if (prefix == null || prefix.trim().isEmpty() || maxCount <= 0) {
            return Collections.emptyList();
        }

        final String trimmed = prefix.trim();
        final String normPrefix = stripAccents(trimmed.toLowerCase());
        TrieNode current = mRoot;

        for (int i = 0; i < normPrefix.length(); i++) {
            final char ch = normPrefix.charAt(i);
            current = current.getChild(ch);
            if (current == null) {
                return Collections.emptyList();
            }
        }

        final List<ScoredWord> rawWords = new ArrayList<>();
        collectWords(current, rawWords, 40);

        final List<ScoredWord> scoredWords = new ArrayList<>(rawWords.size());
        for (ScoredWord sw : rawWords) {
            float score = sw.frequency;
            final boolean isExact = stripAccents(sw.word.toLowerCase()).equals(normPrefix);
            if (isExact) {
                score += 500.0f;
            }
            if (prevWord != null && !prevWord.isEmpty()) {
                final int bigramFreq = getBigramFrequency(prevWord, sw.word);
                if (bigramFreq > 0) {
                    score += bigramFreq * 2.0f;
                }
            }
            scoredWords.add(new ScoredWord(sw.word, sw.frequency, score));
        }

        Collections.sort(scoredWords);

        final List<CharSequence> results = new ArrayList<>();
        final Set<String> added = new HashSet<>();
        for (int i = 0; i < scoredWords.size() && results.size() < maxCount; i++) {
            final String word = scoredWords.get(i).word;
            final String formatted = applyCasing(trimmed, word);
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
        if (s == null || s.length() <= 1) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    public static String applyCasing(final String typed, final String suggestion) {
        if (typed == null || suggestion == null || suggestion.isEmpty()) {
            return suggestion;
        }
        if (isAllUpperCase(typed)) {
            return suggestion.toUpperCase();
        } else if (typed.length() > 0 && Character.isUpperCase(typed.charAt(0))) {
            return Character.toUpperCase(suggestion.charAt(0)) + (suggestion.length() > 1 ? suggestion.substring(1).toLowerCase() : "");
        } else {
            return suggestion.toLowerCase();
        }
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
        return getBestCorrection(word, null);
    }

    public synchronized CharSequence getBestCorrection(final String word, final String prevWord) {
        if (mAutoCorrectionThreshold <= 0.0f) {
            return null;
        }
        if (word == null || word.isEmpty() || shouldSkipAutoCorrection(word)) {
            return null;
        }

        // 1. Instant O(L) exact normalized match (e.g. "autocorreccion" -> "autocorrección")
        final CharSequence exactNorm = getExactNormalizedCorrection(word);
        if (exactNorm != null) {
            return exactNorm;
        }

        final boolean isWordValid = containsWord(word);
        if (isWordValid && (prevWord == null || prevWord.isEmpty())) {
            return null;
        }

        // 2. Fuzzy search for typo corrections
        if (word.length() <= 1) {
            return null;
        }
        final String lower = word.toLowerCase();
        final String norm = stripAccents(lower);
        final int maxDistance = (mAutoCorrectionThreshold >= 3.0f && norm.length() >= 5) ? 2 : 1;
        final List<ScoredWord> rawCandidates = new ArrayList<>();
        searchFuzzy(mRoot, new StringBuilder(), norm, 0, maxDistance, rawCandidates);

        if (rawCandidates.isEmpty()) {
            return null;
        }

        final List<ScoredWord> candidates = new ArrayList<>(rawCandidates.size());
        for (ScoredWord cw : rawCandidates) {
            float score = calcNormalizedScore(norm, cw.word, cw.frequency);
            if (prevWord != null && !prevWord.isEmpty()) {
                final int bigramFreq = getBigramFrequency(prevWord, cw.word);
                if (bigramFreq > 0) {
                    score += bigramFreq * 1.5f;
                }
            }
            candidates.add(new ScoredWord(cw.word, cw.frequency, score));
        }

        Collections.sort(candidates);
        final ScoredWord best = candidates.get(0);

        final float minCandidateScore;
        final float validWordDelta;
        if (mAutoCorrectionThreshold >= 3.0f) {
            minCandidateScore = -60.0f;
            validWordDelta = 60.0f;
        } else if (mAutoCorrectionThreshold >= 2.0f) {
            minCandidateScore = -25.0f;
            validWordDelta = 120.0f;
        } else {
            minCandidateScore = 0.0f;
            validWordDelta = 200.0f;
        }

        if (best.score < minCandidateScore) {
            return null;
        }

        // Valid word protection: never replace valid words unless top candidate is vastly superior
        if (isWordValid) {
            final int selfFreq = getWordFrequency(word);
            float selfScore = calcNormalizedScore(norm, norm, selfFreq);
            if (prevWord != null && !prevWord.isEmpty()) {
                selfScore += getBigramFrequency(prevWord, word) * 1.5f;
            }
            if (best.score < selfScore + validWordDelta) {
                return null;
            }
        }

        return applyCasing(word, best.word);
    }

    public synchronized List<CharSequence> getFuzzySuggestions(final String word, final int maxCount) {
        return getFuzzySuggestions(word, maxCount, null);
    }

    public synchronized List<CharSequence> getFuzzySuggestions(final String word, final int maxCount, final String prevWord) {
        if (word == null || word.length() <= 1 || maxCount <= 0) {
            return Collections.emptyList();
        }
        final String lower = word.toLowerCase();
        final String norm = stripAccents(lower);
        final int maxDistance = (mAutoCorrectionThreshold >= 3.0f && norm.length() >= 5) ? 2 : 1;
        final List<ScoredWord> rawCandidates = new ArrayList<>();
        searchFuzzy(mRoot, new StringBuilder(), norm, 0, maxDistance, rawCandidates);
        if (rawCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        final List<ScoredWord> candidates = new ArrayList<>(rawCandidates.size());
        for (ScoredWord cw : rawCandidates) {
            float score = calcNormalizedScore(norm, cw.word, cw.frequency);
            if (prevWord != null && !prevWord.isEmpty()) {
                final int bigramFreq = getBigramFrequency(prevWord, cw.word);
                if (bigramFreq > 0) {
                    score += bigramFreq * 1.5f;
                }
            }
            candidates.add(new ScoredWord(cw.word, cw.frequency, score));
        }

        Collections.sort(candidates);
        final List<CharSequence> results = new ArrayList<>();
        final Set<String> added = new HashSet<>();
        for (int i = 0; i < candidates.size() && results.size() < maxCount; i++) {
            final String w = candidates.get(i).word;
            final String formatted = applyCasing(word, w);
            if (added.add(w.toLowerCase())) {
                results.add(formatted);
            }
        }
        return results;
    }

    private void searchFuzzy(final TrieNode node, final StringBuilder currentPath,
                             final String target, final int targetIdx, final int remainingDistance,
                             final List<ScoredWord> candidates) {
        if (targetIdx == target.length() && node.words.length > 0) {
            for (int i = 0; i < node.words.length; i++) {
                candidates.add(new ScoredWord(node.words[i], node.freqs[i], node.freqs[i]));
            }
        }

        if (remainingDistance < 0) {
            return;
        }

        // 1. Deletion from target (extra character typed by user)
        if (targetIdx < target.length() && remainingDistance > 0) {
            searchFuzzy(node, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);
        }

        for (int i = 0; i < node.keys.length; i++) {
            final char ch = node.keys[i];
            final TrieNode child = node.children[i];

            currentPath.append(ch);

            if (targetIdx < target.length()) {
                if (target.charAt(targetIdx) == ch) {
                    // Exact character match
                    searchFuzzy(child, currentPath, target, targetIdx + 1, remainingDistance, candidates);
                } else if (remainingDistance > 0) {
                    // Substitution
                    searchFuzzy(child, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);

                    // Transposition
                    if (targetIdx + 1 < target.length() && target.charAt(targetIdx + 1) == ch) {
                        final char nextTargetChar = target.charAt(targetIdx);
                        final TrieNode transChild = child.getChild(nextTargetChar);
                        if (transChild != null) {
                            currentPath.append(nextTargetChar);
                            searchFuzzy(transChild, currentPath, target, targetIdx + 2, remainingDistance - 1, candidates);
                            currentPath.setLength(currentPath.length() - 1);
                        }
                    }
                }
            }

            // Insertion
            if (remainingDistance > 0) {
                searchFuzzy(child, currentPath, target, targetIdx, remainingDistance - 1, candidates);
            }

            currentPath.setLength(currentPath.length() - 1);
        }
    }

    public synchronized int getWordCount() {
        return mWordCount;
    }

    public synchronized void clear() {
        mRoot = new TrieNode();
        mWordCount = 0;
        mBigrams.clear();
    }
}

