package rkr.simplekeyboard.inputmethod.latin.dict.user;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class UserDictionaryConcurrencyAndStressTest {

    private PrefixDictionary mPrefixDict;

    @Before
    public void setUp() {
        mPrefixDict = new PrefixDictionary();
    }

    @Test
    public void testConcurrentInsertAndQueryPrefixDictionary() throws Exception {
        final int threadCount = 8;
        final int wordsPerThread = 200;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < wordsPerThread; i++) {
                        String word = "word_" + threadId + "_" + i;
                        mPrefixDict.insert(word, 100 + (i % 50));
                        mPrefixDict.getSuggestions("word_" + threadId, 5);
                        mPrefixDict.containsWord(word);
                        if (i % 20 == 0) {
                            mPrefixDict.blockWord("blocked_" + threadId + "_" + i);
                            mPrefixDict.isBlocked("blocked_" + threadId + "_" + i);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("Timeout in concurrent dictionary test", doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals("Encountered errors during concurrent execution", 0, errors.get());
        assertTrue(mPrefixDict.getWordCount() >= threadCount * wordsPerThread);
    }

    @Test
    public void testConcurrentBlockAndUnblockCycle() throws Exception {
        final int threadCount = 4;
        final int cycles = 150;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.execute(() -> {
                try {
                    for (int i = 0; i < cycles; i++) {
                        String word = "cycle_word_" + threadId + "_" + i;
                        mPrefixDict.insert(word, 200);
                        assertTrue(mPrefixDict.containsWord(word));

                        mPrefixDict.blockWord(word);
                        assertTrue(mPrefixDict.isBlocked(word));
                        assertFalse(mPrefixDict.containsWord(word));

                        mPrefixDict.unblockWord(word);
                        assertFalse(mPrefixDict.isBlocked(word));
                        assertTrue(mPrefixDict.containsWord(word));

                        mPrefixDict.removeWord(word);
                        assertFalse(mPrefixDict.containsWord(word));
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("Timeout in block/unblock stress cycle", latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(0, errors.get());
    }

    @Test
    public void testLearnedVsBlockedIndependence() {
        // 1. Add word
        mPrefixDict.insert("paradigma", 250);
        assertTrue(mPrefixDict.containsWord("paradigma"));
        assertFalse(mPrefixDict.isBlocked("paradigma"));

        // 2. Block it
        mPrefixDict.blockWord("paradigma");
        assertTrue(mPrefixDict.isBlocked("paradigma"));
        assertFalse(mPrefixDict.containsWord("paradigma"));

        // 3. Clear learned words
        mPrefixDict.clearLearnedWords();
        assertEquals(0, mPrefixDict.getWordCount());
        assertTrue("Blocked status must remain even after clearing learned words",
                mPrefixDict.isBlocked("paradigma"));

        // 4. Unblock it
        mPrefixDict.unblockWord("paradigma");
        assertFalse(mPrefixDict.isBlocked("paradigma"));

        // 5. Clear blocked words
        mPrefixDict.blockWord("otro");
        assertTrue(mPrefixDict.isBlocked("otro"));
        mPrefixDict.clearBlockedWords();
        assertFalse(mPrefixDict.isBlocked("otro"));
    }

    @Test
    public void testUnicodeAndAccentNormalization() {
        mPrefixDict.insert("Canción", 250);
        assertTrue(mPrefixDict.containsWord("canción"));
        assertTrue(mPrefixDict.containsWord("Canción"));
        assertTrue(mPrefixDict.containsWord("CANCIÓN"));

        List<CharSequence> suggestions = mPrefixDict.getSuggestions("canc", 5);
        assertFalse(suggestions.isEmpty());

        mPrefixDict.blockWord("Canción");
        assertTrue(mPrefixDict.isBlocked("canción"));
        assertTrue(mPrefixDict.isBlocked("cancion"));
        assertTrue(mPrefixDict.isBlocked("CANCIÓN"));
        assertFalse(mPrefixDict.containsWord("canción"));

        mPrefixDict.unblockWord("cancion");
        assertFalse(mPrefixDict.isBlocked("canción"));
        assertTrue(mPrefixDict.containsWord("Canción"));
    }
}
