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

    private static final class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>();
        final List<ScoredWord> words = new ArrayList<>();
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
            // Higher frequency first
            return Integer.compare(other.frequency, this.frequency);
        }
    }

    private final TrieNode mRoot = new TrieNode();
    private int mWordCount = 0;

    public PrefixDictionary() {
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
            TrieNode child = current.children.get(ch);
            if (child == null) {
                child = new TrieNode();
                current.children.put(ch, child);
            }
            current = child;
        }

        boolean found = false;
        for (int i = 0; i < current.words.size(); i++) {
            if (current.words.get(i).word.equalsIgnoreCase(word)) {
                if (frequency > current.words.get(i).frequency) {
                    current.words.set(i, new ScoredWord(word, frequency));
                }
                found = true;
                break;
            }
        }
        if (!found) {
            current.words.add(new ScoredWord(word, frequency));
            Collections.sort(current.words);
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
            current = current.children.get(ch);
            if (current == null) {
                return Collections.emptyList();
            }
        }

        final List<ScoredWord> scoredWords = new ArrayList<>();
        collectWords(current, scoredWords);
        Collections.sort(scoredWords);

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

    private void collectWords(final TrieNode node, final List<ScoredWord> accumulator) {
        if (!node.words.isEmpty()) {
            accumulator.addAll(node.words);
        }
        for (TrieNode child : node.children.values()) {
            collectWords(child, accumulator);
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
            current = current.children.get(norm.charAt(i));
            if (current == null) {
                return false;
            }
        }
        for (ScoredWord sw : current.words) {
            if (sw.word.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    public synchronized CharSequence getBestCorrection(final String word) {
        if (word == null || word.length() <= 1) {
            return null;
        }

        final String lower = word.toLowerCase();
        final String norm = stripAccents(lower);

        // 1. Check exact normalized match (e.g. "autocorreccion" -> "autocorrección")
        TrieNode current = mRoot;
        boolean foundNorm = true;
        for (int i = 0; i < norm.length(); i++) {
            current = current.children.get(norm.charAt(i));
            if (current == null) {
                foundNorm = false;
                break;
            }
        }
        if (foundNorm && !current.words.isEmpty()) {
            final String best = current.words.get(0).word;
            if (best.equalsIgnoreCase(word)) {
                return null;
            }
            return formatCasing(best, word);
        }

        // 2. Fuzzy search for typo corrections (d=1)
        if (word.length() <= 2) {
            return null;
        }
        final List<ScoredWord> candidates = new ArrayList<>();
        searchFuzzy(mRoot, new StringBuilder(), norm, 0, 1, candidates);

        if (candidates.isEmpty()) {
            return null;
        }
        Collections.sort(candidates);
        final String best = candidates.get(0).word;
        return formatCasing(best, word);
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
        if (targetIdx == target.length() && !node.words.isEmpty()) {
            candidates.addAll(node.words);
        }

        if (remainingDistance < 0) {
            return;
        }

        // 1. Deletion from target (extra character typed by user)
        if (targetIdx < target.length() && remainingDistance > 0) {
            searchFuzzy(node, currentPath, target, targetIdx + 1, remainingDistance - 1, candidates);
        }

        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            final char ch = entry.getKey();
            final TrieNode child = entry.getValue();

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
                        final TrieNode transChild = child.children.get(nextTargetChar);
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
        mRoot.children.clear();
        mRoot.words.clear();
        mWordCount = 0;
    }
}
