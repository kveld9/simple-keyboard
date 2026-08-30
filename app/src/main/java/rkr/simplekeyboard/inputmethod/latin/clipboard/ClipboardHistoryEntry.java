package rkr.simplekeyboard.inputmethod.latin.clipboard;

import android.database.Cursor;

public class ClipboardHistoryEntry {
    public final long id;
    public final String text;
    public final long timestamp;
    public final boolean isPinned;
    public final String uri;

    public ClipboardHistoryEntry(long id, String text, long timestamp, boolean isPinned) {
        this(id, text, timestamp, isPinned, null);
    }

    public ClipboardHistoryEntry(long id, String text, long timestamp, boolean isPinned, String uri) {
        this.id = id;
        this.text = text;
        this.timestamp = timestamp;
        this.isPinned = isPinned;
        this.uri = uri;
    }

    public static ClipboardHistoryEntry fromCursor(final Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        int idCol = cursor.getColumnIndexOrThrow("id");
        int textCol = cursor.getColumnIndexOrThrow("text");
        int timeCol = cursor.getColumnIndexOrThrow("timestamp");
        int pinCol = cursor.getColumnIndexOrThrow("is_pinned");
        int uriCol = cursor.getColumnIndex("uri");

        long id = cursor.getLong(idCol);
        String text = cursor.getString(textCol);
        long timestamp = cursor.getLong(timeCol);
        boolean isPinned = cursor.getInt(pinCol) == 1;
        String uri = (uriCol != -1 && !cursor.isNull(uriCol)) ? cursor.getString(uriCol) : null;
        return new ClipboardHistoryEntry(id, text, timestamp, isPinned, uri);
    }
}
