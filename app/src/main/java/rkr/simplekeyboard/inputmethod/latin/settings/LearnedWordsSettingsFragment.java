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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

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
        mAdapter = new WordsAdapter(this::onDeleteWordClicked);
        mRecyclerView.setAdapter(mAdapter);

        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCurrentQuery = s != null ? s.toString().trim() : "";
                mClearSearchButton.setVisibility(mCurrentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                loadWords();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mClearSearchButton.setOnClickListener(v -> mSearchEditText.setText(""));

        mAddFab.setOnClickListener(v -> showAddWordDialog());

        loadWords();
    }

    @Override
    public void onResume() {
        super.onResume();
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

    private void onDeleteWordClicked(final UserDictionaryEntry entry) {
        final Context context = getContext();
        if (context == null) return;
        UserDictionaryManager.getInstance(context).removeWordById(entry.id);
        loadWords();
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
        final MenuItem clearItem = menu.add(Menu.NONE, R.id.action_clear_all, Menu.NONE, R.string.clear_all);
        clearItem.setIcon(R.drawable.ic_delete);
        clearItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_clear_all) {
            showClearAllDialog();
            return true;
        }
        return false;
    }

    private static class WordsAdapter extends RecyclerView.Adapter<WordsAdapter.WordViewHolder> {
        interface OnWordActionListener {
            void onAction(UserDictionaryEntry entry);
        }

        private final List<UserDictionaryEntry> mWords = new ArrayList<>();
        private final OnWordActionListener mListener;

        WordsAdapter(final OnWordActionListener listener) {
            mListener = listener;
        }

        void setWords(final List<UserDictionaryEntry> words) {
            mWords.clear();
            if (words != null) {
                mWords.addAll(words);
            }
            notifyDataSetChanged();
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
            holder.actionButton.setOnClickListener(v -> mListener.onAction(entry));
        }

        @Override
        public int getItemCount() {
            return mWords.size();
        }

        static class WordViewHolder extends RecyclerView.ViewHolder {
            final TextView wordText;
            final MaterialButton actionButton;

            WordViewHolder(@NonNull final View itemView) {
                super(itemView);
                wordText = itemView.findViewById(R.id.word_text);
                actionButton = itemView.findViewById(R.id.action_button);
            }
        }
    }
}
