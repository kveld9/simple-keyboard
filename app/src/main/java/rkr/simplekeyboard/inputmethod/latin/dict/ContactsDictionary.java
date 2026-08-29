package rkr.simplekeyboard.inputmethod.latin.dict;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class ContactsDictionary {
    private static final String TAG = "ContactsDictionary";
    private static final int MAX_CONTACT_NAMES = 200;
    private static final int MAX_QUERY_LIMIT = 10000;
    private static final int FREQ_CONTACT = 220;
    private static final int FREQ_CONTACT_BIGRAM = 240;

    private final PrefixDictionary mDict = new PrefixDictionary();
    private final Context mContext;

    public ContactsDictionary(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public synchronized void loadAsync(Executor executor, Runnable onLoaded) {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        executor.execute(() -> {
            loadInternal();
            if (onLoaded != null) onLoaded.run();
        });
    }

    private static class RankedContact {
        final String name;
        final int timesContacted;
        final long lastTimeContacted;
        final boolean inVisibleGroup;
        float affinity = 0.0f;

        RankedContact(String name, int timesContacted, long lastTimeContacted, boolean inVisibleGroup) {
            this.name = name;
            this.timesContacted = timesContacted;
            this.lastTimeContacted = lastTimeContacted;
            this.inVisibleGroup = inVisibleGroup;
        }

        void computeAffinity(int maxTimes, long now) {
            float timesWeight = (float) (timesContacted + 1) / (float) (maxTimes + 1);
            long daysSince = Math.max(0, (now - lastTimeContacted) / (1000L * 60 * 60 * 24));
            float lastTimeWeight = (float) Math.pow(0.5, Math.min(180, daysSince) / 10.0);
            float visibleWeight = inVisibleGroup ? 1.0f : 0.0f;
            this.affinity = (timesWeight + lastTimeWeight + visibleWeight) / 3.0f;
        }
    }

    private static boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (name.contains("@")) return false;
        return true;
    }

    private synchronized void loadInternal() {
        try {
            ContentResolver cr = mContext.getContentResolver();
            try (Cursor countCursor = cr.query(ContactsContract.Contacts.CONTENT_URI, new String[]{BaseColumns._ID}, null, null, null)) {
                if (countCursor == null || countCursor.getCount() == 0 || countCursor.getCount() > MAX_QUERY_LIMIT) {
                    return;
                }
            }

            String[] projection = new String[]{
                    BaseColumns._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.TIMES_CONTACTED,
                    ContactsContract.Contacts.LAST_TIME_CONTACTED,
                    ContactsContract.Contacts.IN_VISIBLE_GROUP
            };
            List<RankedContact> contacts = new ArrayList<>();
            int maxTimes = 0;

            try (Cursor c = cr.query(ContactsContract.Contacts.CONTENT_URI, projection, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                    int timesIdx = c.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED);
                    int lastIdx = c.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED);
                    int visibleIdx = c.getColumnIndex(ContactsContract.Contacts.IN_VISIBLE_GROUP);

                    while (!c.isAfterLast()) {
                        String name = nameIdx >= 0 ? c.getString(nameIdx) : null;
                        if (isValidName(name)) {
                            int times = timesIdx >= 0 ? c.getInt(timesIdx) : 0;
                            long last = lastIdx >= 0 ? c.getLong(lastIdx) : 0;
                            boolean visible = visibleIdx >= 0 && c.getInt(visibleIdx) == 1;
                            maxTimes = Math.max(maxTimes, times);
                            contacts.add(new RankedContact(name, times, last, visible));
                        }
                        c.moveToNext();
                    }
                }
            }

            long now = System.currentTimeMillis();
            for (RankedContact rc : contacts) {
                rc.computeAffinity(maxTimes, now);
            }
            Collections.sort(contacts, (a, b) -> Float.compare(b.affinity, a.affinity));

            PrefixDictionary newDict = new PrefixDictionary();
            int limit = Math.min(contacts.size(), MAX_CONTACT_NAMES);
            for (int i = 0; i < limit; i++) {
                tokenizeAndInsert(newDict, contacts.get(i).name);
            }
            mDict.copyFrom(newDict);
        } catch (SecurityException | SQLiteException e) {
            Log.w(TAG, "Failed reading contacts", e);
        }
    }

    private void tokenizeAndInsert(PrefixDictionary dict, String name) {
        String[] tokens = name.trim().split("[\\s,]+");
        String prev = null;
        for (String token : tokens) {
            if (token.length() > 1 && Character.isLetter(token.charAt(0))) {
                dict.insert(token, FREQ_CONTACT);
                if (prev != null) {
                    dict.setBigram(prev, token, FREQ_CONTACT_BIGRAM);
                }
                prev = token;
            }
        }
    }

    public synchronized List<CharSequence> getSuggestions(String prefix, int maxCount, String w1, String w2) {
        return mDict.getSuggestions(prefix, maxCount, w1, w2);
    }

    public synchronized List<CharSequence> getNextWordPredictions(String w1, String w2, int limit) {
        return mDict.getNextWordPredictions(w1, w2, limit);
    }

    public synchronized void clear() {
        mDict.clear();
    }
}
