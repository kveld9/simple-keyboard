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

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.dict.user.UserDictionaryEntry;
import rkr.simplekeyboard.inputmethod.latin.dict.user.UserDictionaryManager;

public final class DictionarySettingsFragment extends SubScreenFragment {
    private Preference mLearnedWordsPref;
    private Preference mBlockedWordsPref;
    private Preference mClearLearnedPref;

    private final UserDictionaryManager.UserDictionaryListener mListener =
            new UserDictionaryManager.UserDictionaryListener() {
        @Override
        public void onWordAdded(final UserDictionaryEntry entry) {
            updateWordCounts();
        }

        @Override
        public void onWordRemoved(final String word, final long id) {
            updateWordCounts();
        }

        @Override
        public void onAllLearnedWordsCleared() {
            updateWordCounts();
        }

        @Override
        public void onWordBlocked(final String word) {
            updateWordCounts();
        }

        @Override
        public void onWordUnblocked(final String word, final long id) {
            updateWordCounts();
        }

        @Override
        public void onAllBlockedWordsCleared() {
            updateWordCounts();
        }
    };

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.prefs_screen_dictionary, rootKey);

        mLearnedWordsPref = findPreference("screen_learned_words");
        mBlockedWordsPref = findPreference("screen_blocked_words");
        mClearLearnedPref = findPreference("pref_clear_all_learned_words");

        if (mClearLearnedPref != null) {
            mClearLearnedPref.setOnPreferenceClickListener(preference -> {
                showClearLearnedWordsDialog();
                return true;
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        UserDictionaryManager.getInstance(requireContext()).addListener(mListener);
        updateWordCounts();
    }

    @Override
    public void onStop() {
        UserDictionaryManager.getInstance(requireContext()).removeListener(mListener);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWordCounts();
    }

    private void showClearLearnedWordsDialog() {
        final Context context = getContext();
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.clear_learned_words_title)
                .setMessage(R.string.clear_learned_words_message)
                .setPositiveButton(R.string.clear_all, (dialog, which) -> {
                    UserDictionaryManager.getInstance(context).clearLearnedWords();
                    Toast.makeText(context, R.string.learned_words_cleared, Toast.LENGTH_SHORT).show();
                    updateWordCounts();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateWordCounts() {
        final Context context = getContext();
        if (context == null) return;
        final UserDictionaryManager manager = UserDictionaryManager.getInstance(context);
        final int learnedCount = manager.getLearnedWordsCount();
        final int blockedCount = manager.getBlockedWordsCount();

        if (mLearnedWordsPref != null) {
            if (learnedCount == 0) {
                mLearnedWordsPref.setSummary(R.string.learned_words_summary_zero);
            } else {
                mLearnedWordsPref.setSummary(getString(R.string.learned_words_summary_count, learnedCount));
            }
        }

        if (mBlockedWordsPref != null) {
            if (blockedCount == 0) {
                mBlockedWordsPref.setSummary(R.string.blocked_words_summary_zero);
            } else {
                mBlockedWordsPref.setSummary(getString(R.string.blocked_words_summary_count, blockedCount));
            }
        }

        if (mClearLearnedPref != null) {
            mClearLearnedPref.setEnabled(learnedCount > 0);
        }
    }
}
