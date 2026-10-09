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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class FuzzyMatcherTest {

    @Test
    public void testLevenshteinKnownDistances() {
        assertEquals(3, FuzzyMatcher.levenshteinDistance("kitten", "sitting"));
        assertEquals(0, FuzzyMatcher.levenshteinDistance("abc", "abc"));
        assertEquals(3, FuzzyMatcher.levenshteinDistance("", "abc"));
        assertEquals(3, FuzzyMatcher.levenshteinDistance("abc", ""));
        assertEquals(3, FuzzyMatcher.levenshteinDistance(null, "abc"));
        assertEquals(2, FuzzyMatcher.levenshteinDistance("ab", null));
        assertEquals(0, FuzzyMatcher.levenshteinDistance(null, null));
    }

    @Test
    public void testLevenshteinIsCaseSensitive() {
        assertEquals(3, FuzzyMatcher.levenshteinDistance("ABC", "abc"));
    }

    @Test
    public void testRatioIdenticalAndEmpty() {
        assertEquals(100, FuzzyMatcher.ratio("hello", "hello"));
        assertEquals(100, FuzzyMatcher.ratio("  Hello  ", "hello"));
        assertEquals(0, FuzzyMatcher.ratio("", "abc"));
        assertEquals(0, FuzzyMatcher.ratio("   ", "abc"));
        assertEquals(0, FuzzyMatcher.ratio(null, "abc"));
        assertEquals(0, FuzzyMatcher.ratio("abc", null));
    }

    @Test
    public void testRatioSingleSubstitution() {
        // Distance 1 over length 3: (3 - 1) * 100 / 3 = 66.67 rounds to 67.
        assertEquals(67, FuzzyMatcher.ratio("abc", "abd"));
    }

    @Test
    public void testPartialRatioContainment() {
        assertEquals(100, FuzzyMatcher.partialRatio("abc", "xxabcxx"));
        assertEquals(100, FuzzyMatcher.partialRatio("press", "bench press"));
        assertEquals(0, FuzzyMatcher.partialRatio("", "abc"));
        assertEquals(0, FuzzyMatcher.partialRatio(null, "abc"));
    }

    @Test
    public void testTokenSortRatioIgnoresOrder() {
        assertEquals(100, FuzzyMatcher.tokenSortRatio("bench press", "press bench"));
        assertEquals(0, FuzzyMatcher.tokenSortRatio("", "bench"));
        assertEquals(0, FuzzyMatcher.tokenSortRatio(null, "bench"));
    }

    @Test
    public void testTokenSetRatio() {
        assertEquals(100, FuzzyMatcher.tokenSetRatio("apple", "apple"));
        assertEquals(0, FuzzyMatcher.tokenSetRatio("", "apple"));
        // "apple banana" vs "apple cherry": best pairing scores 50.
        assertEquals(50, FuzzyMatcher.tokenSetRatio("apple banana", "apple cherry"));
    }

    @Test
    public void testRatiosStayInRange() {
        final int ratio = FuzzyMatcher.ratio("completely different", "xyz123 tofu");
        assertTrue(ratio >= 0 && ratio <= 100);
        final int partial = FuzzyMatcher.partialRatio("abcd", "wxyz abce vw");
        assertTrue(partial >= 0 && partial <= 100);
    }

    @Test
    public void testLargeSpansStayCorrect() {
        final StringBuilder first = new StringBuilder();
        final StringBuilder second = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            first.append('x');
            second.append('x');
        }
        second.setCharAt(750, 'y');
        assertEquals(0, FuzzyMatcher.levenshteinDistance(first, first));
        assertEquals(1, FuzzyMatcher.levenshteinDistance(first, second));
    }
}
