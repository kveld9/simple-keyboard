package rkr.simplekeyboard.inputmethod.latin.clipboard;

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
}
