package rkr.simplekeyboard.inputmethod.latin.dict.user;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import rkr.simplekeyboard.inputmethod.latin.dict.PrefixDictionary;

public final class UserDictionaryManager {
    public interface UserDictionaryListener {
        default void onWordAdded(UserDictionaryEntry entry) {}
        default void onWordRemoved(String word, long id) {}
        default void onAllLearnedWordsCleared() {}
        default void onWordBlocked(String word) {}
        default void onWordUnblocked(String word, long id) {}
        default void onAllBlockedWordsCleared() {}
        default void onBigramAdded(UserBigramEntry entry) {}
        default void onAllBigramsCleared() {}
    }

    public static final long DEFAULT_DECAY_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000L;
    public static final int DEFAULT_DECAY_STEP = 25;

    private static volatile UserDictionaryManager sInstance;

    private final UserDictionaryDatabase mDatabase;
    private final List<UserDictionaryListener> mListeners = new CopyOnWriteArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public static UserDictionaryManager getInstance(final Context context) {
        if (sInstance == null) {
            synchronized (UserDictionaryManager.class) {
                if (sInstance == null) {
                    sInstance = new UserDictionaryManager(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    public static void initForTesting(final UserDictionaryDatabase database) {
        sInstance = new UserDictionaryManager(database);
    }

    private UserDictionaryManager(final Context context) {
        mDatabase = new UserDictionaryDatabase(context);
    }

    private UserDictionaryManager(final UserDictionaryDatabase database) {
        mDatabase = database;
    }

    public void addListener(final UserDictionaryListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(final UserDictionaryListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    @FunctionalInterface
    private interface ListenerAction {
        void execute(UserDictionaryListener listener);
    }

    private void notifyListeners(final ListenerAction action) {
        mMainHandler.post(() -> {
            for (final UserDictionaryListener listener : mListeners) {
                action.execute(listener);
            }
        });
    }

    private void notifyWordAdded(final UserDictionaryEntry entry) {
        notifyListeners(listener -> listener.onWordAdded(entry));
    }

    private void notifyWordRemoved(final String word, final long id) {
        notifyListeners(listener -> listener.onWordRemoved(word, id));
    }

    private void notifyAllLearnedWordsCleared() {
        notifyListeners(UserDictionaryListener::onAllLearnedWordsCleared);
    }

    private void notifyWordBlocked(final String word) {
        notifyListeners(listener -> listener.onWordBlocked(word));
    }

    private void notifyWordUnblocked(final String word, final long id) {
        notifyListeners(listener -> listener.onWordUnblocked(word, id));
    }

    private void notifyAllBlockedWordsCleared() {
        notifyListeners(UserDictionaryListener::onAllBlockedWordsCleared);
    }

    private void notifyBigramAdded(final UserBigramEntry entry) {
        notifyListeners(listener -> listener.onBigramAdded(entry));
    }

    private void notifyAllBigramsCleared() {
        notifyListeners(UserDictionaryListener::onAllBigramsCleared);
    }

    // --- Learned Words ---

    public boolean addWord(final String word) {
        return addWord(word, PrefixDictionary.BASE_LEARNED_FREQUENCY, null);
    }

    public boolean addWord(final String word, final int frequency) {
        return addWord(word, frequency, null);
    }

    public boolean addWord(final String word, final int frequency, final String shortcut) {
        final boolean success = mDatabase.insertOrUpdateWord(word, frequency, shortcut);
        if (success) {
            notifyWordAdded(new UserDictionaryEntry(word, frequency));
        }
        return success;
    }

    public boolean removeWord(final String word) {
        final boolean success = mDatabase.deleteWord(word);
        if (success) {
            notifyWordRemoved(word, -1);
        }
        return success;
    }

    public boolean removeWordById(final long id) {
        final String word = mDatabase.getWordById(id);
        final boolean success = mDatabase.deleteWordById(id);
        if (success) {
            notifyWordRemoved(word, id);
        }
        return success;
    }

    public boolean removeWords(final List<UserDictionaryEntry> entries) {
        if (entries == null || entries.isEmpty()) return true;
        final List<Long> ids = new ArrayList<>(entries.size());
        for (final UserDictionaryEntry entry : entries) {
            ids.add(entry.id);
        }
        final boolean success = mDatabase.deleteWordsByIds(ids);
        if (success) {
            mMainHandler.post(() -> {
                for (final UserDictionaryListener listener : mListeners) {
                    for (final UserDictionaryEntry entry : entries) {
                        listener.onWordRemoved(entry.word, entry.id);
                    }
                }
            });
        }
        return success;
    }

    public boolean isLearned(final String word) {
        return mDatabase.isWordLearned(word);
    }

    public List<UserDictionaryEntry> getLearnedWords() {
        return mDatabase.getAllWords(null);
    }

    public List<UserDictionaryEntry> getLearnedWords(final String query) {
        return mDatabase.getAllWords(query);
    }

    public int getLearnedWordsCount() {
        return mDatabase.getWordsCount();
    }

    public boolean clearLearnedWords() {
        final boolean success = mDatabase.clearAllWords() && mDatabase.clearAllBigrams();
        if (success) {
            notifyAllLearnedWordsCleared();
        }
        return success;
    }

    // --- Bigram Operations ---

    public boolean addBigram(final String prevWord, final String word) {
        return addBigram(prevWord, word, PrefixDictionary.BASE_LEARNED_FREQUENCY, System.currentTimeMillis());
    }

    public boolean addBigram(final String prevWord, final String word, final int frequency) {
        return addBigram(prevWord, word, frequency, System.currentTimeMillis());
    }

    public boolean addBigram(final String prevWord, final String word, final int frequency, final long timestamp) {
        final boolean success = mDatabase.addOrUpdateBigram(prevWord, word, frequency, timestamp);
        if (success) {
            notifyBigramAdded(new UserBigramEntry(-1, prevWord, word, frequency, timestamp));
        }
        return success;
    }

    public List<UserBigramEntry> getBigrams() {
        return mDatabase.getAllBigrams();
    }

    public List<UserBigramEntry> getAllBigrams() {
        return mDatabase.getAllBigrams();
    }

    public int getBigramFrequency(final String prevWord, final String word) {
        return mDatabase.getBigramFrequency(prevWord, word);
    }

    public int getBigramsCount() {
        return mDatabase.getBigramsCount();
    }

    public boolean deleteBigram(final String prevWord, final String word) {
        return mDatabase.deleteBigram(prevWord, word);
    }

    public boolean clearBigrams() {
        final boolean success = mDatabase.clearAllBigrams();
        if (success) {
            notifyAllBigramsCleared();
        }
        return success;
    }

    // --- Temporal Decay Operations ---

    public boolean applyDecay(final long currentTimestamp, final long decayIntervalMillis, final int decayStep) {
        return mDatabase.applyDecay(currentTimestamp, decayIntervalMillis, decayStep);
    }

    public void runDecayAsync(final java.util.concurrent.Executor executor, final long decayIntervalMillis, final int decayStep) {
        if (executor != null) {
            executor.execute(() -> applyDecay(System.currentTimeMillis(), decayIntervalMillis, decayStep));
        } else {
            new Thread(() -> applyDecay(System.currentTimeMillis(), decayIntervalMillis, decayStep), "UserDictDecayThread").start();
        }
    }

    public void runDecayAsync(final java.util.concurrent.Executor executor) {
        runDecayAsync(executor, DEFAULT_DECAY_INTERVAL_MILLIS, DEFAULT_DECAY_STEP);
    }

    public void runDecayAsync() {
        runDecayAsync(null, DEFAULT_DECAY_INTERVAL_MILLIS, DEFAULT_DECAY_STEP);
    }

    // --- Blocked Words ---

    public boolean blockWord(final String word) {
        final boolean success = mDatabase.insertBlockedWord(word);
        if (success) {
            notifyWordBlocked(word);
        }
        return success;
    }

    public boolean unblockWord(final String word) {
        final boolean success = mDatabase.deleteBlockedWord(word);
        if (success) {
            notifyWordUnblocked(word, -1);
        }
        return success;
    }

    public boolean unblockWordById(final long id) {
        final String word = mDatabase.getBlockedWordById(id);
        final boolean success = mDatabase.deleteBlockedWordById(id);
        if (success) {
            notifyWordUnblocked(word, id);
        }
        return success;
    }

    public boolean unblockWords(final List<UserDictionaryEntry> entries) {
        if (entries == null || entries.isEmpty()) return true;
        final List<Long> ids = new ArrayList<>(entries.size());
        for (final UserDictionaryEntry entry : entries) {
            ids.add(entry.id);
        }
        final boolean success = mDatabase.deleteBlockedWordsByIds(ids);
        if (success) {
            mMainHandler.post(() -> {
                for (final UserDictionaryListener listener : mListeners) {
                    for (final UserDictionaryEntry entry : entries) {
                        listener.onWordUnblocked(entry.word, entry.id);
                    }
                }
            });
        }
        return success;
    }

    public boolean isBlocked(final String word) {
        return mDatabase.isWordBlocked(word);
    }

    public List<UserDictionaryEntry> getBlockedWords() {
        return mDatabase.getAllBlockedWords(null);
    }

    public List<UserDictionaryEntry> getBlockedWords(final String query) {
        return mDatabase.getAllBlockedWords(query);
    }

    public int getBlockedWordsCount() {
        return mDatabase.getBlockedWordsCount();
    }

    public boolean clearBlockedWords() {
        final boolean success = mDatabase.clearAllBlockedWords();
        if (success) {
            notifyAllBlockedWordsCleared();
        }
        return success;
    }
}
