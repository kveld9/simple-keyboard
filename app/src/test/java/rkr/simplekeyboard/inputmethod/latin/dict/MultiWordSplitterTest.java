package rkr.simplekeyboard.inputmethod.latin.dict;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

        mDict.setBigram("no", "tengo", 200);
        mDict.setBigram("de", "los", 220);
        mDict.setBigram("por", "favor", 230);
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
}
