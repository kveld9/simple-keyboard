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

    public static SplitResult findBestSplit(final PrefixDictionary dict, final String typedWord, final String prevWord) {
        if (dict == null || typedWord == null || typedWord.length() < 4) {
            return null;
        }
        final String norm = StringUtils.toNormalizedLower(typedWord);
        final int len = norm.length();

        SplitResult best = null;
        float bestScore = 0.0f;

        for (int i = 1; i <= len - 1; i++) {
            final String part1 = norm.substring(0, i);
            final String part2 = norm.substring(i);

            final int freq1 = dict.getWordFrequency(part1);
            final int freq2 = dict.getWordFrequency(part2);

            if (freq1 > 10 && freq2 > 10) {
                final String canon1 = dict.getCanonicalWord(part1);
                final String canon2 = dict.getCanonicalWord(part2);

                final int biFreq = dict.getBigramFrequency(canon1, canon2);

                // Require a bigram link OR one of the parts being a common short particle/preposition
                final boolean isGrammarParticle = part1.length() <= 3 || part2.length() <= 3;
                if (biFreq <= 0 && !isGrammarParticle) {
                    continue;
                }

                float splitScore = (freq1 * 0.35f) + (freq2 * 0.35f) + (biFreq * 3.0f) + 40.0f;

                if (prevWord != null && !prevWord.isEmpty()) {
                    splitScore += dict.getBigramFrequency(prevWord, canon1) * 1.5f;
                }

                if (splitScore > bestScore && splitScore >= 75.0f) {
                    bestScore = splitScore;
                    best = new SplitResult(canon1, canon2, splitScore);
                }
            }
        }
        return best;
    }
}
