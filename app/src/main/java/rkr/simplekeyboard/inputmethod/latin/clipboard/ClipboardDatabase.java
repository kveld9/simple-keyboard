package rkr.simplekeyboard.inputmethod.latin.clipboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ClipboardDatabase extends SQLiteOpenHelper {
    private static final String TAG = "ClipboardDatabase";
    private static final String DATABASE_NAME = "clipboard_history.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "clips";
    private static final String COL_ID = "id";
    private static final String COL_TEXT = "text";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_PINNED = "is_pinned";
    private static final int MAX_CLIPS = 50;
    private static final int MAX_TEXT_LENGTH = 50000;

    public ClipboardDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TEXT + " TEXT UNIQUE, " +
                COL_TIMESTAMP + " INTEGER, " +
                COL_PINNED + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pinned_time ON " + TABLE_NAME + " (" + COL_PINNED + ", " + COL_TIMESTAMP + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public synchronized void insertClip(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Check if exists and whether it was pinned
            boolean isPinned = false;
            Cursor cursor = db.query(TABLE_NAME, new String[]{COL_PINNED}, COL_TEXT + "=?",
                    new String[]{text}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    isPinned = cursor.getInt(0) == 1;
                }
                cursor.close();
            }

            // Remove existing row to refresh timestamp and position
            db.delete(TABLE_NAME, COL_TEXT + "=?", new String[]{text});

            ContentValues values = new ContentValues();
            values.put(COL_TEXT, text);
            values.put(COL_TIMESTAMP, System.currentTimeMillis());
            values.put(COL_PINNED, isPinned ? 1 : 0);
            db.insert(TABLE_NAME, null, values);

            cleanupOldClips(db);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error inserting clip", e);
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void deleteExpiredClips(long retentionMinutes) {
        if (retentionMinutes <= 0) {
            return; // Never / Unlimited
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long cutoffTimestamp = System.currentTimeMillis() - (retentionMinutes * 60 * 1000L);
            db.delete(TABLE_NAME, COL_PINNED + "=0 AND " + COL_TIMESTAMP + " < ?",
                    new String[]{String.valueOf(cutoffTimestamp)});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error deleting expired clips", e);
        } finally {
            db.endTransaction();
        }
    }

    private void cleanupOldClips(SQLiteDatabase db) {
        try {
            db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COL_PINNED + "=0 AND "
                    + COL_ID + " NOT IN (SELECT " + COL_ID + " FROM " + TABLE_NAME
                    + " WHERE " + COL_PINNED + "=0 ORDER BY " + COL_TIMESTAMP + " DESC LIMIT " + MAX_CLIPS + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up old clips", e);
        }
    }

    public synchronized void deleteClip(long id) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_NAME, COL_ID + "=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            Log.e(TAG, "Error deleting clip", e);
        }
    }

    public synchronized void setPinned(long id, boolean isPinned) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_PINNED, isPinned ? 1 : 0);
            db.update(TABLE_NAME, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        } catch (Exception e) {
            Log.e(TAG, "Error setting clip pinned state", e);
        }
    }

    public synchronized void clearUnpinned() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_NAME, COL_PINNED + "=0", null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing unpinned clips", e);
        }
    }

    public synchronized List<ClipboardHistoryEntry> getClips() {
        List<ClipboardHistoryEntry> clips = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            cursor = db.query(TABLE_NAME, null, null, null, null, null,
                    COL_PINNED + " DESC, " + COL_TIMESTAMP + " DESC");

            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndexOrThrow(COL_ID);
                int textCol = cursor.getColumnIndexOrThrow(COL_TEXT);
                int timeCol = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
                int pinCol = cursor.getColumnIndexOrThrow(COL_PINNED);
                do {
                    long id = cursor.getLong(idCol);
                    String text = cursor.getString(textCol);
                    long timestamp = cursor.getLong(timeCol);
                    boolean isPinned = cursor.getInt(pinCol) == 1;
                    clips.add(new ClipboardHistoryEntry(id, text, timestamp, isPinned));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting clips", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return clips;
    }
}
