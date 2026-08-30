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

import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class StringUtilsTest {

    @Test
    public void testNewSingleCodePointString() {
        assertEquals("a", StringUtils.newSingleCodePointString('a'));
        assertEquals("A", StringUtils.newSingleCodePointString('A'));
        // Unicode surrogate pair: musical symbol G clef (U+1D11E)
        final int gClef = 0x1D11E;
        final String gClefStr = StringUtils.newSingleCodePointString(gClef);
        assertEquals(2, gClefStr.length());
        assertEquals(gClef, gClefStr.codePointAt(0));
    }

    @Test
    public void testContainsInArray() {
        final String[] items = {"alpha", "beta", "gamma"};
        assertTrue(StringUtils.containsInArray("alpha", items));
        assertTrue(StringUtils.containsInArray("beta", items));
        assertFalse(StringUtils.containsInArray("delta", items));
    }

    @Test
    public void testToCodePointArray() {
        final String testStr = "abc";
        final int[] expected = {'a', 'b', 'c'};
        assertArrayEquals(expected, StringUtils.toCodePointArray(testStr));
    }

    @Test
    public void testToSortedCodePointArray() {
        final String testStr = "cba";
        final int[] expected = {'a', 'b', 'c'};
        assertArrayEquals(expected, StringUtils.toSortedCodePointArray(testStr));
    }

    @Test
    public void testIsIdenticalAfterUpcaseAndDowncase() {
        assertTrue(StringUtils.isIdenticalAfterUpcase("HELLO WORLD 123"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("Hello World"));

        assertTrue(StringUtils.isIdenticalAfterDowncase("hello world 123"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("Hello World"));
    }

    @Test
    public void testCapitalizeEachWord() {
        assertEquals("Hello World", StringUtils.capitalizeEachWord("hello world", Locale.ENGLISH));
        assertEquals("Simple Keyboard", StringUtils.capitalizeEachWord("simple keyboard", Locale.ENGLISH));
    }

    @Test
    public void testGermanSharpS() {
        // ß (U+00DF) -> uppercase ẞ (U+1E9E)
        assertEquals("\u1E9E", StringUtils.toTitleCaseOfKeyLabel("\u00DF", Locale.GERMAN));
        // ẞ (U+1E9E) -> lowercase ß (U+00DF)
        assertEquals("\u00DF", StringUtils.toLowerCaseOfKeyLabel("\u1E9E", Locale.GERMAN));
    }

    @Test
    public void testFoldChar() {
        assertEquals('a', StringUtils.foldChar('a'));
        assertEquals('a', StringUtils.foldChar('A'));
        assertEquals('a', StringUtils.foldChar('á'));
        assertEquals('a', StringUtils.foldChar('Á'));
        assertEquals('e', StringUtils.foldChar('é'));
        assertEquals('e', StringUtils.foldChar('É'));
        assertEquals('i', StringUtils.foldChar('í'));
        assertEquals('i', StringUtils.foldChar('Í'));
        assertEquals('o', StringUtils.foldChar('ó'));
        assertEquals('o', StringUtils.foldChar('Ó'));
        assertEquals('u', StringUtils.foldChar('ú'));
        assertEquals('u', StringUtils.foldChar('Ú'));
        assertEquals('n', StringUtils.foldChar('ñ'));
        assertEquals('n', StringUtils.foldChar('Ñ'));
        assertEquals('c', StringUtils.foldChar('ç'));
        assertEquals('c', StringUtils.foldChar('Ç'));
        assertEquals('z', StringUtils.foldChar('Z'));
        assertEquals('1', StringUtils.foldChar('1'));
    }

    @Test
    public void testToNormalizedLower() {
        assertEquals("", StringUtils.toNormalizedLower(null));
        assertEquals("", StringUtils.toNormalizedLower(""));
        assertEquals("arbol", StringUtils.toNormalizedLower("ÁRBOL"));
        assertEquals("cancion", StringUtils.toNormalizedLower("Canción"));
        assertEquals("nino", StringUtils.toNormalizedLower("Niño"));
        assertEquals("facil", StringUtils.toNormalizedLower("FÁCIL"));
        assertEquals("hello world", StringUtils.toNormalizedLower("Hello World"));
    }

    @Test
    public void testIsAllUpperCase() {
        assertFalse(StringUtils.isAllUpperCase(null));
        assertFalse(StringUtils.isAllUpperCase(""));
        assertFalse(StringUtils.isAllUpperCase("A")); // length <= 1 returns false
        assertFalse(StringUtils.isAllUpperCase("123")); // no letter
        assertTrue(StringUtils.isAllUpperCase("HELLO"));
        assertTrue(StringUtils.isAllUpperCase("HELLO WORLD"));
        assertTrue(StringUtils.isAllUpperCase("HELLO 123"));
        assertFalse(StringUtils.isAllUpperCase("Hello"));
        assertFalse(StringUtils.isAllUpperCase("hello"));
    }

    @Test
    public void testIsPunctuationOrSymbol() {
        assertTrue(StringUtils.isPunctuationOrSymbol('.'));
        assertTrue(StringUtils.isPunctuationOrSymbol(','));
        assertTrue(StringUtils.isPunctuationOrSymbol('!'));
        assertTrue(StringUtils.isPunctuationOrSymbol('?'));
        assertTrue(StringUtils.isPunctuationOrSymbol('@'));
        assertFalse(StringUtils.isPunctuationOrSymbol('a'));
        assertFalse(StringUtils.isPunctuationOrSymbol('Z'));
        assertFalse(StringUtils.isPunctuationOrSymbol('5'));
        assertFalse(StringUtils.isPunctuationOrSymbol(' ')); // codePoint 32
        assertFalse(StringUtils.isPunctuationOrSymbol('\n')); // codePoint 10
    }

    @Test
    public void testIsWordCharacter() {
        assertTrue(StringUtils.isWordCharacter('a'));
        assertTrue(StringUtils.isWordCharacter('Z'));
        assertTrue(StringUtils.isWordCharacter('á'));
        assertTrue(StringUtils.isWordCharacter('ñ'));
        assertTrue(StringUtils.isWordCharacter('\''));
        assertTrue(StringUtils.isWordCharacter('-'));
        assertFalse(StringUtils.isWordCharacter(' '));
        assertFalse(StringUtils.isWordCharacter('.'));
        assertFalse(StringUtils.isWordCharacter(','));
        assertFalse(StringUtils.isWordCharacter('1'));
    }
}
