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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.dict.user.UserDictionaryEntry;
import rkr.simplekeyboard.inputmethod.latin.dict.user.UserDictionaryManager;

public final class LearnedWordsSettingsFragment extends Fragment implements MenuProvider {

    private EditText mSearchEditText;
    private ImageButton mClearSearchButton;
    private RecyclerView mRecyclerView;
    private TextView mEmptyView;
    private ExtendedFloatingActionButton mAddFab;
    private WordsAdapter mAdapter;
    private String mCurrentQuery = "";
    private OnBackPressedCallback mBackCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_dictionary_words, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        mSearchEditText = view.findViewById(R.id.search_edit_text);
        mClearSearchButton = view.findViewById(R.id.clear_search_button);
        mRecyclerView = view.findViewById(R.id.words_recycler_view);
        mEmptyView = view.findViewById(R.id.empty_view);
        mAddFab = view.findViewById(R.id.add_word_fab);

        mEmptyView.setText(R.string.no_learned_words);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new WordsAdapter(
                this::onWordSingleClicked,
                this::onWordLongClicked,
                this::onSelectionChanged
        );
        mRecyclerView.setAdapter(mAdapter);

        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null && mAdapter.isSelectionMode()) {
                    exitSelectionMode();
                }
                mCurrentQuery = s != null ? s.toString().trim() : "";
                mClearSearchButton.setVisibility(mCurrentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                loadWords();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mClearSearchButton.setOnClickListener(v -> mSearchEditText.setText(""));

        mAddFab.setOnClickListener(v -> showAddWordDialog());

        mBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (mAdapter.isSelectionMode()) {
                    exitSelectionMode();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), mBackCallback);

        loadWords();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mAdapter.isSelectionMode()) {
            requireActivity().setTitle(R.string.learned_words);
        }
        loadWords();
    }

    private void loadWords() {
        final Context context = getContext();
        if (context == null) return;
        final UserDictionaryManager manager = UserDictionaryManager.getInstance(context);
        final List<UserDictionaryEntry> words = manager.getLearnedWords(mCurrentQuery);
        mAdapter.setWords(words);

        if (words.isEmpty()) {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void onWordSingleClicked(final UserDictionaryEntry entry) {
        final Context context = getContext();
        if (context == null) return;

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_word)
                .setMessage(getString(R.string.delete_word_confirm, entry.word))
                .setPositiveButton(R.string.delete_word, (dialog, which) -> {
                    UserDictionaryManager.getInstance(context).removeWordById(entry.id);
                    Toast.makeText(context, R.string.word_deleted, Toast.LENGTH_SHORT).show();
                    loadWords();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onWordLongClicked(final UserDictionaryEntry entry) {
        mAdapter.setSelectionMode(true);
        mAdapter.toggleSelection(entry.id);
        mAddFab.setVisibility(View.GONE);
        mBackCallback.setEnabled(true);
        updateSelectionUI();
    }

    private void onSelectionChanged() {
        if (mAdapter.isSelectionMode()) {
            if (mAdapter.getSelectedCount() == 0) {
                exitSelectionMode();
            } else {
                updateSelectionUI();
            }
        }
    }

    private void updateSelectionUI() {
        if (isAdded()) {
            requireActivity().setTitle(getString(R.string.selected_count, mAdapter.getSelectedCount()));
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void exitSelectionMode() {
        mAdapter.clearSelection();
        mAdapter.setSelectionMode(false);
        mAddFab.setVisibility(View.VISIBLE);
        mBackCallback.setEnabled(false);
        if (isAdded()) {
            requireActivity().setTitle(R.string.learned_words);
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void showAddWordDialog() {
        final Context context = requireContext();
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.add_word_hint);
        input.setSingleLine(true);

        final FrameLayout container = new FrameLayout(context);
        final int margin = (int) (20 * getResources().getDisplayMetrics().density);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        params.rightMargin = margin;
        params.topMargin = margin / 2;
        params.bottomMargin = margin / 2;
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.add_word)
                .setView(container)
                .setPositiveButton(R.string.add_word, (dialog, which) -> {
                    final String word = input.getText().toString().trim();
                    if (!word.isEmpty()) {
                        final boolean added = UserDictionaryManager.getInstance(context).addWord(word);
                        if (added) {
                            Toast.makeText(context, R.string.word_added, Toast.LENGTH_SHORT).show();
                            loadWords();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteSelectedDialog() {
        final Context context = requireContext();
        final int count = mAdapter.getSelectedCount();
        if (count == 0) return;

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_selected)
                .setMessage(getString(R.string.delete_selected_confirm, count))
                .setPositiveButton(R.string.delete_word, (dialog, which) -> {
                    final List<UserDictionaryEntry> selected = mAdapter.getSelectedEntries();
                    final UserDictionaryManager manager = UserDictionaryManager.getInstance(context);
                    manager.removeWords(selected);
                    Toast.makeText(context, getString(R.string.words_deleted, selected.size()), Toast.LENGTH_SHORT).show();
                    exitSelectionMode();
                    loadWords();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showClearAllDialog() {
        final Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.clear_learned_words_title)
                .setMessage(R.string.clear_learned_words_message)
                .setPositiveButton(R.string.clear_all, (dialog, which) -> {
                    UserDictionaryManager.getInstance(context).clearLearnedWords();
                    loadWords();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onCreateMenu(@NonNull final Menu menu, @NonNull final MenuInflater menuInflater) {
        if (mAdapter != null && mAdapter.isSelectionMode()) {
            final boolean allSelected = mAdapter.isAllSelected();
            final MenuItem selectAllItem = menu.add(Menu.NONE, R.id.action_select_all, Menu.NONE,
                    allSelected ? R.string.deselect_all : R.string.select_all);
            selectAllItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            final MenuItem deleteSelectedItem = menu.add(Menu.NONE, R.id.action_delete_selected, Menu.NONE, R.string.delete_selected);
            deleteSelectedItem.setIcon(R.drawable.ic_delete);
            deleteSelectedItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            final MenuItem clearItem = menu.add(Menu.NONE, R.id.action_clear_all, Menu.NONE, R.string.clear_all);
            clearItem.setIcon(R.drawable.ic_delete);
            clearItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
        final int id = menuItem.getItemId();
        if (id == R.id.action_clear_all) {
            showClearAllDialog();
            return true;
        } else if (id == R.id.action_select_all) {
            mAdapter.toggleSelectAll();
            return true;
        } else if (id == R.id.action_delete_selected) {
            showDeleteSelectedDialog();
            return true;
        } else if (id == android.R.id.home && mAdapter != null && mAdapter.isSelectionMode()) {
            exitSelectionMode();
            return true;
        }
        return false;
    }

    private static class WordsAdapter extends RecyclerView.Adapter<WordsAdapter.WordViewHolder> {
        interface OnWordClickListener {
            void onWordClick(UserDictionaryEntry entry);
        }

        interface OnWordLongClickListener {
            void onWordLongClick(UserDictionaryEntry entry);
        }

        interface OnSelectionChangedListener {
            void onSelectionChanged();
        }

        private final List<UserDictionaryEntry> mWords = new ArrayList<>();
        private final Set<Long> mSelectedIds = new HashSet<>();
        private boolean mSelectionMode = false;

        private final OnWordClickListener mClickListener;
        private final OnWordLongClickListener mLongClickListener;
        private final OnSelectionChangedListener mSelectionChangedListener;

        WordsAdapter(final OnWordClickListener clickListener,
                     final OnWordLongClickListener longClickListener,
                     final OnSelectionChangedListener selectionChangedListener) {
            mClickListener = clickListener;
            mLongClickListener = longClickListener;
            mSelectionChangedListener = selectionChangedListener;
        }

        void setWords(final List<UserDictionaryEntry> words) {
            mWords.clear();
            if (words != null) {
                mWords.addAll(words);
            }
            notifyDataSetChanged();
        }

        boolean isSelectionMode() {
            return mSelectionMode;
        }

        void setSelectionMode(final boolean enabled) {
            mSelectionMode = enabled;
            if (!enabled) {
                mSelectedIds.clear();
            }
            notifyDataSetChanged();
        }

        void toggleSelection(final long id) {
            if (mSelectedIds.contains(id)) {
                mSelectedIds.remove(id);
            } else {
                mSelectedIds.add(id);
            }
            notifyDataSetChanged();
            if (mSelectionChangedListener != null) {
                mSelectionChangedListener.onSelectionChanged();
            }
        }

        void clearSelection() {
            mSelectedIds.clear();
            notifyDataSetChanged();
        }

        boolean isAllSelected() {
            return !mWords.isEmpty() && mSelectedIds.size() >= mWords.size();
        }

        void toggleSelectAll() {
            if (isAllSelected()) {
                mSelectedIds.clear();
            } else {
                for (final UserDictionaryEntry entry : mWords) {
                    mSelectedIds.add(entry.id);
                }
            }
            notifyDataSetChanged();
            if (mSelectionChangedListener != null) {
                mSelectionChangedListener.onSelectionChanged();
            }
        }

        int getSelectedCount() {
            return mSelectedIds.size();
        }

        List<UserDictionaryEntry> getSelectedEntries() {
            final List<UserDictionaryEntry> list = new ArrayList<>();
            for (final UserDictionaryEntry entry : mWords) {
                if (mSelectedIds.contains(entry.id)) {
                    list.add(entry);
                }
            }
            return list;
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_dictionary_word, parent, false);
            return new WordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final WordViewHolder holder, final int position) {
            final UserDictionaryEntry entry = mWords.get(position);
            holder.wordText.setText(entry.word);

            final boolean isSelected = mSelectedIds.contains(entry.id);

            if (mSelectionMode) {
                holder.selectionCheckbox.setVisibility(View.VISIBLE);
                holder.selectionCheckbox.setChecked(isSelected);
                holder.actionButton.setVisibility(View.GONE);
                holder.itemView.setActivated(isSelected);
            } else {
                holder.selectionCheckbox.setVisibility(View.GONE);
                holder.actionButton.setVisibility(View.VISIBLE);
                holder.itemView.setActivated(false);
            }

            holder.itemView.setOnClickListener(v -> {
                if (mSelectionMode) {
                    toggleSelection(entry.id);
                } else {
                    mClickListener.onWordClick(entry);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!mSelectionMode) {
                    mLongClickListener.onWordLongClick(entry);
                    return true;
                }
                return false;
            });

            holder.actionButton.setOnClickListener(v -> mClickListener.onWordClick(entry));
        }

        @Override
        public int getItemCount() {
            return mWords.size();
        }

        static class WordViewHolder extends RecyclerView.ViewHolder {
            final MaterialCheckBox selectionCheckbox;
            final TextView wordText;
            final MaterialButton actionButton;

            WordViewHolder(@NonNull final View itemView) {
                super(itemView);
                selectionCheckbox = itemView.findViewById(R.id.selection_checkbox);
                wordText = itemView.findViewById(R.id.word_text);
                actionButton = itemView.findViewById(R.id.action_button);
            }
        }
    }
}
