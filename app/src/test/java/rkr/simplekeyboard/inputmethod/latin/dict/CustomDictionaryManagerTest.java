package rkr.simplekeyboard.inputmethod.latin.dict;

import org.junit.Test;
import static org.junit.Assert.*;

public class CustomDictionaryManagerTest {

    @Test
    public void testIsValidLanguageCode() {
        assertTrue(CustomDictionaryManager.isValidLanguageCode("es"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("en"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("pt"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("de"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("fr"));
        assertTrue(CustomDictionaryManager.isValidLanguageCode("it"));

        assertFalse(CustomDictionaryManager.isValidLanguageCode(null));
        assertFalse(CustomDictionaryManager.isValidLanguageCode(""));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("e"));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("español"));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("12"));
        assertFalse(CustomDictionaryManager.isValidLanguageCode("xyz99"));
    }

    @Test
    public void testParseLanguageFromFilename() {
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es.bin"));
        assertEquals("en", CustomDictionaryManager.parseLanguageFromFilename("dict_en.bin"));
        assertEquals("en", CustomDictionaryManager.parseLanguageFromFilename("dict_en_us.bin"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("dict_es.dict"));
        assertEquals("es", CustomDictionaryManager.parseLanguageFromFilename("es.bin"));
        assertEquals("pt", CustomDictionaryManager.parseLanguageFromFilename("/storage/emulated/0/Download/dict_pt_br.bin"));
        assertEquals("fr", CustomDictionaryManager.parseLanguageFromFilename("dict_fr.bin"));

        // Negative and edge cases
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("foo.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("dict_.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("dict_xx.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("dict_english.bin"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename(null));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename(""));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("   "));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("random_file.txt"));
        assertNull(CustomDictionaryManager.parseLanguageFromFilename("my_custom_words.bin"));
    }
}
