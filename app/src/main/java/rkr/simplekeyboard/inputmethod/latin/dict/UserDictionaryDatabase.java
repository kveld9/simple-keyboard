package rkr.simplekeyboard.inputmethod.latin.dict;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight, direct-boot aware SQLite database for user-learned words and bigrams
 * with Forgetting Curve decay and stale word cleanup.
 */
public class UserDictionaryDatabase extends SQLiteOpenHelper {
    private static final String TAG = "UserDictionaryDatabase";
    private static final String DATABASE_NAME = "user_dictionary.db";
    private static final int DATABASE_VERSION = 3;

    private static final String PREF_NAME = "user_dict_prefs";
    private static final String KEY_LAST_CLEANUP = "last_user_dict_decay_cleanup";

    private static final String TABLE_NAME = "user_words";
    private static final String COL_ID = "id";
    private static final String COL_WORD = "word";
    private static final String COL_FREQ = "freq";
    private static final String COL_LAST_USED = "last_used";
    private static final String COL_CREATED_AT = "created_at";

    private static final String TABLE_BIGRAMS = "user_bigrams";
    private static final String COL_BIGRAM_ID = "id";
    private static final String COL_PREV_WORD = "prev_word";
    private static final String COL_BIGRAM_WORD = "word";
    private static final String COL_BIGRAM_FREQ = "freq";
    private static final String COL_BIGRAM_LAST_USED = "last_used";

    private static final int MAX_LEARNED_WORDS = 2000;
    private static final int MAX_LEARNED_BIGRAMS = 2000;
    public static final int BASE_LEARNED_FREQUENCY = 250;
    public static final int MAX_LEARNED_FREQUENCY = 255;
    public static final int FREQUENCY_STEP = 10;

    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000L;
    private static final long FOURTEEN_DAYS_MS = 14L * 24 * 60 * 60 * 1000L;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000L;

    private final Context mContext;

    public UserDictionaryDatabase(Context context) {
        super(getDeviceProtectedContext(context), DATABASE_NAME, null, DATABASE_VERSION);
        mContext = getDeviceProtectedContext(context);
    }

    private static Context getDeviceProtectedContext(Context context) {
        if (context == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.isDeviceProtectedStorage() ? context : context.createDeviceProtectedStorageContext();
        }
        return context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_WORD + " TEXT UNIQUE, " +
                COL_FREQ + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY + ", " +
                COL_LAST_USED + " INTEGER, " +
                COL_CREATED_AT + " INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word ON " + TABLE_NAME + " (" + COL_WORD + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_last_used ON " + TABLE_NAME + " (" + COL_LAST_USED + ")");

        db.execSQL("CREATE TABLE " + TABLE_BIGRAMS + " (" +
                COL_BIGRAM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PREV_WORD + " TEXT, " +
                COL_BIGRAM_WORD + " TEXT, " +
                COL_BIGRAM_FREQ + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY + ", " +
                COL_BIGRAM_LAST_USED + " INTEGER, " +
                "UNIQUE(" + COL_PREV_WORD + ", " + COL_BIGRAM_WORD + "))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_prev ON " + TABLE_BIGRAMS + " (" + COL_PREV_WORD + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_last_used ON " + TABLE_BIGRAMS + " (" + COL_BIGRAM_LAST_USED + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            upgradeToVersion2(db);
        }
        if (oldVersion < 3) {
            upgradeToVersion3(db);
        }
    }

    private void upgradeToVersion2(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BIGRAMS + " (" +
                COL_BIGRAM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PREV_WORD + " TEXT, " +
                COL_BIGRAM_WORD + " TEXT, " +
                COL_BIGRAM_FREQ + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY + ", " +
                COL_BIGRAM_LAST_USED + " INTEGER, " +
                "UNIQUE(" + COL_PREV_WORD + ", " + COL_BIGRAM_WORD + "))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_prev ON " + TABLE_BIGRAMS + " (" + COL_PREV_WORD + ")");
    }

    private void upgradeToVersion3(SQLiteDatabase db) {
        final long now = System.currentTimeMillis();
        final String nowStr = String.valueOf(now);

        // 1. Upgrade user_words
        ensureColumn(db, TABLE_NAME, COL_FREQ, "INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY, "frequency", null);
        ensureColumn(db, TABLE_NAME, COL_LAST_USED, "INTEGER", "timestamp", nowStr);
        ensureColumn(db, TABLE_NAME, COL_CREATED_AT, "INTEGER", "timestamp", nowStr);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_last_used ON " + TABLE_NAME + " (" + COL_LAST_USED + ")");

        // 2. Upgrade user_bigrams
        ensureColumn(db, TABLE_BIGRAMS, COL_BIGRAM_FREQ, "INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY, "frequency", null);
        ensureColumn(db, TABLE_BIGRAMS, COL_BIGRAM_LAST_USED, "INTEGER", "timestamp", nowStr);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_last_used ON " + TABLE_BIGRAMS + " (" + COL_BIGRAM_LAST_USED + ")");
    }

    private static void ensureColumn(SQLiteDatabase db, String table, String colName, String typeDef, String legacyCol, String defaultVal) {
        if (!columnExists(db, table, colName)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + colName + " " + typeDef);
            if (legacyCol != null && columnExists(db, table, legacyCol)) {
                db.execSQL("UPDATE " + table + " SET " + colName + " = " + legacyCol + " WHERE " + colName + " IS NULL");
            }
            if (defaultVal != null) {
                db.execSQL("UPDATE " + table + " SET " + colName + " = " + defaultVal + " WHERE " + colName + " IS NULL");
            }
        }
    }

    private static boolean columnExists(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (column.equalsIgnoreCase(cursor.getString(nameIndex))) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check column " + column + " in table " + table, e);
        }
        return false;
    }

    private static class WordData {
        final int freq;
        final long createdAt;
        WordData(int freq, long createdAt) {
            this.freq = freq;
            this.createdAt = createdAt;
        }
    }

    private WordData queryExistingWordData(SQLiteDatabase db, String word, long now) {
        try (Cursor cursor = db.query(TABLE_NAME, new String[]{COL_FREQ, COL_CREATED_AT}, COL_WORD + "=?",
                new String[]{word}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int freq = Math.min(MAX_LEARNED_FREQUENCY, cursor.getInt(0) + FREQUENCY_STEP);
                long createdAt = cursor.isNull(1) ? now : cursor.getLong(1);
                return new WordData(freq, createdAt);
            }
        }
        return new WordData(BASE_LEARNED_FREQUENCY, now);
    }

    private static void pruneTable(SQLiteDatabase db, String table, String idCol, String orderCol, int limit) {
        db.execSQL("DELETE FROM " + table + " WHERE " + idCol + " NOT IN (" +
                "SELECT " + idCol + " FROM " + table + " ORDER BY " + orderCol + " DESC LIMIT " + limit + ")");
    }

    public synchronized void learnWord(final String rawWord) {
        if (rawWord == null) return;
        final String word = rawWord.trim();
        if (word.length() <= 1 || word.length() > 64) {
            return;
        }

        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                final long now = System.currentTimeMillis();
                final WordData data = queryExistingWordData(db, word, now);

                ContentValues values = new ContentValues();
                values.put(COL_WORD, word);
                values.put(COL_FREQ, data.freq);
                values.put(COL_LAST_USED, now);
                values.put(COL_CREATED_AT, data.createdAt);

                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                pruneTable(db, TABLE_NAME, COL_ID, COL_LAST_USED, MAX_LEARNED_WORDS);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to learn word: " + word, e);
        }
    }

    public synchronized void unlearnWord(final String word) {
        if (word == null || word.isEmpty()) return;
        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete(TABLE_NAME, COL_WORD + "=?", new String[]{word});
                db.delete(TABLE_BIGRAMS, COL_PREV_WORD + "=? OR " + COL_BIGRAM_WORD + "=?", new String[]{word, word});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unlearn word: " + word, e);
        }
    }

    public synchronized Map<String, Integer> loadUserWords() {
        final Map<String, Integer> words = new HashMap<>();
        try {
            final SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(TABLE_NAME, new String[]{COL_WORD, COL_FREQ}, null, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        words.put(cursor.getString(0), cursor.getInt(1));
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load user words", e);
        }
        return words;
    }

    public synchronized Map<String, Integer> getAllLearnedWords() {
        return loadUserWords();
    }

    private int queryExistingBigramFrequency(SQLiteDatabase db, String prevWord, String word) {
        try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_BIGRAM_FREQ},
                COL_PREV_WORD + "=? AND " + COL_BIGRAM_WORD + "=?",
                new String[]{prevWord, word}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return Math.min(MAX_LEARNED_FREQUENCY, cursor.getInt(0) + FREQUENCY_STEP);
            }
        }
        return BASE_LEARNED_FREQUENCY;
    }

    private static boolean isValidBigramWord(String word) {
        return !word.isEmpty() && word.length() <= 64;
    }

    public synchronized void learnBigram(final String rawPrevWord, final String rawWord) {
        if (rawPrevWord == null || rawWord == null) return;
        final String prevWord = rawPrevWord.trim();
        final String word = rawWord.trim();
        if (!isValidBigramWord(prevWord) || !isValidBigramWord(word)) {
            return;
        }

        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                final long now = System.currentTimeMillis();
                final int freq = queryExistingBigramFrequency(db, prevWord, word);

                ContentValues values = new ContentValues();
                values.put(COL_PREV_WORD, prevWord);
                values.put(COL_BIGRAM_WORD, word);
                values.put(COL_BIGRAM_FREQ, freq);
                values.put(COL_BIGRAM_LAST_USED, now);

                db.insertWithOnConflict(TABLE_BIGRAMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                pruneTable(db, TABLE_BIGRAMS, COL_BIGRAM_ID, COL_BIGRAM_LAST_USED, MAX_LEARNED_BIGRAMS);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to learn bigram: " + prevWord + " -> " + word, e);
        }
    }

    public synchronized void unlearnBigram(final String prevWord, final String word) {
        if (prevWord == null || word == null) return;
        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_BIGRAMS, COL_PREV_WORD + "=? AND " + COL_BIGRAM_WORD + "=?", new String[]{prevWord, word});
        } catch (Exception e) {
            Log.w(TAG, "Failed to unlearn bigram: " + prevWord + " -> " + word, e);
        }
    }

    public synchronized int getBigramFrequency(final String prevWord, final String word) {
        if (prevWord == null || word == null) return 0;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_BIGRAM_FREQ},
                    COL_PREV_WORD + "=? AND " + COL_BIGRAM_WORD + "=?",
                    new String[]{prevWord, word}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query bigram frequency", e);
        }
        return 0;
    }

    public synchronized Map<String, Integer> getBigramsForWord(final String prevWord) {
        final Map<String, Integer> nextWords = new HashMap<>();
        if (prevWord == null || prevWord.isEmpty()) return nextWords;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_BIGRAM_WORD, COL_BIGRAM_FREQ},
                    COL_PREV_WORD + "=?", new String[]{prevWord}, null, null, COL_BIGRAM_FREQ + " DESC")) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        nextWords.put(cursor.getString(0), cursor.getInt(1));
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query bigrams for word: " + prevWord, e);
        }
        return nextWords;
    }

    public synchronized Map<String, Map<String, Integer>> loadUserBigrams() {
        final Map<String, Map<String, Integer>> allBigrams = new HashMap<>();
        try {
            final SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_PREV_WORD, COL_BIGRAM_WORD, COL_BIGRAM_FREQ},
                    null, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        final String prev = cursor.getString(0);
                        final String next = cursor.getString(1);
                        final int freq = cursor.getInt(2);
                        Map<String, Integer> map = allBigrams.get(prev);
                        if (map == null) {
                            map = new HashMap<>();
                            allBigrams.put(prev, map);
                        }
                        map.put(next, freq);
                    } while (cursor.moveToNext());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load all bigrams", e);
        }
        return allBigrams;
    }

    public synchronized Map<String, Map<String, Integer>> getAllBigrams() {
        return loadUserBigrams();
    }

    private void fetchWordsFromQuery(final String table, final String column, final String selection,
                                     final String[] selectionArgs, final String orderBy, final int queryLimit,
                                     final int targetLimit, final Set<String> added, final List<CharSequence> results) {
        try {
            final SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(table, new String[]{column}, selection, selectionArgs,
                    null, null, orderBy, String.valueOf(queryLimit))) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        final String w = cursor.getString(0);
                        if (w != null && added.add(w.toLowerCase())) {
                            results.add(w);
                        }
                    } while (cursor.moveToNext() && results.size() < targetLimit);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query words from " + table, e);
        }
    }

    private static void appendDefaultFallbacks(final int limit, final Set<String> added, final List<CharSequence> results) {
        final String[] defaultFallback = {"the", "to", "and", "a", "in", "is", "it", "you", "that", "he", "que", "de", "no", "la", "el", "es", "en"};
        for (String w : defaultFallback) {
            if (results.size() >= limit) break;
            if (added.add(w.toLowerCase())) {
                results.add(w);
            }
        }
    }

    public synchronized List<CharSequence> getNextWordPredictions(final String prevWord, final int limit) {
        final List<CharSequence> results = new ArrayList<>(Math.max(0, limit));
        if (limit <= 0) return results;
        final Set<String> added = new HashSet<>();

        if (prevWord != null && !prevWord.trim().isEmpty()) {
            fetchWordsFromQuery(TABLE_BIGRAMS, COL_BIGRAM_WORD, COL_PREV_WORD + "=?",
                    new String[]{prevWord.trim()}, COL_BIGRAM_FREQ + " DESC, " + COL_BIGRAM_LAST_USED + " DESC",
                    limit, limit, added, results);
        }

        if (results.size() < limit) {
            fetchWordsFromQuery(TABLE_NAME, COL_WORD, null, null,
                    COL_FREQ + " DESC, " + COL_LAST_USED + " DESC",
                    limit * 2, limit, added, results);
        }

        if (results.size() < limit) {
            appendDefaultFallbacks(limit, added, results);
        }

        return results;
    }

    /**
     * Executes the forgetting curve decay and cleans up stale/unreinforced words and bigrams.
     */
    public synchronized void decayAndCleanup() {
        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                final long now = System.currentTimeMillis();
                final long sevenDaysAgo = now - SEVEN_DAYS_MS;
                final long fourteenDaysAgo = now - FOURTEEN_DAYS_MS;

                // 1. Forgetting curve: decay words with > 7 days without use
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_FREQ + " = MAX(1, " + COL_FREQ + " - " + FREQUENCY_STEP + ") WHERE " + COL_LAST_USED + " < ?",
                        new Object[]{sevenDaysAgo});

                // 2. Forgetting curve: decay bigrams with > 7 days without use
                db.execSQL("UPDATE " + TABLE_BIGRAMS + " SET " + COL_BIGRAM_FREQ + " = MAX(1, " + COL_BIGRAM_FREQ + " - " + FREQUENCY_STEP + ") WHERE " + COL_BIGRAM_LAST_USED + " < ?",
                        new Object[]{sevenDaysAgo});

                // 3. Typo/stale cleanup: delete user words not used in > 14 days and with frequency <= BASE_LEARNED_FREQUENCY
                db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COL_FREQ + " <= " + BASE_LEARNED_FREQUENCY + " AND " + COL_LAST_USED + " < ?",
                        new Object[]{fourteenDaysAgo});

                // 4. Stale bigram cleanup: delete bigrams not used in > 14 days and with frequency <= BASE_LEARNED_FREQUENCY
                db.execSQL("DELETE FROM " + TABLE_BIGRAMS + " WHERE " + COL_BIGRAM_FREQ + " <= " + BASE_LEARNED_FREQUENCY + " AND " + COL_BIGRAM_LAST_USED + " < ?",
                        new Object[]{fourteenDaysAgo});

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed during decay and cleanup", e);
        }
    }

    /**
     * Executes decay and cleanup if more than 1 day has elapsed since the last run.
     */
    public synchronized void decayAndCleanupIfNecessary() {
        if (mContext == null) {
            decayAndCleanup();
            return;
        }
        try {
            final SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            final long now = System.currentTimeMillis();
            final long lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0L);
            if (now - lastCleanup >= ONE_DAY_MS) {
                decayAndCleanup();
                prefs.edit().putLong(KEY_LAST_CLEANUP, now).apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed checking cleanup interval", e);
        }
    }

    /**
     * Runs decay and cleanup non-blockingly on a background thread.
     */
    public void decayAndCleanupAsync() {
        new Thread(this::decayAndCleanupIfNecessary, "UserDict-DecayThread").start();
    }
}

