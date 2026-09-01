package rkr.simplekeyboard.inputmethod.latin.dict;

import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

/**
 * Intelligent word segmentation engine that identifies merged words without spaces
 * (e.g. "comoteva" -> "cómo te va", "delos" -> "de los", "notengo" -> "no tengo", "porqyeno" -> "porque no")
 * and suggests/autocorrects them with the missing space and typo tolerance.
 */
public final class MultiWordSplitter {

    private static final int MAX_WORD_LEN = 32;

    public static class SplitResult {
        public String word1;
        public String word2;
        public String word3;
        public String combined;
        public float score;
        public boolean isValid;

        public SplitResult() {
        }

        public void set(final String word1, final String word2, final String word3, final float score) {
            this.word1 = word1;
            this.word2 = word2;
            this.word3 = word3;
            this.isValid = true;
            if (word3 == null || word3.isEmpty()) {
                this.combined = word1 + " " + word2;
            } else {
                this.combined = word1 + " " + word2 + " " + word3;
            }
            this.score = score;
        }
    }

    private static final class ComponentMatch {
        String word;
        int freq;
        int typoCost; // 0 for exact/canonical, 1 for 1-edit typo, -1 for invalid
        boolean evaluated;

        void reset() {
            word = null;
            freq = 0;
            typoCost = -1;
            evaluated = false;
        }
    }

    private static final ThreadLocal<ComponentMatch[][]> sComponentTable = new ThreadLocal<ComponentMatch[][]>() {
        @Override
        protected ComponentMatch[][] initialValue() {
            final ComponentMatch[][] table = new ComponentMatch[MAX_WORD_LEN + 1][MAX_WORD_LEN + 1];
            for (int i = 0; i <= MAX_WORD_LEN; i++) {
                for (int j = 0; j <= MAX_WORD_LEN; j++) {
                    table[i][j] = new ComponentMatch();
                }
            }
            return table;
        }
    };

    private static final ThreadLocal<SplitResult> sScratchResult1 = new ThreadLocal<SplitResult>() {
        @Override
        protected SplitResult initialValue() {
            return new SplitResult();
        }
    };

    private static final ThreadLocal<SplitResult> sScratchResult2 = new ThreadLocal<SplitResult>() {
        @Override
        protected SplitResult initialValue() {
            return new SplitResult();
        }
    };

    private MultiWordSplitter() {
    }

    private static boolean isValidForSplit(final PrefixDictionary dict, final String typedWord) {
        if (dict == null || typedWord == null || typedWord.length() < 4) {
            return false;
        }
        final String norm = StringUtils.toNormalizedLower(typedWord);
        if (dict.containsWord(norm) || dict.getWordFrequency(norm) > 10) {
            return false;
        }
        return true;
    }

    private static boolean isGrammarParticle(final String word) {
        return word != null && word.length() <= 3;
    }

    private static boolean hasValidConnection2(final int biFreq, final String word1, final String word2) {
        return biFreq > 0 || isGrammarParticle(word1) || isGrammarParticle(word2);
    }

    private static boolean hasValidConnection3(final int bi1_2, final int bi2_3, final String word1, final String word2, final String word3) {
        if (bi1_2 > 0 || bi2_3 > 0) {
            return true;
        }
        return isGrammarParticle(word2) && (isGrammarParticle(word1) || isGrammarParticle(word3));
    }

    private static ComponentMatch getOrEvaluateComponent(final PrefixDictionary dict, final String norm, final int start, final int end, final ComponentMatch[][] table) {
        final ComponentMatch match = table[start][end];
        if (match.evaluated) {
            return match;
        }
        match.evaluated = true;
        match.word = null;
        match.freq = 0;
        match.typoCost = -1;

        final int len = end - start;
        if (len <= 0) {
            return match;
        }
        final String part = norm.substring(start, end);

        // 1. Exact or Canonical Lookup
        int freq = dict.getWordFrequency(part);
        String canon = dict.getCanonicalWord(part);

        if (canon != null && !canon.isEmpty() && !canon.equalsIgnoreCase(part)) {
            int canonFreq = dict.getWordFrequency(canon);
            if (canonFreq > freq) {
                freq = canonFreq;
            }
        }

        if (freq > 0) {
            match.word = (canon != null && !canon.isEmpty()) ? canon : part;
            match.freq = freq;
            match.typoCost = 0;
            return match;
        }

        if (canon != null && !canon.isEmpty() && dict.containsWord(canon)) {
            match.word = canon;
            match.freq = Math.max(dict.getWordFrequency(canon), 50);
            match.typoCost = 0;
            return match;
        }

        // 2. 1-edit typo matching (allowed for components with length >= 3)
        if (len >= 3) {
            final List<CharSequence> fuzzy = dict.getFuzzySuggestions(part, 3);
            if (fuzzy != null && !fuzzy.isEmpty()) {
                for (int i = 0; i < fuzzy.size(); i++) {
                    final String cand = fuzzy.get(i).toString();
                    if (cand == null || cand.isEmpty() || dict.isBlocked(cand)) {
                        continue;
                    }
                    final String normCand = StringUtils.toNormalizedLower(cand);
                    final float dist = PrefixDictionary.computeWeightedDistance(part, normCand);
                    if (dist <= 1.5f) {
                        final int candFreq = dict.getWordFrequency(cand);
                        if (candFreq > 10) {
                            match.word = cand;
                            match.freq = candFreq;
                            match.typoCost = 1;
                            return match;
                        }
                    }
                }
            }
        }

        return match;
    }

    private static void evaluate2WordSplit(final PrefixDictionary dict, final String norm, final int index, final String prevWord, final ComponentMatch[][] table, final SplitResult outResult) {
        outResult.isValid = false;
        final ComponentMatch m1 = getOrEvaluateComponent(dict, norm, 0, index, table);
        if (m1.word == null || m1.freq <= 10) {
            return;
        }
        final ComponentMatch m2 = getOrEvaluateComponent(dict, norm, index, norm.length(), table);
        if (m2.word == null || m2.freq <= 10) {
            return;
        }

        final int totalTypos = m1.typoCost + m2.typoCost;
        if (totalTypos > 1) {
            return;
        }

        final int biFreq = dict.getBigramFrequency(m1.word, m2.word);
        if (!hasValidConnection2(biFreq, m1.word, m2.word)) {
            return;
        }

        float score = (m1.freq * 0.35f) + (m2.freq * 0.35f) + (biFreq * 3.0f) + 40.0f;
        if (totalTypos > 0) {
            score -= (totalTypos * 25.0f);
        }
        if (prevWord != null && !prevWord.isEmpty()) {
            score += dict.getBigramFrequency(prevWord, m1.word) * 1.5f;
        }

        if (score >= 75.0f) {
            outResult.set(m1.word, m2.word, null, score);
        }
    }

    private static void evaluate3WordSplit(final PrefixDictionary dict, final String norm, final int i, final int j, final String prevWord, final ComponentMatch[][] table, final SplitResult outResult) {
        outResult.isValid = false;
        final ComponentMatch m1 = getOrEvaluateComponent(dict, norm, 0, i, table);
        if (m1.word == null || m1.freq <= 10) {
            return;
        }
        final ComponentMatch m2 = getOrEvaluateComponent(dict, norm, i, j, table);
        if (m2.word == null || m2.freq <= 10) {
            return;
        }
        final ComponentMatch m3 = getOrEvaluateComponent(dict, norm, j, norm.length(), table);
        if (m3.word == null || m3.freq <= 10) {
            return;
        }

        final int totalTypos = m1.typoCost + m2.typoCost + m3.typoCost;
        if (totalTypos > 1) {
            return;
        }

        final int bi1_2 = dict.getBigramFrequency(m1.word, m2.word);
        final int bi2_3 = dict.getBigramFrequency(m2.word, m3.word);

        if (!hasValidConnection3(bi1_2, bi2_3, m1.word, m2.word, m3.word)) {
            return;
        }

        float score = (m1.freq * 0.25f) + (m2.freq * 0.25f) + (m3.freq * 0.25f) + 45.0f;
        score += (bi1_2 * 2.5f) + (bi2_3 * 2.5f);
        if (bi1_2 > 0 && bi2_3 > 0) {
            score += 20.0f;
        }
        if (totalTypos > 0) {
            score -= (totalTypos * 25.0f);
        }
        if (prevWord != null && !prevWord.isEmpty()) {
            score += dict.getBigramFrequency(prevWord, m1.word) * 1.5f;
        }

        if (score >= 75.0f) {
            outResult.set(m1.word, m2.word, m3.word, score);
        }
    }

    private static boolean isBetterSplit(final SplitResult candidate, final SplitResult currentBest) {
        return candidate.isValid && (!currentBest.isValid || candidate.score > currentBest.score);
    }

    public static SplitResult split(final PrefixDictionary dict, final String typedWord) {
        return findBestSplit(dict, typedWord, null);
    }

    public static SplitResult split(final PrefixDictionary dict, final String typedWord, final String prevWord) {
        return findBestSplit(dict, typedWord, prevWord);
    }

    public static SplitResult findBestSplit(final PrefixDictionary dict, final String typedWord, final String prevWord) {
        if (!isValidForSplit(dict, typedWord)) {
            return null;
        }
        final String norm = StringUtils.toNormalizedLower(typedWord);
        final int len = norm.length();
        if (len > MAX_WORD_LEN) {
            return null;
        }

        final ComponentMatch[][] table = sComponentTable.get();
        for (int i = 0; i <= len; i++) {
            for (int j = 0; j <= len; j++) {
                table[i][j].reset();
            }
        }

        SplitResult best = sScratchResult1.get();
        best.isValid = false;
        SplitResult candidate = sScratchResult2.get();
        candidate.isValid = false;

        // 1. Evaluate 2-word splits
        for (int i = 1; i <= len - 1; i++) {
            evaluate2WordSplit(dict, norm, i, prevWord, table, candidate);
            if (isBetterSplit(candidate, best)) {
                best.set(candidate.word1, candidate.word2, candidate.word3, candidate.score);
            }
        }

        // 2. Evaluate 3-word splits (for length >= 5)
        if (len >= 5) {
            for (int i = 1; i <= len - 2; i++) {
                for (int j = i + 1; j <= len - 1; j++) {
                    evaluate3WordSplit(dict, norm, i, j, prevWord, table, candidate);
                    if (isBetterSplit(candidate, best)) {
                        best.set(candidate.word1, candidate.word2, candidate.word3, candidate.score);
                    }
                }
            }
        }

        return best.isValid ? best : null;
    }
}
