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
 * Lightweight, direct-boot aware SQLite database for user-learned words and auto-learning dictionary.
 */
public class UserDictionaryDatabase extends SQLiteOpenHelper {
    private static final String TAG = "UserDictionaryDatabase";
    private static final String DATABASE_NAME = "user_dictionary.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "user_words";
    private static final String COL_ID = "id";
    private static final String COL_WORD = "word";
    private static final String COL_FREQUENCY = "frequency";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final int MAX_LEARNED_WORDS = 2000;
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
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
}
