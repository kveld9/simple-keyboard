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

    @Test
    public void testUserBigramEntryCreation() {
        long now = System.currentTimeMillis();
        UserBigramEntry entry = new UserBigramEntry(42L, "how", "are", 250, now);

        assertEquals(42L, entry.id);
        assertEquals("how", entry.prevWord);
        assertEquals("word must match", "are", entry.word);
        assertEquals(250, entry.frequency);
        assertEquals(now, entry.timestamp);
    }

    @Test
    public void testUserBigramEntryConvenienceConstructor() {
        UserBigramEntry entry = new UserBigramEntry("good", "morning", 200);

        assertEquals(-1L, entry.id);
        assertEquals("good", entry.prevWord);
        assertEquals("morning", entry.word);
        assertEquals(200, entry.frequency);
        assertTrue(entry.timestamp > 0);
    }

    @Test
    public void testUserBigramEntryEqualityAndHashCode() {
        long now = System.currentTimeMillis();
        UserBigramEntry entry1 = new UserBigramEntry(1L, "how", "are", 250, now);
        UserBigramEntry entry2 = new UserBigramEntry(2L, "how", "are", 250, now + 1000);
        UserBigramEntry entry3 = new UserBigramEntry(3L, "how", "is", 250, now);

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
        assertNotNull(entry1.toString());
        assertTrue(entry1.toString().contains("how"));
        assertTrue(entry1.toString().contains("are"));
        org.junit.Assert.assertNotEquals(entry1, entry3);
    }
}
