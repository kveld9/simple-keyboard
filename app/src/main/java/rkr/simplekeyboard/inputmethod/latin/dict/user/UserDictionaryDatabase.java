/*
 * Copyright (C) 2026 Simple Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.dict.user;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

public class UserDictionaryDatabase extends SQLiteOpenHelper {

    private static final String TAG = "UserDictDb";
    private static final String DATABASE_NAME = "user_dictionary.db";
    private static final int DATABASE_VERSION = 1;

    public static final int MAX_WORD_LENGTH = 48;
    public static final int MAX_QUERY_LENGTH = 48;

    // Table: words (learned and custom words)
    private static final String TABLE_WORDS = "words";
    private static final String COL_WORDS_ID = "id";
    private static final String COL_WORDS_WORD = "word";
    private static final String COL_WORDS_NORM = "normalized_word";
    private static final String COL_WORDS_FREQ = "frequency";
    private static final String COL_WORDS_SHORTCUT = "shortcut";
    private static final String COL_WORDS_TIMESTAMP = "timestamp";

    // Table: blocked_words
    private static final String TABLE_BLOCKED = "blocked_words";
    private static final String COL_BLOCKED_ID = "id";
    private static final String COL_BLOCKED_WORD = "word";
    private static final String COL_BLOCKED_NORM = "normalized_word";
    private static final String COL_BLOCKED_TIMESTAMP = "timestamp";

    public UserDictionaryDatabase(final Context context) {
        super(PreferenceManagerCompat.getDeviceContext(context), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        final String createWordsTable = "CREATE TABLE " + TABLE_WORDS + " (" +
                COL_WORDS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_WORDS_WORD + " TEXT NOT NULL, " +
                COL_WORDS_NORM + " TEXT NOT NULL UNIQUE, " +
                COL_WORDS_FREQ + " INTEGER DEFAULT 250, " +
                COL_WORDS_SHORTCUT + " TEXT, " +
                COL_WORDS_TIMESTAMP + " INTEGER NOT NULL)";
        db.execSQL(createWordsTable);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_words_norm ON " + TABLE_WORDS + " (" + COL_WORDS_NORM + ")");

        final String createBlockedTable = "CREATE TABLE " + TABLE_BLOCKED + " (" +
                COL_BLOCKED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_BLOCKED_WORD + " TEXT NOT NULL, " +
                COL_BLOCKED_NORM + " TEXT NOT NULL UNIQUE, " +
                COL_BLOCKED_TIMESTAMP + " INTEGER NOT NULL)";
        db.execSQL(createBlockedTable);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_blocked_norm ON " + TABLE_BLOCKED + " (" + COL_BLOCKED_NORM + ")");
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        // Initial version; migrations will be handled here if schema changes in future versions.
    }

    @Override
    public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        // Safe downgrade strategy
    }

    public static String sanitizeWord(final String word) {
        if (word == null) return null;
        final String trimmed = word.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_WORD_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            if (Character.isISOControl(c) || c == '\n' || c == '\r' || c == '\t') {
                return null;
            }
        }
        return trimmed;
    }

    public static String escapeLikePattern(final String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }

    // --- Learned / User Words Operations ---

    public synchronized boolean insertOrUpdateWord(final String word, final int frequency, final String shortcut) {
        final String cleanWord = sanitizeWord(word);
        if (cleanWord == null) {
            return false;
        }
        final String normWord = StringUtils.toNormalizedLower(cleanWord);
        final int targetFreq = Math.max(1, frequency);
        final String targetShortcut = (shortcut != null && !shortcut.trim().isEmpty()) ? shortcut.trim() : null;
        final long timestamp = System.currentTimeMillis();

        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getWritableDatabase();
            cursor = db.query(TABLE_WORDS, new String[]{COL_WORDS_WORD, COL_WORDS_FREQ, COL_WORDS_SHORTCUT},
                    COL_WORDS_NORM + "=?", new String[]{normWord}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final String existingWord = cursor.getString(0);
                final int existingFreq = cursor.getInt(1);
                final String existingShortcut = cursor.getString(2);
                final String normExistingShortcut = (existingShortcut != null && !existingShortcut.trim().isEmpty())
                        ? existingShortcut.trim() : null;

                final boolean wordMatches = cleanWord.equals(existingWord);
                final boolean shortcutMatches = (targetShortcut == null && normExistingShortcut == null)
                        || (targetShortcut != null && targetShortcut.equals(normExistingShortcut));

                if (wordMatches && shortcutMatches && existingFreq >= targetFreq) {
                    return false;
                }
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            final ContentValues values = new ContentValues();
            values.put(COL_WORDS_WORD, cleanWord);
            values.put(COL_WORDS_NORM, normWord);
            values.put(COL_WORDS_FREQ, targetFreq);
            if (targetShortcut != null) {
                values.put(COL_WORDS_SHORTCUT, targetShortcut);
            }
            values.put(COL_WORDS_TIMESTAMP, timestamp);

            final long result = db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            return result != -1;
        } catch (Throwable e) {
            Log.e(TAG, "Error inserting/updating word", e);
            return false;
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
    }

    public synchronized boolean deleteWord(final String word) {
        final String cleanWord = sanitizeWord(word);
        if (cleanWord == null) {
            return false;
        }
        final String normWord = StringUtils.toNormalizedLower(cleanWord);
        try {
            final SQLiteDatabase db = getWritableDatabase();
            final int rows = db.delete(TABLE_WORDS, COL_WORDS_NORM + "=?", new String[]{normWord});
            return rows > 0;
        } catch (Throwable e) {
            Log.e(TAG, "Error deleting word", e);
            return false;
        }
    }

    public synchronized boolean deleteWordById(final long id) {
        try {
            final SQLiteDatabase db = getWritableDatabase();
            final int rows = db.delete(TABLE_WORDS, COL_WORDS_ID + "=?", new String[]{String.valueOf(id)});
            return rows > 0;
        } catch (Throwable e) {
            Log.e(TAG, "Error deleting word by id", e);
            return false;
        }
    }

    public synchronized boolean deleteWordsByIds(final List<Long> ids) {
        if (ids == null || ids.isEmpty()) return true;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            for (final Long id : ids) {
                if (id != null) {
                    db.delete(TABLE_WORDS, COL_WORDS_ID + "=?", new String[]{String.valueOf(id)});
                }
            }
            db.setTransactionSuccessful();
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Error batch deleting words by ids", e);
            return false;
        } finally {
            if (db != null && db.inTransaction()) {
                try {
                    db.endTransaction();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
    }

    public synchronized String getWordById(final long id) {
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            cursor = db.query(TABLE_WORDS, new String[]{COL_WORDS_WORD}, COL_WORDS_ID + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error getting word by id", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
        return null;
    }

    public synchronized boolean isWordLearned(final String word) {
        final String cleanWord = sanitizeWord(word);
        if (cleanWord == null) {
            return false;
        }
        final String normWord = StringUtils.toNormalizedLower(cleanWord);
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            cursor = db.query(TABLE_WORDS, new String[]{COL_WORDS_ID}, COL_WORDS_NORM + "=?",
                    new String[]{normWord}, null, null, null);
            return cursor != null && cursor.moveToFirst();
        } catch (Throwable e) {
            Log.e(TAG, "Error checking isWordLearned", e);
            return false;
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
    }

    public synchronized List<UserDictionaryEntry> getAllWords(final String query) {
        final List<UserDictionaryEntry> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            String selection = null;
            String[] selectionArgs = null;
            if (query != null && !query.trim().isEmpty()) {
                final String sanitized = query.trim().length() > MAX_QUERY_LENGTH
                        ? query.trim().substring(0, MAX_QUERY_LENGTH) : query.trim();
                selection = COL_WORDS_WORD + " LIKE ? ESCAPE '\\' OR " + COL_WORDS_NORM + " LIKE ? ESCAPE '\\'";
                final String pattern = "%" + escapeLikePattern(sanitized) + "%";
                selectionArgs = new String[]{pattern, pattern};
            }
            cursor = db.query(TABLE_WORDS, null, selection, selectionArgs, null, null,
                    COL_WORDS_WORD + " COLLATE NOCASE ASC");
            if (cursor != null && cursor.moveToFirst()) {
                final int idIdx = cursor.getColumnIndexOrThrow(COL_WORDS_ID);
                final int wordIdx = cursor.getColumnIndexOrThrow(COL_WORDS_WORD);
                final int normIdx = cursor.getColumnIndexOrThrow(COL_WORDS_NORM);
                final int freqIdx = cursor.getColumnIndexOrThrow(COL_WORDS_FREQ);
                final int shortcutIdx = cursor.getColumnIndexOrThrow(COL_WORDS_SHORTCUT);
                final int timeIdx = cursor.getColumnIndexOrThrow(COL_WORDS_TIMESTAMP);
                do {
                    final long id = cursor.getLong(idIdx);
                    final String word = cursor.getString(wordIdx);
                    final String norm = cursor.getString(normIdx);
                    final int freq = cursor.getInt(freqIdx);
                    final String shortcut = cursor.getString(shortcutIdx);
                    final long time = cursor.getLong(timeIdx);
                    list.add(new UserDictionaryEntry(id, word, norm, freq, shortcut, time));
                } while (cursor.moveToNext());
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error querying all words", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
        return list;
    }

    public synchronized int getWordsCount() {
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_WORDS, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error counting words", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
        return 0;
    }

    public synchronized boolean clearAllWords() {
        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_WORDS, null, null);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Error clearing words table", e);
            return false;
        }
    }

    // --- Blocked Words Operations ---

    public synchronized boolean insertBlockedWord(final String word) {
        final String cleanWord = sanitizeWord(word);
        if (cleanWord == null) {
            return false;
        }
        final String normWord = StringUtils.toNormalizedLower(cleanWord);
        final long timestamp = System.currentTimeMillis();

        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getWritableDatabase();
            cursor = db.query(TABLE_BLOCKED, new String[]{COL_BLOCKED_ID}, COL_BLOCKED_NORM + "=?",
                    new String[]{normWord}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return false;
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            final ContentValues values = new ContentValues();
            values.put(COL_BLOCKED_WORD, cleanWord);
            values.put(COL_BLOCKED_NORM, normWord);
            values.put(COL_BLOCKED_TIMESTAMP, timestamp);

            final long result = db.insertWithOnConflict(TABLE_BLOCKED, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            return result != -1;
        } catch (Throwable e) {
            Log.e(TAG, "Error inserting blocked word", e);
            return false;
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
    }

    public synchronized boolean deleteBlockedWord(final String word) {
        final String cleanWord = sanitizeWord(word);
        if (cleanWord == null) {
            return false;
        }
        final String normWord = StringUtils.toNormalizedLower(cleanWord);
        try {
            final SQLiteDatabase db = getWritableDatabase();
            final int rows = db.delete(TABLE_BLOCKED, COL_BLOCKED_NORM + "=?", new String[]{normWord});
            return rows > 0;
        } catch (Throwable e) {
            Log.e(TAG, "Error deleting blocked word", e);
            return false;
        }
    }

    public synchronized boolean deleteBlockedWordById(final long id) {
        try {
            final SQLiteDatabase db = getWritableDatabase();
            final int rows = db.delete(TABLE_BLOCKED, COL_BLOCKED_ID + "=?", new String[]{String.valueOf(id)});
            return rows > 0;
        } catch (Throwable e) {
            Log.e(TAG, "Error deleting blocked word by id", e);
            return false;
        }
    }

    public synchronized boolean deleteBlockedWordsByIds(final List<Long> ids) {
        if (ids == null || ids.isEmpty()) return true;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            for (final Long id : ids) {
                if (id != null) {
                    db.delete(TABLE_BLOCKED, COL_BLOCKED_ID + "=?", new String[]{String.valueOf(id)});
                }
            }
            db.setTransactionSuccessful();
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Error batch deleting blocked words by ids", e);
            return false;
        } finally {
            if (db != null && db.inTransaction()) {
                try {
                    db.endTransaction();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
    }

    public synchronized String getBlockedWordById(final long id) {
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            cursor = db.query(TABLE_BLOCKED, new String[]{COL_BLOCKED_WORD}, COL_BLOCKED_ID + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error getting blocked word by id", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
        return null;
    }

    public synchronized boolean isWordBlocked(final String word) {
        final String cleanWord = sanitizeWord(word);
        if (cleanWord == null) {
            return false;
        }
        final String normWord = StringUtils.toNormalizedLower(cleanWord);
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            cursor = db.query(TABLE_BLOCKED, new String[]{COL_BLOCKED_ID}, COL_BLOCKED_NORM + "=?",
                    new String[]{normWord}, null, null, null);
            return cursor != null && cursor.moveToFirst();
        } catch (Throwable e) {
            Log.e(TAG, "Error checking isWordBlocked", e);
            return false;
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
    }

    public synchronized List<UserDictionaryEntry> getAllBlockedWords(final String query) {
        final List<UserDictionaryEntry> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            String selection = null;
            String[] selectionArgs = null;
            if (query != null && !query.trim().isEmpty()) {
                final String sanitized = query.trim().length() > MAX_QUERY_LENGTH
                        ? query.trim().substring(0, MAX_QUERY_LENGTH) : query.trim();
                selection = COL_BLOCKED_WORD + " LIKE ? ESCAPE '\\' OR " + COL_BLOCKED_NORM + " LIKE ? ESCAPE '\\'";
                final String pattern = "%" + escapeLikePattern(sanitized) + "%";
                selectionArgs = new String[]{pattern, pattern};
            }
            cursor = db.query(TABLE_BLOCKED, null, selection, selectionArgs, null, null,
                    COL_BLOCKED_WORD + " COLLATE NOCASE ASC");
            if (cursor != null && cursor.moveToFirst()) {
                final int idIdx = cursor.getColumnIndexOrThrow(COL_BLOCKED_ID);
                final int wordIdx = cursor.getColumnIndexOrThrow(COL_BLOCKED_WORD);
                final int normIdx = cursor.getColumnIndexOrThrow(COL_BLOCKED_NORM);
                final int timeIdx = cursor.getColumnIndexOrThrow(COL_BLOCKED_TIMESTAMP);
                do {
                    final long id = cursor.getLong(idIdx);
                    final String word = cursor.getString(wordIdx);
                    final String norm = cursor.getString(normIdx);
                    final long time = cursor.getLong(timeIdx);
                    list.add(new UserDictionaryEntry(id, word, norm, 0, null, time));
                } while (cursor.moveToNext());
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error querying blocked words", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
        return list;
    }

    public synchronized int getBlockedWordsCount() {
        Cursor cursor = null;
        try {
            final SQLiteDatabase db = getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_BLOCKED, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error counting blocked words", e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable e) { Log.w(TAG, "Cleanup failed", e); }
            }
        }
        return 0;
    }

    public synchronized boolean clearAllBlockedWords() {
        try {
            final SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_BLOCKED, null, null);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Error clearing blocked words table", e);
            return false;
        }
    }
}
