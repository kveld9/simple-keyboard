/*
 * Copyright (C) 2026 Simple Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java fuzzy string comparison utility ported from trackGym's {@code FuzzySearch}.
 * Computes Levenshtein distance, normalized similarity ratios, partial ratios,
 * and token-based ratios. Used to rank dictionary and suggestion candidates.
 *
 * <p>Allocation contract: {@link #levenshteinDistance}, {@link #ratio} and the
 * window scan inside {@link #partialRatio} allocate nothing after the first call
 * (reused {@code ThreadLocal} row buffers, manual case folding, no
 * {@code substring} calls). The token-based methods allocate (regex split, sets,
 * sorting) and are cold-path only: never call them per keypress.
 */
public final class FuzzyMatcher {
    private FuzzyMatcher() {
        // This utility class is not publicly instantiable.
    }

    private static final Pattern WORD_DELIMITER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SEPARATOR = Pattern.compile("[\\s\\-_/]+");

    private static final ThreadLocal<int[]> S_ROW_PREV = new ThreadLocal<>();
    private static final ThreadLocal<int[]> S_ROW_CURR = new ThreadLocal<>();

    private static int[] getRow(final ThreadLocal<int[]> slot, final int size) {
        int[] row = slot.get();
        if (row == null || row.length < size) {
            row = new int[size];
            slot.set(row);
        }
        return row;
    }

    private static boolean foldedEquals(final char a, final char b) {
        return a == b || Character.toLowerCase(a) == Character.toLowerCase(b);
    }

    private static int trimStart(final String s) {
        final int len = s.length();
        int i = 0;
        while (i < len && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int trimEnd(final String s) {
        int end = s.length() - 1;
        while (end >= 0 && Character.isWhitespace(s.charAt(end))) {
            end--;
        }
        return end;
    }

    /**
     * Classic case-sensitive Levenshtein distance using a rolling two-row buffer.
     * A null input counts as an empty sequence.
     */
    public static int levenshteinDistance(final CharSequence s1, final CharSequence s2) {
        final int len1 = s1 == null ? 0 : s1.length();
        final int len2 = s2 == null ? 0 : s2.length();
        if (len1 == 0) {
            return len2;
        }
        if (len2 == 0) {
            return len1;
        }
        // Keep the shorter sequence on the row axis to bound buffer size.
        if (len2 > len1) {
            return levenshteinDistance(s2, s1);
        }
        int[] prev = getRow(S_ROW_PREV, len2 + 1);
        int[] curr = getRow(S_ROW_CURR, len2 + 1);
        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= len1; i++) {
            final char c1 = s1.charAt(i - 1);
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                final int cost = c1 == s2.charAt(j - 1) ? 0 : 1;
                final int deletion = prev[j] + 1;
                final int insertion = curr[j - 1] + 1;
                final int substitution = prev[j - 1] + cost;
                int best = deletion < insertion ? deletion : insertion;
                if (substitution < best) {
                    best = substitution;
                }
                curr[j] = best;
            }
            final int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[len2];
    }

    private static boolean boundsEqualFolded(final String s1, final int a0, final int a1,
            final String s2, final int b0, final int b1) {
        if (a1 - a0 != b1 - b0) {
            return false;
        }
        for (int k = 0; k <= a1 - a0; k++) {
            if (!foldedEquals(s1.charAt(a0 + k), s2.charAt(b0 + k))) {
                return false;
            }
        }
        return true;
    }

    private static int levenshteinFolded(final String s1, final int a0, final int a1,
            final String s2, final int b0, final int b1) {
        final int len1 = a1 - a0 + 1;
        final int len2 = b1 - b0 + 1;
        if (len1 <= 0) {
            return Math.max(0, len2);
        }
        if (len2 <= 0) {
            return Math.max(0, len1);
        }
        // Keep the shorter span on the row axis.
        if (len2 > len1) {
            return levenshteinFolded(s2, b0, b1, s1, a0, a1);
        }
        int[] prev = getRow(S_ROW_PREV, len2 + 1);
        int[] curr = getRow(S_ROW_CURR, len2 + 1);
        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= len1; i++) {
            final char c1 = s1.charAt(a0 + i - 1);
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                final int cost = foldedEquals(c1, s2.charAt(b0 + j - 1)) ? 0 : 1;
                final int deletion = prev[j] + 1;
                final int insertion = curr[j - 1] + 1;
                final int substitution = prev[j - 1] + cost;
                int best = deletion < insertion ? deletion : insertion;
                if (substitution < best) {
                    best = substitution;
                }
                curr[j] = best;
            }
            final int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[len2];
    }

    private static int ratioBounds(final String s1, final int a0, final int a1,
            final String s2, final int b0, final int b1) {
        if (a0 > a1 || b0 > b1) {
            return 0;
        }
        if (boundsEqualFolded(s1, a0, a1, s2, b0, b1)) {
            return 100;
        }
        final int dist = levenshteinFolded(s1, a0, a1, s2, b0, b1);
        final int maxLen = Math.max(a1 - a0 + 1, b1 - b0 + 1);
        final int score = (int) Math.round((maxLen - dist) * 100.0 / maxLen);
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Normalized similarity ratio between 0 and 100. 100 means equal ignoring
     * case and surrounding whitespace. Allocates nothing.
     */
    public static int ratio(final String s1, final String s2) {
        if (s1 == null || s2 == null) {
            return 0;
        }
        return ratioBounds(s1, trimStart(s1), trimEnd(s1), s2, trimStart(s2), trimEnd(s2));
    }

    private static boolean containsFolded(final String hay, final int h0, final int h1,
            final String ndl, final int n0, final int n1) {
        final int needleLen = n1 - n0 + 1;
        if (needleLen <= 0) {
            return true;
        }
        for (int start = h0; start + needleLen - 1 <= h1; start++) {
            boolean match = true;
            for (int k = 0; k < needleLen; k++) {
                if (!foldedEquals(hay.charAt(start + k), ndl.charAt(n0 + k))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    /**
     * Best match of the shorter string against any substring or token block of
     * the longer string. Exact containment returns 100. The window scan
     * allocates nothing; token iteration reuses one {@code Matcher}.
     */
    public static int partialRatio(final String s1, final String s2) {
        if (s1 == null || s2 == null) {
            return 0;
        }
        final int a0 = trimStart(s1);
        final int a1 = trimEnd(s1);
        final int b0 = trimStart(s2);
        final int b1 = trimEnd(s2);
        if (a0 > a1 || b0 > b1) {
            return 0;
        }
        final boolean aShorter = (a1 - a0) <= (b1 - b0);
        final String shorter = aShorter ? s1 : s2;
        final int s0 = aShorter ? a0 : b0;
        final int s1End = aShorter ? a1 : b1;
        final String longer = aShorter ? s2 : s1;
        final int l0 = aShorter ? b0 : a0;
        final int l1 = aShorter ? b1 : a1;
        if (boundsEqualFolded(shorter, s0, s1End, longer, l0, l1)) {
            return 100;
        }
        if (containsFolded(longer, l0, l1, shorter, s0, s1End)) {
            return 100;
        }
        final int shortLen = s1End - s0 + 1;
        int best = 0;
        for (int start = l0; start + shortLen - 1 <= l1; start++) {
            final int r = ratioBounds(shorter, s0, s1End, longer, start, start + shortLen - 1);
            if (r > best) {
                best = r;
                if (best == 100) {
                    return 100;
                }
            }
        }
        final Matcher matcher = SEPARATOR.matcher(longer);
        matcher.region(l0, l1 + 1);
        int tokenStart = l0;
        while (matcher.find()) {
            final int t1 = matcher.start() - 1;
            if (tokenStart <= t1) {
                final int r = ratioBounds(shorter, s0, s1End, longer, tokenStart, t1);
                if (r > best) {
                    best = r;
                    if (best == 100) {
                        return 100;
                    }
                }
            }
            tokenStart = matcher.end();
        }
        if (tokenStart <= l1) {
            final int r = ratioBounds(shorter, s0, s1End, longer, tokenStart, l1);
            if (r > best) {
                best = r;
            }
        }
        return best;
    }

    private static String tokenizeAndSort(final String s) {
        final String[] tokens = WORD_DELIMITER.split(s.trim().toLowerCase(Locale.ROOT));
        final List<String> words = new ArrayList<>();
        for (final String token : tokens) {
            if (!token.isEmpty()) {
                words.add(token);
            }
        }
        if (words.isEmpty()) {
            return s.trim().toLowerCase(Locale.ROOT);
        }
        Collections.sort(words);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(words.get(i));
        }
        return sb.toString();
    }

    /**
     * Order-invariant similarity: tokenizes, sorts alphabetically, then ratios.
     * Cold path only (allocates).
     */
    public static int tokenSortRatio(final String s1, final String s2) {
        if (s1 == null || s2 == null || s1.trim().isEmpty() || s2.trim().isEmpty()) {
            return 0;
        }
        return ratio(tokenizeAndSort(s1), tokenizeAndSort(s2));
    }

    /**
     * Word-set similarity over the token intersection and differences.
     * Cold path only (allocates).
     */
    public static int tokenSetRatio(final String s1, final String s2) {
        if (s1 == null || s2 == null || s1.trim().isEmpty() || s2.trim().isEmpty()) {
            return 0;
        }
        final Set<String> set1 = new HashSet<>();
        for (final String token
                : WORD_DELIMITER.split(s1.trim().toLowerCase(Locale.ROOT))) {
            if (!token.isEmpty()) {
                set1.add(token);
            }
        }
        final Set<String> set2 = new HashSet<>();
        for (final String token
                : WORD_DELIMITER.split(s2.trim().toLowerCase(Locale.ROOT))) {
            if (!token.isEmpty()) {
                set2.add(token);
            }
        }
        if (set1.isEmpty() || set2.isEmpty()) {
            return ratio(s1, s2);
        }
        final List<String> intersection = new ArrayList<>();
        final List<String> diff1 = new ArrayList<>();
        final List<String> diff2 = new ArrayList<>();
        for (final String token : set1) {
            if (set2.contains(token)) {
                intersection.add(token);
            } else {
                diff1.add(token);
            }
        }
        for (final String token : set2) {
            if (!set1.contains(token)) {
                diff2.add(token);
            }
        }
        Collections.sort(intersection);
        Collections.sort(diff1);
        Collections.sort(diff2);
        final String common = joinTokens(intersection);
        final String first = joinTokens(intersection, diff1);
        final String second = joinTokens(intersection, diff2);
        int best = ratio(common, first);
        best = Math.max(best, ratio(common, second));
        return Math.max(best, ratio(first, second));
    }

    private static String joinTokens(final List<String> tokens) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    private static String joinTokens(final List<String> base, final List<String> extra) {
        if (extra.isEmpty()) {
            return joinTokens(base);
        }
        if (base.isEmpty()) {
            return joinTokens(extra);
        }
        return joinTokens(base) + " " + joinTokens(extra);
    }
}
