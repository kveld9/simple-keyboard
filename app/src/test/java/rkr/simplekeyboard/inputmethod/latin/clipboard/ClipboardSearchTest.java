package rkr.simplekeyboard.inputmethod.latin.clipboard;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class ClipboardSearchTest {

    @Test
    public void testMatchesQueryEmptyOrNull() {
        assertTrue(ClipboardDatabase.matchesQuery("Hello World", null));
        assertTrue(ClipboardDatabase.matchesQuery("Hello World", ""));
        assertTrue(ClipboardDatabase.matchesQuery("Hello World", "   "));
        assertFalse(ClipboardDatabase.matchesQuery(null, "query"));
    }

    @Test
    public void testMatchesQueryCaseInsensitive() {
        assertTrue(ClipboardDatabase.matchesQuery("Hello World", "hello"));
        assertTrue(ClipboardDatabase.matchesQuery("hello world", "WORLD"));
        assertTrue(ClipboardDatabase.matchesQuery("Simple Keyboard", "KeyBoard"));
    }

    @Test
    public void testMatchesQuerySubstring() {
        assertTrue(ClipboardDatabase.matchesQuery("https://github.com/soyelmismo/simple-keyboard", "soyelmismo"));
        assertTrue(ClipboardDatabase.matchesQuery("https://github.com/soyelmismo/simple-keyboard", "github.com"));
        assertFalse(ClipboardDatabase.matchesQuery("https://github.com/soyelmismo/simple-keyboard", "gitlab"));
    }

    @Test
    public void testMatchesQueryUnicode() {
        // Cyrillic
        assertTrue(ClipboardDatabase.matchesQuery("ПРИВЕТ МИР", "привет"));
        assertTrue(ClipboardDatabase.matchesQuery("тестовая строка", "СТРОКА"));

        // Spanish accents
        assertTrue(ClipboardDatabase.matchesQuery("ESTÁ AQUÍ", "está"));
        assertTrue(ClipboardDatabase.matchesQuery("canción favorita", "CANCIÓN"));

        // Greek & Emojis
        assertTrue(ClipboardDatabase.matchesQuery("Ελληνικά κείμενα", "ελληνικά"));
        assertTrue(ClipboardDatabase.matchesQuery("Project 🔥 Rocket", "🔥"));
    }

    @Test
    public void testMatchesQueryWildcardLiterals() {
        // % and _ must be treated as literal characters, not SQL wildcards
        assertTrue(ClipboardDatabase.matchesQuery("100% discount", "%"));
        assertTrue(ClipboardDatabase.matchesQuery("100% discount", "100%"));
        assertFalse(ClipboardDatabase.matchesQuery("100 discount", "%"));

        assertTrue(ClipboardDatabase.matchesQuery("my_variable_name", "_"));
        assertTrue(ClipboardDatabase.matchesQuery("my_variable_name", "my_var"));
        assertFalse(ClipboardDatabase.matchesQuery("my variable name", "_"));

        assertTrue(ClipboardDatabase.matchesQuery("path\\to\\file", "\\"));
    }

    @Test
    public void testClipListFiltering() {
        List<ClipboardHistoryEntry> entries = new ArrayList<>();
        entries.add(new ClipboardHistoryEntry(1, "Pinned note with 100% effort", 1000L, true));
        entries.add(new ClipboardHistoryEntry(2, "Regular clip with 50% discount", 2000L, false));
        entries.add(new ClipboardHistoryEntry(3, "Unrelated text", 3000L, false));

        List<ClipboardHistoryEntry> filtered = new ArrayList<>();
        for (ClipboardHistoryEntry entry : entries) {
            if (ClipboardDatabase.matchesQuery(entry.text, "%")) {
                filtered.add(entry);
            }
        }

        assertEquals(2, filtered.size());
        assertTrue(filtered.get(0).isPinned);
        assertEquals(1, filtered.get(0).id);
        assertFalse(filtered.get(1).isPinned);
        assertEquals(2, filtered.get(1).id);
    }

    @Test
    public void testPinLimitsConstants() {
        assertEquals(50, ClipboardDatabase.MAX_CLIPS);
        assertEquals(50, ClipboardDatabase.MAX_PINNED_CLIPS);
    }

    @Test
    public void testMonotonicTokenSuperseding() {
        long currentToken = 0L;

        long token1 = ++currentToken;
        assertEquals(1L, token1);

        long token2 = ++currentToken;
        assertEquals(2L, token2);

        // Result from token1 arrives late
        boolean token1Valid = (token1 == currentToken);
        assertFalse(token1Valid);

        // Result from token2 arrives
        boolean token2Valid = (token2 == currentToken);
        assertTrue(token2Valid);
    }

    @Test
    public void testTypoTolerantSearch() {
        // Single typo still finds the clip ("clipboad" scores 89 against "clipboar").
        assertTrue(ClipboardDatabase.matchesQuery("clipboard history", "clipboad"));
        // Missing letter still finds the clip ("meting" scores 86 against "meeting").
        assertTrue(ClipboardDatabase.matchesQuery("meeting notes", "meting"));
    }

    @Test
    public void testTypoThresholdRejectsLookalikes() {
        // "gitlab" scores 67 against "github": below threshold, still rejected.
        assertFalse(ClipboardDatabase.matchesQuery(
                "https://github.com/soyelmismo/simple-keyboard", "gitlab"));
        assertFalse(ClipboardDatabase.matchesQuery("abc", "xyz"));
    }

    @Test
    public void testShortClipDoesNotMatchLongerQuery() {
        assertFalse(ClipboardDatabase.matchesQuery("a", "abc"));
        assertFalse(ClipboardDatabase.matchesQuery("hello", "say hello world"));
    }

    @Test
    public void testLongClipSkipsFuzzyFallback() {
        final StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longText.append("clipboard ");
        }
        final String text = longText.toString();
        // Exact substring still matches past the fuzzy length cap.
        assertTrue(ClipboardDatabase.matchesQuery(text, "clipboard"));
        // Typo no longer fuzzy-matches past the cap.
        assertFalse(ClipboardDatabase.matchesQuery(text, "clipboad"));
    }
}