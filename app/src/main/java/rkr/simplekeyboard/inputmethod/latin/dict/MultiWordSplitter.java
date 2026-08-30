package rkr.simplekeyboard.inputmethod.latin.dict;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

/**
 * Intelligent word segmentation engine that identifies merged words without spaces
 * (e.g. "comoteva" -> "cómo te va", "delos" -> "de los", "notengo" -> "no tengo")
 * and suggests/autocorrects them with the missing space.
 */
public final class MultiWordSplitter {

    public static class SplitResult {
        public final String word1;
        public final String word2;
        public final String combined;
        public final float score;

        public SplitResult(final String word1, final String word2, final float score) {
            this.word1 = word1;
            this.word2 = word2;
            this.combined = word1 + " " + word2;
            this.score = score;
        }
    }

    private MultiWordSplitter() {
    }

    private static boolean isValidForSplit(final PrefixDictionary dict, final String typedWord) {
        return dict != null && typedWord != null && typedWord.length() >= 4;
    }

    private static boolean hasSufficientFrequency(final int freq1, final int freq2) {
        return freq1 > 10 && freq2 > 10;
    }

    private static boolean isGrammarParticle(final String part1, final String part2) {
        return part1.length() <= 3 || part2.length() <= 3;
    }

    private static boolean hasValidConnection(final int biFreq, final String part1, final String part2) {
        return biFreq > 0 || isGrammarParticle(part1, part2);
    }

    private static float computeSplitScore(final PrefixDictionary dict, final int freq1, final int freq2, final int biFreq, final String canon1, final String prevWord) {
        float splitScore = (freq1 * 0.35f) + (freq2 * 0.35f) + (biFreq * 3.0f) + 40.0f;
        if (prevWord != null && !prevWord.isEmpty()) {
            splitScore += dict.getBigramFrequency(prevWord, canon1) * 1.5f;
        }
        return splitScore;
    }

    private static SplitResult evaluateSplitAt(final PrefixDictionary dict, final String norm, final int index, final String prevWord) {
        final String part1 = norm.substring(0, index);
        final String part2 = norm.substring(index);

        final int freq1 = dict.getWordFrequency(part1);
        final int freq2 = dict.getWordFrequency(part2);
        if (!hasSufficientFrequency(freq1, freq2)) {
            return null;
        }

        final String canon1 = dict.getCanonicalWord(part1);
        final String canon2 = dict.getCanonicalWord(part2);
        final int biFreq = dict.getBigramFrequency(canon1, canon2);

        if (!hasValidConnection(biFreq, part1, part2)) {
            return null;
        }

        final float score = computeSplitScore(dict, freq1, freq2, biFreq, canon1, prevWord);
        return (score >= 75.0f) ? new SplitResult(canon1, canon2, score) : null;
    }

    private static boolean isBetterSplit(final SplitResult candidate, final SplitResult currentBest) {
        return candidate != null && (currentBest == null || candidate.score > currentBest.score);
    }

    public static SplitResult findBestSplit(final PrefixDictionary dict, final String typedWord, final String prevWord) {
        if (!isValidForSplit(dict, typedWord)) {
            return null;
        }
        final String norm = StringUtils.toNormalizedLower(typedWord);
        final int len = norm.length();

        SplitResult best = null;
        for (int i = 1; i <= len - 1; i++) {
            final SplitResult candidate = evaluateSplitAt(dict, norm, i, prevWord);
            if (isBetterSplit(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }
}
