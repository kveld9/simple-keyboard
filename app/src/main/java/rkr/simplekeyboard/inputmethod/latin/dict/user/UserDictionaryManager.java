package rkr.simplekeyboard.inputmethod.latin.dict.user;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
    }

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

    private void notifyWordAdded(final UserDictionaryEntry entry) {
        mMainHandler.post(() -> {
            for (final UserDictionaryListener listener : mListeners) {
                listener.onWordAdded(entry);
            }
        });
    }

    private void notifyWordRemoved(final String word, final long id) {
        mMainHandler.post(() -> {
            for (final UserDictionaryListener listener : mListeners) {
                listener.onWordRemoved(word, id);
            }
        });
    }

    private void notifyAllLearnedWordsCleared() {
        mMainHandler.post(() -> {
            for (final UserDictionaryListener listener : mListeners) {
                listener.onAllLearnedWordsCleared();
            }
        });
    }

    private void notifyWordBlocked(final String word) {
        mMainHandler.post(() -> {
            for (final UserDictionaryListener listener : mListeners) {
                listener.onWordBlocked(word);
            }
        });
    }

    private void notifyWordUnblocked(final String word, final long id) {
        mMainHandler.post(() -> {
            for (final UserDictionaryListener listener : mListeners) {
                listener.onWordUnblocked(word, id);
            }
        });
    }

    private void notifyAllBlockedWordsCleared() {
        mMainHandler.post(() -> {
            for (final UserDictionaryListener listener : mListeners) {
                listener.onAllBlockedWordsCleared();
            }
        });
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
        final boolean success = mDatabase.clearAllWords();
        if (success) {
            notifyAllLearnedWordsCleared();
        }
        return success;
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
