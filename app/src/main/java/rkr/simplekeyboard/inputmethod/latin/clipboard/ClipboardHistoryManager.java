package rkr.simplekeyboard.inputmethod.latin.clipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClipboardHistoryManager implements ClipboardManager.OnPrimaryClipChangedListener {
    private static final String TAG = "ClipboardHistoryManager";
    public static final long CLIPBOARD_SUGGESTION_TIMEOUT_MS = 15 * 60 * 1000L; // 15 minutes

    private final Context mContext;
    private final ClipboardManager mClipboardManager;
    private final ClipboardDatabase mDatabase;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private volatile String mLastText = null;
    private volatile long mLastTextTime = 0L;
    private volatile boolean mLastTextUsed = false;
    private boolean mIsListening = false;

    public ClipboardHistoryManager(Context context) {
        mContext = context;
        mDatabase = new ClipboardDatabase(context);
        mClipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public void start() {
        if (mClipboardManager != null && !mIsListening) {
            try {
                mClipboardManager.addPrimaryClipChangedListener(this);
                mIsListening = true;
                updateCurrentClip();
            } catch (Exception e) {
                Log.w(TAG, "Failed to start ClipboardHistoryManager listener", e);
            }
        }
    }

    public void stop() {
        if (mClipboardManager != null && mIsListening) {
            try {
                mClipboardManager.removePrimaryClipChangedListener(this);
            } catch (Exception e) {
                Log.w(TAG, "Failed to stop ClipboardHistoryManager listener", e);
            }
            mIsListening = false;
        }
    }

    public void close() {
        stop();
        mExecutor.shutdown();
        mDatabase.close();
    }

    public ClipboardDatabase getDatabase() {
        return mDatabase;
    }

    public void updateCurrentClip() {
        onPrimaryClipChanged();
    }

    public String getLatestClipText() {
        if (mClipboardManager != null) {
            try {
                if (mClipboardManager.hasPrimaryClip()) {
                    final String currentText = processPrimaryClip(mClipboardManager.getPrimaryClip());
                    if (currentText != null) {
                        return currentText;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error getting latest clip text", e);
            }
        }
        return mLastText;
    }

    private String processPrimaryClip(ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        ClipData.Item item = clip.getItemAt(0);
        CharSequence text = item != null ? item.coerceToText(mContext) : null;
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        final String currentText = text.toString();
        long clipTimestamp = System.currentTimeMillis();
        try {
            if (clip.getDescription() != null && clip.getDescription().getTimestamp() > 0) {
                clipTimestamp = clip.getDescription().getTimestamp();
            }
        } catch (Throwable ignored) {}

        storeClipTextIfChanged(currentText, clipTimestamp);
        return currentText;
    }

    private void storeClipTextIfChanged(final String currentText, final long clipTimestamp) {
        if (!currentText.equals(mLastText)) {
            mLastText = currentText;
            mLastTextTime = clipTimestamp;
            mLastTextUsed = false;
            final long retentionMinutes = getRetentionMinutes();
            mExecutor.execute(() -> {
                mDatabase.deleteExpiredClips(retentionMinutes);
                if (retentionMinutes <= 0 || (System.currentTimeMillis() - clipTimestamp <= retentionMinutes * 60 * 1000L)) {
                    mDatabase.insertClip(currentText, false, clipTimestamp);
                }
            });
        } else if (mLastTextTime <= 0) {
            mLastTextTime = clipTimestamp;
        }
    }

    public String getRecentClipForSuggestion() {
        final String clip = getLatestClipText();
        if (clip == null || clip.trim().isEmpty()) {
            return null;
        }
        if (mLastTextUsed) {
            return null;
        }
        final long now = System.currentTimeMillis();
        long maxTimeout = CLIPBOARD_SUGGESTION_TIMEOUT_MS;
        final long retentionMinutes = getRetentionMinutes();
        if (retentionMinutes > 0) {
            maxTimeout = Math.min(maxTimeout, retentionMinutes * 60 * 1000L);
        }
        if (mLastTextTime <= 0 || (now - mLastTextTime > maxTimeout)) {
            return null;
        }
        return clip;
    }

    public void markLatestClipUsed() {
        mLastTextUsed = true;
    }

    public long getLastClipTime() {
        return mLastTextTime;
    }

    public boolean isLastClipUsed() {
        return mLastTextUsed;
    }

    public void setLatestClip(String text, long timestamp, boolean isUsed) {
        mLastText = text;
        mLastTextTime = timestamp;
        mLastTextUsed = isUsed;
    }

    private long getRetentionMinutes() {
        android.content.SharedPreferences prefs = rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat.getDeviceSharedPreferences(mContext);
        String val = prefs.getString(rkr.simplekeyboard.inputmethod.latin.settings.Settings.PREF_CLIPBOARD_RETENTION_TIME, "1440");
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 1440L;
        }
    }

    @Override
    public void onPrimaryClipChanged() {
        if (mClipboardManager == null) return;

        try {
            if (mClipboardManager.hasPrimaryClip()) {
                processPrimaryClip(mClipboardManager.getPrimaryClip());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error handling clipboard change", e);
        }
    }
}
