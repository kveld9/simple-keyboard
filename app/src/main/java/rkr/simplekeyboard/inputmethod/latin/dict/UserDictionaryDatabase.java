package rkr.simplekeyboard.inputmethod.latin.dict;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight, direct-boot aware SQLite database for user-learned words and bigrams.
 */
public class UserDictionaryDatabase extends SQLiteOpenHelper {
    private static final String TAG = "UserDictionaryDatabase";
    private static final String DATABASE_NAME = "user_dictionary.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_NAME = "user_words";
    private static final String COL_ID = "id";
    private static final String COL_WORD = "word";
    private static final String COL_FREQUENCY = "frequency";
    private static final String COL_TIMESTAMP = "timestamp";

    private static final String TABLE_BIGRAMS = "user_bigrams";
    private static final String COL_BIGRAM_ID = "id";
    private static final String COL_PREV_WORD = "prev_word";
    private static final String COL_BIGRAM_WORD = "word";
    private static final String COL_BIGRAM_FREQUENCY = "frequency";
    private static final String COL_BIGRAM_TIMESTAMP = "timestamp";

    private static final int MAX_LEARNED_WORDS = 2000;
    private static final int MAX_LEARNED_BIGRAMS = 2000;
    public static final int BASE_LEARNED_FREQUENCY = 260;

    public UserDictionaryDatabase(Context context) {
        super(getDeviceProtectedContext(context), DATABASE_NAME, null, DATABASE_VERSION);
    }

    private static Context getDeviceProtectedContext(Context context) {
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
                COL_FREQUENCY + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY + ", " +
                COL_TIMESTAMP + " INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word ON " + TABLE_NAME + " (" + COL_WORD + ")");

        db.execSQL("CREATE TABLE " + TABLE_BIGRAMS + " (" +
                COL_BIGRAM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PREV_WORD + " TEXT, " +
                COL_BIGRAM_WORD + " TEXT, " +
                COL_BIGRAM_FREQUENCY + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY + ", " +
                COL_BIGRAM_TIMESTAMP + " INTEGER, " +
                "UNIQUE(" + COL_PREV_WORD + ", " + COL_BIGRAM_WORD + "))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_prev ON " + TABLE_BIGRAMS + " (" + COL_PREV_WORD + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BIGRAMS + " (" +
                    COL_BIGRAM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_PREV_WORD + " TEXT, " +
                    COL_BIGRAM_WORD + " TEXT, " +
                    COL_BIGRAM_FREQUENCY + " INTEGER DEFAULT " + BASE_LEARNED_FREQUENCY + ", " +
                    COL_BIGRAM_TIMESTAMP + " INTEGER, " +
                    "UNIQUE(" + COL_PREV_WORD + ", " + COL_BIGRAM_WORD + "))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigram_prev ON " + TABLE_BIGRAMS + " (" + COL_PREV_WORD + ")");
        }
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
                int freq = BASE_LEARNED_FREQUENCY;
                try (Cursor cursor = db.query(TABLE_NAME, new String[]{COL_FREQUENCY}, COL_WORD + "=?",
                        new String[]{word}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        freq = Math.min(300, cursor.getInt(0) + 10);
                    }
                }

                ContentValues values = new ContentValues();
                values.put(COL_WORD, word);
                values.put(COL_FREQUENCY, freq);
                values.put(COL_TIMESTAMP, System.currentTimeMillis());

                db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);

                // Prune if exceeds max limit
                db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COL_ID + " NOT IN (" +
                        "SELECT " + COL_ID + " FROM " + TABLE_NAME + " ORDER BY " + COL_TIMESTAMP + " DESC LIMIT " + MAX_LEARNED_WORDS + ")");

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
            db.delete(TABLE_NAME, COL_WORD + "=?", new String[]{word});
        } catch (Exception e) {
            Log.w(TAG, "Failed to unlearn word: " + word, e);
        }
    }

    public synchronized Map<String, Integer> getAllLearnedWords() {
        final Map<String, Integer> words = new HashMap<>();
        try {
            final SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(TABLE_NAME, new String[]{COL_WORD, COL_FREQUENCY}, null, null, null, null, null)) {
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
                int freq = BASE_LEARNED_FREQUENCY;
                try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_BIGRAM_FREQUENCY},
                        COL_PREV_WORD + "=? AND " + COL_BIGRAM_WORD + "=?",
                        new String[]{prevWord, word}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        freq = Math.min(300, cursor.getInt(0) + 10);
                    }
                }

                ContentValues values = new ContentValues();
                values.put(COL_PREV_WORD, prevWord);
                values.put(COL_BIGRAM_WORD, word);
                values.put(COL_BIGRAM_FREQUENCY, freq);
                values.put(COL_BIGRAM_TIMESTAMP, System.currentTimeMillis());

                db.insertWithOnConflict(TABLE_BIGRAMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);

                // Prune if exceeds max limit
                db.execSQL("DELETE FROM " + TABLE_BIGRAMS + " WHERE " + COL_BIGRAM_ID + " NOT IN (" +
                        "SELECT " + COL_BIGRAM_ID + " FROM " + TABLE_BIGRAMS + " ORDER BY " + COL_BIGRAM_TIMESTAMP + " DESC LIMIT " + MAX_LEARNED_BIGRAMS + ")");

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
            try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_BIGRAM_FREQUENCY},
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
            try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_BIGRAM_WORD, COL_BIGRAM_FREQUENCY},
                    COL_PREV_WORD + "=?", new String[]{prevWord}, null, null, COL_BIGRAM_FREQUENCY + " DESC")) {
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

    public synchronized Map<String, Map<String, Integer>> getAllBigrams() {
        final Map<String, Map<String, Integer>> allBigrams = new HashMap<>();
        try {
            final SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(TABLE_BIGRAMS, new String[]{COL_PREV_WORD, COL_BIGRAM_WORD, COL_BIGRAM_FREQUENCY},
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
}

