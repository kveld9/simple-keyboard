package rkr.simplekeyboard.inputmethod.latin.dict.binary;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BinaryTrieCompilerTest {

    @Rule
    public TemporaryFolder mTempFolder = new TemporaryFolder();

    @Test
    public void testCompileAndLoad() throws Exception {
        final List<BinaryTrieCompiler.WordEntry> words = new ArrayList<>();
        words.add(new BinaryTrieCompiler.WordEntry("hola", 200));
        words.add(new BinaryTrieCompiler.WordEntry("holanda", 150));
        words.add(new BinaryTrieCompiler.WordEntry("bien", 220));
        words.add(new BinaryTrieCompiler.WordEntry("también", 210));
        words.add(new BinaryTrieCompiler.WordEntry("canción", 180));

        final File tempFile = mTempFolder.newFile("test_dict.bin");
        BinaryTrieCompiler.compile(words, tempFile);

        assertTrue(tempFile.exists());
        assertTrue(tempFile.length() > 16);

        try (FileInputStream fis = new FileInputStream(tempFile);
             FileChannel channel = fis.getChannel()) {
            final ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, tempFile.length());
            final BinaryTrieDictionary dict = new BinaryTrieDictionary(buffer);

            assertEquals(5, dict.getWordCount());
            assertTrue(dict.containsWord("hola"));
            assertTrue(dict.containsWord("holanda"));
            assertTrue(dict.containsWord("bien"));
            assertTrue(dict.containsWord("también"));
            assertTrue(dict.containsWord("canción"));
            assertFalse(dict.containsWord("bién"));
            assertFalse(dict.containsWord("nonexistent"));

            assertEquals(200, dict.getWordFrequency("hola"));
            assertEquals(220, dict.getWordFrequency("bien"));
            assertEquals(210, dict.getWordFrequency("también"));

            final List<CharSequence> suggestions = dict.getPrefixSuggestions("hol", 5);
            assertNotNull(suggestions);
            assertTrue(suggestions.size() >= 2);
            assertEquals("también", dict.getCanonicalWord("tambien"));
        }
    }
}
