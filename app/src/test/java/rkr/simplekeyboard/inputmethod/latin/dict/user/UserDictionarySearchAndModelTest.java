package rkr.simplekeyboard.inputmethod.latin.dict.user;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class UserDictionarySearchAndModelTest {

    @Test
    public void testUserDictionaryEntryCreation() {
        long now = System.currentTimeMillis();
        UserDictionaryEntry entry = new UserDictionaryEntry(1L, "Café", "cafe", 250, "cf", now);

        assertEquals(1L, entry.id);
        assertEquals("Café", entry.word);
        assertEquals("cafe", entry.normalizedWord);
        assertEquals(250, entry.frequency);
        assertEquals("cf", entry.shortcut);
        assertEquals(now, entry.timestamp);
    }

    @Test
    public void testConvenienceConstructor() {
        UserDictionaryEntry entry = new UserDictionaryEntry("SimpleKeyboard", 200);

        assertEquals(-1L, entry.id);
        assertEquals("SimpleKeyboard", entry.word);
        assertEquals("simplekeyboard", entry.normalizedWord);
        assertEquals(200, entry.frequency);
        assertTrue(entry.timestamp > 0);
    }

    @Test
    public void testSanitizeWord() {
        assertNull(UserDictionaryDatabase.sanitizeWord(null));
        assertNull(UserDictionaryDatabase.sanitizeWord(""));
        assertNull(UserDictionaryDatabase.sanitizeWord("   "));
        assertNull(UserDictionaryDatabase.sanitizeWord("word\nwith\nnewline"));
        assertNull(UserDictionaryDatabase.sanitizeWord("word\twith\ttab"));
        assertNull(UserDictionaryDatabase.sanitizeWord("a".repeat(50))); // exceeds MAX_WORD_LENGTH

        assertEquals("hello", UserDictionaryDatabase.sanitizeWord("  hello  "));
        assertEquals("canción", UserDictionaryDatabase.sanitizeWord("canción"));
        assertEquals("привет", UserDictionaryDatabase.sanitizeWord("привет"));
        assertEquals("🔥🚀", UserDictionaryDatabase.sanitizeWord("🔥🚀"));
    }

    @Test
    public void testEscapeLikePattern() {
        assertEquals("", UserDictionaryDatabase.escapeLikePattern(null));
        assertEquals("hello", UserDictionaryDatabase.escapeLikePattern("hello"));
        assertEquals("100\\%", UserDictionaryDatabase.escapeLikePattern("100%"));
        assertEquals("my\\_var", UserDictionaryDatabase.escapeLikePattern("my_var"));
        assertEquals("path\\\\to\\\\file", UserDictionaryDatabase.escapeLikePattern("path\\to\\file"));
    }
}
