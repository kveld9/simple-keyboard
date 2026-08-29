package rkr.simplekeyboard.inputmethod.latin.dict;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

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
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BIGRAMS + " (" +
                    COL_BIGRAM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_PREV_WORD + " TEXT, " +
                    COL_BIGRAM_WORD + " TEXT, " +
                    COL_BIGRAM_FREQ + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY + ", " +
                    COL_BIGRAM_LAST_USED + " INTEGER, " +
                    "UNIQUE(" + COL_PREV_WORD + ", " + COL_BIGRAM_WORD + "))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_prev ON " + TABLE_BIGRAMS + " (" + COL_PREV_WORD + ")");
        }

        if (oldVersion < 3) {
            final long now = System.currentTimeMillis();

            // 1. Upgrade user_words
            if (!columnExists(db, TABLE_NAME, COL_FREQ)) {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_FREQ + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY);
                if (columnExists(db, TABLE_NAME, "frequency")) {
                    db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_FREQ + " = frequency WHERE " + COL_FREQ + " IS NULL");
                }
            }
            if (!columnExists(db, TABLE_NAME, COL_LAST_USED)) {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_LAST_USED + " INTEGER");
                if (columnExists(db, TABLE_NAME, "timestamp")) {
                    db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_LAST_USED + " = timestamp WHERE " + COL_LAST_USED + " IS NULL");
                }
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_LAST_USED + " = " + now + " WHERE " + COL_LAST_USED + " IS NULL");
            }
            if (!columnExists(db, TABLE_NAME, COL_CREATED_AT)) {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_CREATED_AT + " INTEGER");
                if (columnExists(db, TABLE_NAME, "timestamp")) {
                    db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_CREATED_AT + " = timestamp WHERE " + COL_CREATED_AT + " IS NULL");
                }
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_CREATED_AT + " = " + now + " WHERE " + COL_CREATED_AT + " IS NULL");
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_last_used ON " + TABLE_NAME + " (" + COL_LAST_USED + ")");

            // 2. Upgrade user_bigrams
            if (!columnExists(db, TABLE_BIGRAMS, COL_BIGRAM_FREQ)) {
                db.execSQL("ALTER TABLE " + TABLE_BIGRAMS + " ADD COLUMN " + COL_BIGRAM_FREQ + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY);
                if (columnExists(db, TABLE_BIGRAMS, "frequency")) {
                    db.execSQL("UPDATE " + TABLE_BIGRAMS + " SET " + COL_BIGRAM_FREQ + " = frequency WHERE " + COL_BIGRAM_FREQ + " IS NULL");
                }
            }
            if (!columnExists(db, TABLE_BIGRAMS, COL_BIGRAM_LAST_USED)) {
                db.execSQL("ALTER TABLE " + TABLE_BIGRAMS + " ADD COLUMN " + COL_BIGRAM_LAST_USED + " INTEGER");
                if (columnExists(db, TABLE_BIGRAMS, "timestamp")) {
                    db.execSQL("UPDATE " + TABLE_BIGRAMS + " SET " + COL_BIGRAM_LAST_USED + " = timestamp WHERE " + COL_BIGRAM_LAST_USED + " IS NULL");
                }
                db.execSQL("UPDATE " + TABLE_BIGRAMS + " SET " + COL_BIGRAM_LAST_USED + " = " + now + " WHERE " + COL_BIGRAM_LAST_USED + " IS NULL");
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_last_used ON " + TABLE_BIGRAMS + " (" + COL_BIGRAM_LAST_USED + ")");
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
                int freq = BASE_LEARNED_FREQUENCY;
                long createdAt = now;

                try (Cursor cursor = db.query(TABLE_NAME, new String[]{COL_FREQ, COL_CREATED_AT}, COL_WORD + "=?",
                        new String[]{word}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        freq = Math.min(MAX_LEARNED_FREQUENCY, cursor.getInt(0) + FREQUENCY_STEP);
                        if (!cursor.isNull(1)) {
                            createdAt = cursor.getLong(1);
                        }
                    }
                }

                ContentValues values = new ContentValues();
                values.put(COL_WORD, word);
                values.put(COL_FREQ, freq);
                values.put(COL_LAST_USED, now);
                values.put(COL_CREATED_AT, createdAt);

                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);

                // Prune if exceeds max limit
                db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COL_ID + " NOT IN (" +
                        "SELECT " + COL_ID + " FROM " + TABLE_NAME + " ORDER BY " + COL_LAST_USED + " DESC LIMIT " + MAX_LEARNED_WORDS + ")");

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

    public synchronized void learnBigram(final String rawPrevWord, final String rawWord) {
        if (rawPrevWord == null || rawWord == null) return;
        final String prevWord = rawPrevWord.trim();
        final String word = rawWord.trim();
        if (prevWord.isEmpty() || word.isEmpty() || prevWord.length() > 64 || word.length() > 64) {
            return;
        }

        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                final long now = System.currentTimeMillis();
                int freq = BASE_LEARNED_FREQUENCY;
                try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_BIGRAM_FREQ},
                        COL_PREV_WORD + "=? AND " + COL_BIGRAM_WORD + "=?",
                        new String[]{prevWord, word}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        freq = Math.min(MAX_LEARNED_FREQUENCY, cursor.getInt(0) + FREQUENCY_STEP);
                    }
                }

                ContentValues values = new ContentValues();
                values.put(COL_PREV_WORD, prevWord);
                values.put(COL_BIGRAM_WORD, word);
                values.put(COL_BIGRAM_FREQ, freq);
                values.put(COL_BIGRAM_LAST_USED, now);

                db.insertWithOnConflict(TABLE_BIGRAMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);

                // Prune if exceeds max limit
                db.execSQL("DELETE FROM " + TABLE_BIGRAMS + " WHERE " + COL_BIGRAM_ID + " NOT IN (" +
                        "SELECT " + COL_BIGRAM_ID + " FROM " + TABLE_BIGRAMS + " ORDER BY " + COL_BIGRAM_LAST_USED + " DESC LIMIT " + MAX_LEARNED_BIGRAMS + ")");

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

