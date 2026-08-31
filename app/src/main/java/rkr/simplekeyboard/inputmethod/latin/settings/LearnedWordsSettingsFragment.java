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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.dict.user.UserDictionaryEntry;
import rkr.simplekeyboard.inputmethod.latin.dict.user.UserDictionaryManager;

public final class LearnedWordsSettingsFragment extends BaseUserDictionaryWordsFragment {

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_learned_words;
    }

    @Override
    protected int getFabTextResId() {
        return R.string.add_word;
    }

    @Override
    protected int getFabDialogTitleResId() {
        return R.string.add_word;
    }

    @Override
    protected int getFabDialogHintResId() {
        return R.string.add_word_hint;
    }

    @Override
    protected int getDeleteSingleDialogTitleResId() {
        return R.string.delete_word;
    }

    @Override
    protected int getDeleteSingleDialogMessageResId() {
        return R.string.delete_word_confirm;
    }

    @Override
    protected int getDeleteSingleButtonResId() {
        return R.string.delete_word;
    }

    @Override
    protected int getDeleteBatchDialogTitleResId() {
        return R.string.delete_selected;
    }

    @Override
    protected int getDeleteBatchDialogMessageResId() {
        return R.string.delete_selected_confirm;
    }

    @Override
    protected int getDeleteBatchButtonResId() {
        return R.string.delete_selected;
    }

    @Override
    protected int getSingleDeletedToastResId() {
        return R.string.word_deleted;
    }

    @Override
    protected int getBatchDeletedToastResId() {
        return R.string.words_deleted;
    }

    @Override
    protected List<UserDictionaryEntry> queryEntries(@NonNull final Context context, @Nullable final String query) {
        return UserDictionaryManager.getInstance(context).getLearnedWords(query);
    }

    @Override
    protected boolean onAddWord(@NonNull final Context context, @NonNull final String word) {
        return UserDictionaryManager.getInstance(context).addWord(word);
    }

    @Override
    protected void onRemoveSingleEntry(@NonNull final Context context, @NonNull final UserDictionaryEntry entry) {
        UserDictionaryManager.getInstance(context).removeWordById(entry.id);
    }

    @Override
    protected void onRemoveBatchEntries(@NonNull final Context context, @NonNull final List<UserDictionaryEntry> entries) {
        UserDictionaryManager.getInstance(context).removeWords(entries);
    }
}
