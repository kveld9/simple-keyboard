package rkr.simplekeyboard.inputmethod.latin.dict;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MultiWordSplitterTest {

    private PrefixDictionary mDict;

    @Before
    public void setUp() {
        mDict = new PrefixDictionary();
        mDict.insert("no", 250);
        mDict.insert("tengo", 200);
        mDict.insert("de", 255);
        mDict.insert("los", 240);
        mDict.insert("como", 230);
        mDict.insert("cómo", 240);
        mDict.insert("te", 210);
        mDict.insert("va", 200);
        mDict.insert("por", 245);
        mDict.insert("favor", 220);
        mDict.insert("que", 250);
        mDict.insert("qué", 220);
        mDict.insert("porque", 240);

        mDict.setBigram("no", "tengo", 200);
        mDict.setBigram("de", "los", 220);
        mDict.setBigram("por", "favor", 230);
        mDict.setBigram("cómo", "te", 230);
        mDict.setBigram("te", "va", 220);
        mDict.setBigram("por", "que", 220);
        mDict.setBigram("que", "no", 210);
        mDict.setBigram("porque", "no", 230);
    }

    @Test
    public void testSplitBasic() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "notengo", null);
        assertNotNull(result);
        assertEquals("no", result.word1);
        assertEquals("tengo", result.word2);
        assertEquals("no tengo", result.combined);
    }

    @Test
    public void testSplitWithBigram() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "delos", null);
        assertNotNull(result);
        assertEquals("de", result.word1);
        assertEquals("los", result.word2);
        assertEquals("de los", result.combined);
    }

    @Test
    public void testSplitPorFavor() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "porfavor", null);
        assertNotNull(result);
        assertEquals("por", result.word1);
        assertEquals("favor", result.word2);
        assertEquals("por favor", result.combined);
    }

    @Test
    public void testShortWordNoSplit() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "no", null);
        assertNull(result);
    }

    @Test
    public void test3WordSplitClean() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "comoteva", null);
        assertNotNull(result);
        assertEquals("cómo", result.word1);
        assertEquals("te", result.word2);
        assertEquals("va", result.word3);
        assertEquals("cómo te va", result.combined);
        assertTrue(result.score >= 75.0f);
    }

    @Test
    public void test3WordSplitPorQueNo() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "porqueno", null);
        assertNotNull(result);
        assertTrue("porque no".equals(result.combined) || "por que no".equals(result.combined));
        assertTrue(result.score >= 75.0f);
    }

    @Test
    public void testSplitWithTypoPorqyeno() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "porqyeno", null);
        assertNotNull("Should split typo 'porqyeno'", result);
        assertTrue("Expected 'porque no' or 'por que no', but got: " + result.combined,
                "porque no".equals(result.combined) || "por que no".equals(result.combined));
        assertTrue(result.score >= 75.0f);
    }

    @Test
    public void testSplitWithComponentTypoNotengp() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.findBestSplit(mDict, "notengp", null);
        assertNotNull("Should split 'notengp' to 'no tengo'", result);
        assertEquals("no", result.word1);
        assertEquals("tengo", result.word2);
        assertEquals("no tengo", result.combined);
    }

    @Test
    public void testSplitConvenienceMethods() {
        MultiWordSplitter.SplitResult result = MultiWordSplitter.split(mDict, "delos");
        assertNotNull(result);
        assertEquals("de los", result.combined);

        MultiWordSplitter.SplitResult resultWithPrev = MultiWordSplitter.split(mDict, "tengo", "no");
        // "tengo" is single word, should not split
        assertNull(resultWithPrev);
    }
}
