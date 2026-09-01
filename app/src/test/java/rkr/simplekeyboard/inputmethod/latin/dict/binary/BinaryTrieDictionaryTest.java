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
    public void setUp() throws Exception {
        final List<BinaryTrieCompiler.WordEntry> esWords = new java.util.ArrayList<>();
        esWords.add(new BinaryTrieCompiler.WordEntry("que", 250));
        esWords.add(new BinaryTrieCompiler.WordEntry("qué", 220));
        esWords.add(new BinaryTrieCompiler.WordEntry("hay", 200));
        esWords.add(new BinaryTrieCompiler.WordEntry("el", 240));
        esWords.add(new BinaryTrieCompiler.WordEntry("rara", 100));
        esWords.add(new BinaryTrieCompiler.WordEntry("bien", 220));
        esWords.add(new BinaryTrieCompiler.WordEntry("hola", 210));
        esWords.add(new BinaryTrieCompiler.WordEntry("también", 210));
        esWords.add(new BinaryTrieCompiler.WordEntry("esta", 200));
        esWords.add(new BinaryTrieCompiler.WordEntry("está", 220));
        esWords.add(new BinaryTrieCompiler.WordEntry("hoy", 190));
        esWords.add(new BinaryTrieCompiler.WordEntry("hora", 180));
        esWords.add(new BinaryTrieCompiler.WordEntry("hecho", 170));
        esWords.add(new BinaryTrieCompiler.WordEntry("hemos", 160));
        esWords.add(new BinaryTrieCompiler.WordEntry("manzana", 150));
        esWords.add(new BinaryTrieCompiler.WordEntry("casa", 220));
        esWords.add(new BinaryTrieCompiler.WordEntry("casas", 180));
        for (int i = 0; i < 50; i++) {
            esWords.add(new BinaryTrieCompiler.WordEntry("a" + (char)('a' + (i % 26)) + i, 100 + (i % 50)));
        }

        final List<BinaryTrieCompiler.WordEntry> enWords = new java.util.ArrayList<>();
        enWords.add(new BinaryTrieCompiler.WordEntry("the", 250));
        enWords.add(new BinaryTrieCompiler.WordEntry("and", 240));

        final File esFile = File.createTempFile("test_es_", ".bin");
        esFile.deleteOnExit();
        BinaryTrieCompiler.compile(esWords, esFile);

        final File enFile = File.createTempFile("test_en_", ".bin");
        enFile.deleteOnExit();
        BinaryTrieCompiler.compile(enWords, enFile);

        try (FileInputStream fis = new FileInputStream(esFile);
             FileChannel channel = fis.getChannel()) {
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, esFile.length());
            esDict = new BinaryTrieDictionary(buffer);
        }

        try (FileInputStream fis = new FileInputStream(enFile);
             FileChannel channel = fis.getChannel()) {
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, enFile.length());
            enDict = new BinaryTrieDictionary(buffer);
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
    public void testPrefixSuggestionsFindsWordsAcrossMultipleBranches() {
        List<CharSequence> suggestions = esDict.getPrefixSuggestions("h", 30);
        assertNotNull(suggestions);
        assertTrue("Should collect suggestions across multiple branches", suggestions.size() > 0);
        boolean foundHoBranch = false;
        boolean foundHeBranch = false;
        for (CharSequence s : suggestions) {
            String w = s.toString().toLowerCase();
            if (w.startsWith("ho")) {
                foundHoBranch = true;
            }
            if (w.startsWith("he")) {
                foundHeBranch = true;
            }
        }
        assertTrue("Prefix 'h' must discover words in 'ho...' branch (e.g. hoy, hola, hora)", foundHoBranch);
        assertTrue("Prefix 'h' must discover words in 'he...' branch (e.g. hecho, hemos)", foundHeBranch);
    }

    @Test
    public void testFuzzySearchFindsMatchWithCostZeroPriority() {
        assertTrue("Dictionary must contain 'manzana'", esDict.containsWord("manzana"));
        List<rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary.ScoredWord> candidates = new java.util.ArrayList<>();
        esDict.searchFuzzy(esDict.getRootNode(), new StringBuilder(), "mwnzana", 0, 1, candidates);
        boolean foundManzana = false;
        for (rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary.ScoredWord sw : candidates) {
            if ("manzana".equalsIgnoreCase(sw.word)) {
                foundManzana = true;
                break;
            }
        }
        assertTrue("Fuzzy search for 'mwnzana' must find 'manzana' without getting trapped in earlier branches", foundManzana);

        candidates.clear();
        esDict.searchFuzzy(esDict.getRootNode(), new StringBuilder(), "hxla", 0, 1, candidates);
        boolean foundHola = false;
        for (rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary.ScoredWord sw : candidates) {
            if ("hola".equalsIgnoreCase(sw.word)) {
                foundHola = true;
                break;
            }
        }
        assertTrue("Fuzzy search for 'hxla' must find 'hola'", foundHola);
    }

    @Test
    public void testPrefixSuggestionsBoundariesAndLimits() {
        // Limit 1, 5, 40 boundary tests
        List<CharSequence> res1 = esDict.getPrefixSuggestions("a", 1);
        assertNotNull(res1);
        assertEquals(1, res1.size());

        List<CharSequence> res5 = esDict.getPrefixSuggestions("a", 5);
        assertNotNull(res5);
        assertEquals(5, res5.size());

        List<CharSequence> res40 = esDict.getPrefixSuggestions("a", 40);
        assertNotNull(res40);
        assertEquals(40, res40.size());

        // Empty / non-matching prefix
        List<CharSequence> resNone = esDict.getPrefixSuggestions("zzxxqq123", 10);
        assertNotNull(resNone);
        assertEquals(0, resNone.size());
    }

    @Test
    public void testFuzzySearchAdversarialCases() {
        List<rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary.ScoredWord> candidates = new java.util.ArrayList<>();

        // 1. Exact match (Cost 0)
        esDict.searchFuzzy(esDict.getRootNode(), new StringBuilder(), "casa", 0, 0, candidates);
        assertTrue("Exact match search for 'casa' with distance 0 must find 'casa'",
                candidates.stream().anyMatch(sw -> "casa".equalsIgnoreCase(sw.word)));

        // 2. Extra character typed by user (deletion from target)
        candidates.clear();
        esDict.searchFuzzy(esDict.getRootNode(), new StringBuilder(), "casasx", 0, 1, candidates);
        assertTrue("Fuzzy search for 'casasx' must find 'casas'",
                candidates.stream().anyMatch(sw -> "casas".equalsIgnoreCase(sw.word)));

        // 3. Missing character typed by user (insertion into target)
        candidates.clear();
        esDict.searchFuzzy(esDict.getRootNode(), new StringBuilder(), "csa", 0, 1, candidates);
        assertTrue("Fuzzy search for 'csa' must find 'casa'",
                candidates.stream().anyMatch(sw -> "casa".equalsIgnoreCase(sw.word)));

        // 4. Non-matching input (negative case)
        candidates.clear();
        esDict.searchFuzzy(esDict.getRootNode(), new StringBuilder(), "zzqqxx123", 0, 1, candidates);
        assertEquals("Fuzzy search for impossible word must return 0 candidates", 0, candidates.size());
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
        assertTrue("Should traverse words", count[0] >= 50);
        assertTrue("Should find 'que'", foundQue[0]);
    }
}
