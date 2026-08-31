package rkr.simplekeyboard.inputmethod.latin.dict.binary;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class BinaryTrieDictionaryTest {
    
    private BinaryTrieDictionary esDict;
    private BinaryTrieDictionary enDict;

    @Before
    public void setUp() throws IOException {
        esDict = loadDictionary("dict_es.bin");
        enDict = loadDictionary("dict_en.bin");
    }

    private BinaryTrieDictionary loadDictionary(String filename) throws IOException {
        File file = new File("dictionaries/" + filename);
        if (!file.exists()) {
            file = new File("../dictionaries/" + filename);
        }
        if (!file.exists()) {
            throw new RuntimeException("Test dictionary not found: " + filename);
        }
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel channel = fis.getChannel()) {
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            return new BinaryTrieDictionary(buffer);
        }
    }

    @Test
    public void testContainsWord() {
        assertTrue("Should contain 'que'", esDict.containsWord("que"));
        assertTrue("Should contain 'qué'", esDict.containsWord("qué"));
        assertTrue("Should contain 'hay'", esDict.containsWord("hay"));
        assertTrue("Should contain 'el'", esDict.containsWord("el"));
        assertTrue("Should contain 'rara'", esDict.containsWord("rara"));
        assertTrue("Should contain 'the'", enDict.containsWord("the"));
        
        assertTrue("Should contain 'bien'", esDict.containsWord("bien"));
        assertFalse("Should not contain 'bién'", esDict.containsWord("bién"));
        assertTrue("Should contain 'hola'", esDict.containsWord("hola"));
        assertFalse("Should not contain 'holá'", esDict.containsWord("holá"));
        assertTrue("Should contain 'también'", esDict.containsWord("también"));
        assertFalse("Should not contain 'tí'", esDict.containsWord("tí"));
        assertFalse("Should not contain 'nonexistentword123'", esDict.containsWord("nonexistentword123"));
    }

    @Test
    public void testGetWordFrequency() {
        int freqQue = esDict.getWordFrequency("que");
        assertTrue("Frequency should be valid", freqQue > 0);
        
        int freqQueAccent = esDict.getWordFrequency("qué");
        assertTrue("Frequency should be valid", freqQueAccent > 0);
    }

    @Test
    public void testGetCanonicalWord() {
        // "que" is both unaccented and in the dictionary
        // "qué" should be found when asking for canonical of "que" (wait, which is more frequent?)
        String canonicalQue = esDict.getCanonicalWord("que");
        assertNotNull(canonicalQue);
        // It should match unaccented 'que' chars
        assertTrue(canonicalQue.equals("que") || canonicalQue.equals("qué"));
        
        // "esta" -> "está" or "esta"
        String canonicalEsta = esDict.getCanonicalWord("esta");
        assertNotNull(canonicalEsta);
    }

    @Test
    public void testGetPrefixSuggestions() {
        List<CharSequence> suggestions = esDict.getPrefixSuggestions("qu", 5);
        assertNotNull(suggestions);
        assertTrue("Should have some suggestions", suggestions.size() > 0);
    }

    @Test
    public void testNavigationMethods() {
        int root = esDict.getRootNode();
        assertTrue(root > 0);
        
        int childQ = esDict.getChildNode(root, 'q');
        assertTrue(childQ > 0);
        
        int childU = esDict.getChildNode(childQ, 'u');
        assertTrue(childU > 0);
        
        char[] outChars = new char[10];
        int[] outOffsets = new int[10];
        int count = esDict.getChildren(childU, outChars, outOffsets);
        assertTrue(count > 0);
    }

    @Test
    public void testForEachWord() {
        int[] count = new int[1];
        boolean[] foundQue = new boolean[1];
        esDict.forEachWord((word, freq) -> {
            count[0]++;
            if ("que".equals(word) && freq > 0) {
                foundQue[0] = true;
            }
        });
        assertTrue("Should traverse many words", count[0] > 10000);
        assertTrue("Should find 'que'", foundQue[0]);
    }
}
