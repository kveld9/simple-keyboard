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
 * Lightweight in-memory Trie dictionary with Accent-Folding and Diacritic Invariance.
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

    private static final class ScoredWord implements Comparable<ScoredWord> {
        final String word;
        final int frequency;

        ScoredWord(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }

        @Override
        public int compareTo(ScoredWord other) {
            return Integer.compare(other.frequency, this.frequency);
        }
    }

    private TrieNode mRoot = new TrieNode();
    private int mWordCount = 0;

    public PrefixDictionary() {
    }

    public synchronized void copyFrom(final PrefixDictionary other) {
        if (other != null) {
            synchronized (other) {
                this.mRoot = other.mRoot;
                this.mWordCount = other.mWordCount;
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

    public synchronized void loadFromStream(final InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final String[] parts = line.split("[\\s,]+");
                if (parts.length >= 2) {
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

        final List<ScoredWord> scoredWords = new ArrayList<>();
        collectWords(current, scoredWords, 40);
        Collections.sort(scoredWords, (a, b) -> {
            final boolean aExact = stripAccents(a.word.toLowerCase()).equals(normPrefix);
            final boolean bExact = stripAccents(b.word.toLowerCase()).equals(normPrefix);
            if (aExact != bExact) {
                return aExact ? -1 : 1;
            }
            return Integer.compare(b.frequency, a.frequency);
        });

        final boolean isAllUpper = isAllUpperCase(trimmed);
        final boolean isFirstUpper = Character.isUpperCase(trimmed.charAt(0));

        final List<CharSequence> results = new ArrayList<>();
        final Set<String> added = new HashSet<>();
        for (int i = 0; i < scoredWords.size() && results.size() < maxCount; i++) {
            final String word = scoredWords.get(i).word;
            String formatted;
            if (isAllUpper && word.length() > 1) {
                formatted = word.toUpperCase();
            } else if (isFirstUpper && word.length() > 0) {
                formatted = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            } else {
                formatted = word;
            }
            if (added.add(formatted.toLowerCase())) {
                results.add(formatted);
            }
        }
        return results;
    }

    private void collectWords(final TrieNode node, final List<ScoredWord> accumulator, final int maxLimit) {
        for (int i = 0; i < node.words.length; i++) {
            accumulator.add(new ScoredWord(node.words[i], node.freqs[i]));
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

    private static boolean isAllUpperCase(final String s) {
        if (s.length() <= 1) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i)) && !Character.isUpperCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
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
        if (word == null || word.isEmpty()) {
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
            return formatCasing(best, word);
        }
        return null;
    }

    public synchronized CharSequence getBestCorrection(final String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }

        // 1. Instant O(L) exact normalized match (e.g. "autocorreccion" -> "autocorrección")
        final CharSequence exactNorm = getExactNormalizedCorrection(word);
        if (exactNorm != null) {
            return exactNorm;
        }
        if (containsWord(word)) {
            return null;
        }

        // 2. Fuzzy search for typo corrections (d=1, minimum length 2)
        if (word.length() <= 1) {
            return null;
        }
        final String lower = word.toLowerCase();
        final String norm = stripAccents(lower);
        final List<ScoredWord> candidates = new ArrayList<>();
        searchFuzzy(mRoot, new StringBuilder(), norm, 0, 1, candidates);

        if (candidates.isEmpty()) {
            return null;
        }
        Collections.sort(candidates);
        final String best = candidates.get(0).word;
        return formatCasing(best, word);
    }

    public synchronized List<CharSequence> getFuzzySuggestions(final String word, final int maxCount) {
        if (word == null || word.length() <= 1 || maxCount <= 0) {
            return Collections.emptyList();
        }
        final String lower = word.toLowerCase();
        final String norm = stripAccents(lower);
        final List<ScoredWord> candidates = new ArrayList<>();
        searchFuzzy(mRoot, new StringBuilder(), norm, 0, 1, candidates);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(candidates);
        final List<CharSequence> results = new ArrayList<>();
        final Set<String> added = new HashSet<>();
        for (int i = 0; i < candidates.size() && results.size() < maxCount; i++) {
            final String w = candidates.get(i).word;
            final CharSequence formatted = formatCasing(w, word);
            if (added.add(w.toLowerCase())) {
                results.add(formatted);
            }
        }
        return results;
    }

    private static CharSequence formatCasing(final String target, final String original) {
        if (isAllUpperCase(original) && target.length() > 1) {
            return target.toUpperCase();
        } else if (Character.isUpperCase(original.charAt(0)) && target.length() > 0) {
            return Character.toUpperCase(target.charAt(0)) + target.substring(1);
        }
        return target;
    }

    private void searchFuzzy(final TrieNode node, final StringBuilder currentPath,
                             final String target, final int targetIdx, final int remainingDistance,
                             final List<ScoredWord> candidates) {
        if (targetIdx == target.length() && node.words.length > 0) {
            for (int i = 0; i < node.words.length; i++) {
                candidates.add(new ScoredWord(node.words[i], node.freqs[i]));
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
    }
}
