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

package rkr.simplekeyboard.inputmethod.latin.dict;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class PrefixDictionaryTest {

    private PrefixDictionary mDict;

    @Before
    public void setUp() {
        mDict = new PrefixDictionary();
    }

    @Test
    public void testBasicPrefixSearch() {
        mDict.insert("keyboard", 100);
        mDict.insert("key", 50);
        mDict.insert("keypad", 80);
        mDict.insert("simple", 90);

        List<CharSequence> results = mDict.getSuggestions("key", 5);
        assertEquals(3, results.size());
        assertEquals("key", results.get(0)); // exact match prioritized
        assertEquals("keyboard", results.get(1)); // then by frequency
        assertEquals("keypad", results.get(2));
    }

    @Test
    public void testCasePreservation() {
        mDict.insert("hello", 100);
        mDict.insert("help", 80);

        // Title case
        List<CharSequence> titleResults = mDict.getSuggestions("He", 5);
        assertEquals(2, titleResults.size());
        assertEquals("Hello", titleResults.get(0));
        assertEquals("Help", titleResults.get(1));

        // Uppercase
        List<CharSequence> upperResults = mDict.getSuggestions("HEL", 5);
        assertEquals(2, upperResults.size());
        assertEquals("HELLO", upperResults.get(0));
        assertEquals("HELP", upperResults.get(1));
    }

    @Test
    public void testPrefixSuggestionsSorting() {
        mDict.insert("android", 200);
        mDict.insert("apple", 150);
        mDict.insert("apartment", 100);
        mDict.insert("application", 180);

        assertEquals(4, mDict.getWordCount());
        List<CharSequence> results = mDict.getSuggestions("ap", 3);
        assertEquals(3, results.size());
        assertEquals("application", results.get(0)); // 180
        assertEquals("apple", results.get(1));       // 150
        assertEquals("apartment", results.get(2));   // 100
    }

    @Test
    public void testEmptyAndNonMatchingPrefix() {
        mDict.insert("test", 10);
        assertTrue(mDict.getSuggestions("", 5).isEmpty());
        assertTrue(mDict.getSuggestions("xyz", 5).isEmpty());
        assertTrue(mDict.getSuggestions(null, 5).isEmpty());
    }

    @Test
    public void testFuzzyAutocorrection() {
        mDict.insert("teclado", 100);
        mDict.insert("hello", 100);
        mDict.insert("simple", 90);

        // Exact match should return null (no correction needed)
        assertEquals(null, mDict.getBestCorrection("teclado"));

        // 1-character deletion typo (missing 'd') -> should correct to "teclado"
        assertEquals("teclado", mDict.getBestCorrection("teclao"));

        // 1-character substitution typo ('a' instead of 'e') -> "hallo" -> "hello"
        assertEquals("hello", mDict.getBestCorrection("hallo"));

        // 1-character transposition typo -> "helol" -> "hello"
        assertEquals("hello", mDict.getBestCorrection("helol"));

        // Case preservation in autocorrection
        assertEquals("Hello", mDict.getBestCorrection("Helol"));
        assertEquals("HELLO", mDict.getBestCorrection("HELOL"));

        // Accent-folding autocorrection (unaccented input -> accented dictionary word)
        mDict.insert("autocorrección", 100);
        mDict.insert("también", 100);
        assertEquals("autocorrección", mDict.getBestCorrection("autocorreccion"));
        assertEquals("Autocorrección", mDict.getBestCorrection("Autocorreccion"));
        assertEquals("AUTOCORRECCIÓN", mDict.getBestCorrection("AUTOCORRECCION"));
        assertEquals("también", mDict.getBestCorrection("tambien"));
        assertEquals("También", mDict.getBestCorrection("Tambien"));

        // 2-letter typo correction (e.g. "qe" -> "que")
        mDict.insert("que", 250);
        assertEquals("que", mDict.getBestCorrection("qe"));
        List<CharSequence> fuzzyQe = mDict.getFuzzySuggestions("qe", 2);
        assertTrue(fuzzyQe.contains("que"));

        // Too short or non-correctable
        assertEquals(null, mDict.getBestCorrection("a"));
        assertEquals(null, mDict.getBestCorrection("xyzabc"));
    }

    @Test
    public void testComputeWeightedDistance() {
        // Equal strings -> 0
        assertEquals(0.0f, PrefixDictionary.computeWeightedDistance("test", "test"), 0.001f);

        // Physical adjacency substitution (cost 0.5): 'a' adjacent to 's'
        assertEquals(0.5f, PrefixDictionary.computeWeightedDistance("a", "s"), 0.001f);

        // Non-adjacent substitution (cost 1.0): 'a' not adjacent to 'p'
        assertEquals(1.0f, PrefixDictionary.computeWeightedDistance("a", "p"), 0.001f);

        // Transposition (cost 0.5)
        assertEquals(0.5f, PrefixDictionary.computeWeightedDistance("ab", "ba"), 0.001f);

        // Insertion / Deletion (cost 1.0)
        assertEquals(1.0f, PrefixDictionary.computeWeightedDistance("abc", "ab"), 0.001f);
        assertEquals(1.0f, PrefixDictionary.computeWeightedDistance("ab", "abc"), 0.001f);
    }

    @Test
    public void testLongWordCorrectionBonus() {
        // <= 6 chars -> no bonus
        assertEquals(0.0f, PrefixDictionary.getLongWordCorrectionBonus("simple", "simple"), 0.001f);
        assertEquals(0.0f, PrefixDictionary.getLongWordCorrectionBonus("cat", "cot"), 0.001f);

        // > 6 chars -> bonus proportional to length
        float bonus7 = PrefixDictionary.getLongWordCorrectionBonus("teclados", "teclado");
        assertTrue(bonus7 > 0.0f);

        float bonus14 = PrefixDictionary.getLongWordCorrectionBonus("reconociminto", "reconocimiento");
        assertTrue(bonus14 > bonus7);
    }

    @Test
    public void testCalcNormalizedScore() {
        // Exact match gets huge priority
        float exactScore = PrefixDictionary.calcNormalizedScore("hola", "hola", 100);
        assertTrue(exactScore >= 1100.0f);

        // Adjacent typo scores higher than non-adjacent typo
        // 'w' adjacent to 'q', 'p' not adjacent to 'q'
        float adjScore = PrefixDictionary.calcNormalizedScore("w", "q", 100);
        float nonAdjScore = PrefixDictionary.calcNormalizedScore("p", "q", 100);
        assertTrue("Adjacent typo score (" + adjScore + ") should be higher than non-adjacent (" + nonAdjScore + ")",
                adjScore > nonAdjScore);
    }

    @Test
    public void testValidWordProtection() {
        mDict.insert("in", 200);
        mDict.insert("on", 150);

        // "in" is a valid word. It should not be autocorrected to "on" even though they are close
        assertEquals(null, mDict.getBestCorrection("in"));

        // Direct accent replacement should still happen for valid/unaccented variants
        mDict.insert("canción", 250);
        assertEquals("canción", mDict.getBestCorrection("cancion"));
    }

    @Test
    public void testBigramContextSuggestionsAndCorrections() {
        mDict.insert("buenos", 100);
        mDict.insert("días", 100);
        mDict.insert("noches", 90);
        mDict.insert("tardes", 80);

        // Without bigram, "días" is at top
        mDict.setBigram("buenos", "noches", 200);
        assertEquals(200, mDict.getBigramFrequency("buenos", "noches"));

        // When "buenos" is the previous word, "noches" receives the bigram boost
        List<CharSequence> suggestions = mDict.getSuggestions("n", 3, "buenos");
        assertEquals("noches", suggestions.get(0));

        // Fuzzy correction with bigram context
        mDict.insert("amigo", 50);
        mDict.insert("amiga", 50);
        mDict.setBigram("mi", "amiga", 250);

        // Typos with previous word "mi": "amgo" -> should prioritize "amiga" if strong bigram
        CharSequence corr = mDict.getBestCorrection("amga", "mi");
        assertEquals("amiga", corr);
    }

    @Test
    public void testAutoCorrectionThresholdSettings() {
        mDict.insert("teclado", 100);
        mDict.insert("reconocer", 100);

        // 1. Modest threshold (default 1.0f)
        assertEquals(1.0f, mDict.getAutoCorrectionThreshold(), 0.001f);
        assertEquals("teclado", mDict.getBestCorrection("teclao"));

        // 2. Off threshold (0.0f) -> No corrections performed
        mDict.setAutoCorrectionThreshold(0.0f);
        assertEquals(0.0f, mDict.getAutoCorrectionThreshold(), 0.001f);
        assertEquals(null, mDict.getBestCorrection("teclao"));
        assertEquals(null, mDict.getBestCorrection("autocorreccion"));

        // 3. Aggressive threshold (2.0f)
        mDict.setAutoCorrectionThreshold(2.0f);
        assertEquals(2.0f, mDict.getAutoCorrectionThreshold(), 0.001f);
        assertEquals("teclado", mDict.getBestCorrection("teclao"));

        // 4. Very Aggressive threshold (3.0f) -> Supports distance 2 on longer words
        mDict.setAutoCorrectionThreshold(3.0f);
        assertEquals(3.0f, mDict.getAutoCorrectionThreshold(), 0.001f);
        assertEquals("teclado", mDict.getBestCorrection("teclao"));
        // 2-error typo on 9-letter word: "reocnoer" (missing 'c' and transposition) -> "reconocer"
        assertEquals("reconocer", mDict.getBestCorrection("reocnoer"));

        // 5. copyFrom preserves threshold
        PrefixDictionary target = new PrefixDictionary();
        target.copyFrom(mDict);
        assertEquals(3.0f, target.getAutoCorrectionThreshold(), 0.001f);
    }

    @Test
    public void testSpecialWordProtection() {
        mDict.insert("tiempo", 100);
        mDict.insert("problema", 100);
        mDict.insert("árbol", 100);
        mDict.insert("google", 100);

        // 1. Numbers / Digits (mp3, h2o, v2, 100km)
        assertTrue(PrefixDictionary.hasDigits("mp3"));
        assertTrue(PrefixDictionary.hasDigits("h2o"));
        assertTrue(PrefixDictionary.hasDigits("v2"));
        assertTrue(PrefixDictionary.hasDigits("100km"));
        assertEquals(null, mDict.getBestCorrection("mp3"));
        assertEquals(null, mDict.getBestCorrection("h2o"));
        assertEquals(null, mDict.getBestCorrection("v2"));
        assertEquals(null, mDict.getBestCorrection("100km"));
        assertEquals(null, mDict.getExactNormalizedCorrection("mp3"));

        // 2. URLs / Emails (@, .)
        assertTrue(PrefixDictionary.hasUrlOrEmailSymbol("correo@gmail.com"));
        assertTrue(PrefixDictionary.hasUrlOrEmailSymbol("google.com"));
        assertTrue(PrefixDictionary.hasUrlOrEmailSymbol("app.kt"));
        assertEquals(null, mDict.getBestCorrection("correo@gmail.com"));
        assertEquals(null, mDict.getBestCorrection("google.com"));
        assertEquals(null, mDict.getBestCorrection("app.kt"));
        assertEquals(null, mDict.getExactNormalizedCorrection("google.com"));

        // 3. Intermediate uppercase / CamelCase / Acronyms
        assertTrue(PrefixDictionary.hasIntermediateUpperCase("WiFi"));
        assertTrue(PrefixDictionary.hasIntermediateUpperCase("ChatGPT"));
        assertTrue(PrefixDictionary.hasIntermediateUpperCase("iPhone"));
        assertTrue(PrefixDictionary.hasIntermediateUpperCase("PlayStation"));
        assertTrue(PrefixDictionary.hasIntermediateUpperCase("McDonalds"));
        assertEquals(null, mDict.getBestCorrection("WiFi"));
        assertEquals(null, mDict.getBestCorrection("ChatGPT"));
        assertEquals(null, mDict.getBestCorrection("iPhone"));
        assertEquals(null, mDict.getBestCorrection("PlayStation"));
        assertEquals(null, mDict.getBestCorrection("McDonalds"));

        // Standard casing should NOT be blocked
        assertEquals("árbol", mDict.getBestCorrection("arbol"));
        assertEquals("Árbol", mDict.getBestCorrection("Arbol"));
        assertEquals("ÁRBOL", mDict.getBestCorrection("ARBOL"));
    }

    @Test
    public void testTranspositionDetection() {
        mDict.insert("tiempo", 100);
        mDict.insert("problema", 100);
        mDict.insert("gracias", 100);

        // Fast typing adjacent swaps
        assertEquals("tiempo", mDict.getBestCorrection("tiemop"));
        assertEquals("problema", mDict.getBestCorrection("porblema"));
        assertEquals("gracias", mDict.getBestCorrection("gracais"));

        // Transposition cost check
        assertEquals(0.5f, PrefixDictionary.computeWeightedDistance("tiemop", "tiempo"), 0.001f);
        assertEquals(0.5f, PrefixDictionary.computeWeightedDistance("porblema", "problema"), 0.001f);
        assertEquals(0.5f, PrefixDictionary.computeWeightedDistance("gracais", "gracias"), 0.001f);
    }

    @Test
    public void testApplyCasingAndInheritance() {
        assertEquals("ÁRBOL", StringUtils.applyCasing("ARBOL", "árbol"));
        assertEquals("Canción", StringUtils.applyCasing("Cancion", "canción"));
        assertEquals("canción", StringUtils.applyCasing("cancion", "canción"));
        assertEquals("HELLO", StringUtils.applyCasing("HEL", "hello"));
        assertEquals("Hello", StringUtils.applyCasing("He", "hello"));
        assertEquals("hello", StringUtils.applyCasing("he", "HELLO"));

        mDict.insert("árbol", 100);
        mDict.insert("canción", 100);

        assertEquals("ÁRBOL", mDict.getBestCorrection("ARBOL"));
        assertEquals("Árbol", mDict.getBestCorrection("Arbol"));
        assertEquals("árbol", mDict.getBestCorrection("arbol"));
        assertEquals("CANCIÓN", mDict.getBestCorrection("CANCION"));
        assertEquals("Canción", mDict.getBestCorrection("Cancion"));
        assertEquals("canción", mDict.getBestCorrection("cancion"));
    }

    @Test
    public void testAccentRestorationWithoutPenalty() {
        mDict.insert("árbol", 100);
        mDict.insert("también", 100);
        mDict.insert("música", 100);
        mDict.insert("fácil", 100);

        assertEquals(0.0f, PrefixDictionary.computeWeightedDistance("arbol", "árbol"), 0.001f);
        assertEquals(0.0f, PrefixDictionary.computeWeightedDistance("tambien", "también"), 0.001f);
        assertEquals(0.0f, PrefixDictionary.computeWeightedDistance("musica", "música"), 0.001f);
        assertEquals(0.0f, PrefixDictionary.computeWeightedDistance("facil", "fácil"), 0.001f);

        assertEquals("árbol", mDict.getBestCorrection("arbol"));
        assertEquals("también", mDict.getBestCorrection("tambien"));
        assertEquals("música", mDict.getBestCorrection("musica"));
        assertEquals("fácil", mDict.getBestCorrection("facil"));
    }



    @Test
    public void testGetNextWordPredictionsWithBigrams() {
        mDict.insert("how", 100);
        mDict.insert("are", 90);
        mDict.insert("is", 80);
        mDict.insert("do", 70);

        mDict.setBigram("how", "are", 250);
        mDict.setBigram("how", "is", 200);
        mDict.setBigram("how", "do", 150);

        List<CharSequence> preds = mDict.getNextWordPredictions("how", 3);
        assertEquals(3, preds.size());
        assertEquals("are", preds.get(0).toString());
        assertEquals("is", preds.get(1).toString());
        assertEquals("do", preds.get(2).toString());
    }

    @Test
    public void testGetNextWordPredictionsFallbackToTopWords() {
        mDict.insert("the", 250);
        mDict.insert("to", 240);
        mDict.insert("and", 230);
        mDict.insert("world", 100);

        mDict.setBigram("hello", "world", 255);

        // "hello" only has 1 bigram ("world"), so remaining 2 slots fall back to top frequent words ("the", "to")
        List<CharSequence> preds = mDict.getNextWordPredictions("hello", 3);
        assertEquals(3, preds.size());
        assertEquals("world", preds.get(0).toString());
        assertEquals("the", preds.get(1).toString());
        assertEquals("to", preds.get(2).toString());
    }

    @Test
    public void testGetNextWordPredictionsChainingAndAccents() {
        mDict.insert("cómo", 200);
        mDict.insert("estás", 180);
        mDict.insert("hoy", 160);

        mDict.setBigram("cómo", "estás", 250);
        mDict.setBigram("estás", "hoy", 240);

        List<CharSequence> step1 = mDict.getNextWordPredictions("cómo", 1);
        assertEquals(1, step1.size());
        assertEquals("estás", step1.get(0).toString());

        List<CharSequence> step2 = mDict.getNextWordPredictions("estás", 1);
        assertEquals(1, step2.size());
        assertEquals("hoy", step2.get(0).toString());
    }

    @Test
    public void testGetNextWordPredictionsEmptyOrInvalid() {
        mDict.insert("test", 100);
        assertTrue(mDict.getNextWordPredictions("test", 0).isEmpty());
        assertTrue(mDict.getNextWordPredictions("test", -1).isEmpty());

        List<CharSequence> predsNull = mDict.getNextWordPredictions(null, 2);
        assertEquals(2, predsNull.size());

        List<CharSequence> predsEmpty = mDict.getNextWordPredictions("", 2);
        assertEquals(2, predsEmpty.size());
    }

    @Test
    public void testGetNextWordPredictionsCopyAndClear() {
        mDict.insert("hello", 100);
        mDict.insert("world", 90);
        mDict.setBigram("hello", "world", 200);

        PrefixDictionary copied = new PrefixDictionary();
        copied.copyFrom(mDict);

        List<CharSequence> copiedPreds = copied.getNextWordPredictions("hello", 2);
        assertEquals(2, copiedPreds.size());
        assertEquals("world", copiedPreds.get(0).toString());

        mDict.clear();
        assertEquals(0, mDict.getWordCount());
    }
}
