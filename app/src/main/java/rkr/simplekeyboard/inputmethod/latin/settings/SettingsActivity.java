/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
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
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.utils.FragmentUtils;

public class SettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    private static final String DEFAULT_FRAGMENT = SettingsFragment.class.getName();
    public static final String EXTRA_SHOW_FRAGMENT = ":android:show_fragment";
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":android:show_fragment_args";
    public static final String EXTRA_NO_HEADERS = ":android:no_headers";
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private MaterialToolbar mToolbar;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {

        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_settings);

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        View rootView = findViewById(R.id.settings_coordinator);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        mToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        getSupportFragmentManager().addOnBackStackChangedListener(this::updateToolbar);

        if (savedInstanceState == null) {
            String initialFragment = getIntent().getStringExtra(EXTRA_SHOW_FRAGMENT);
            if (initialFragment == null || !FragmentUtils.isValidFragment(initialFragment)) {
                initialFragment = DEFAULT_FRAGMENT;
            }
            Bundle initialArgs = getIntent().getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
            Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                    getClassLoader(), initialFragment);
            if (initialArgs != null) {
                fragment.setArguments(initialArgs);
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, fragment)
                    .commit();
        }

        updateToolbar();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            final Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.settings_container);
            if (fragment instanceof SettingsFragment) {
                ((SettingsFragment) fragment).updateImeBanner();
            }
        }
    }


    private void updateToolbar() {
        int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
        if (backStackCount > 0) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
            FragmentManager.BackStackEntry entry =
                    getSupportFragmentManager().getBackStackEntryAt(backStackCount - 1);
            if (entry.getName() != null) {
                setTitle(entry.getName());
            }
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                getSupportActionBar().setDisplayShowHomeEnabled(false);
            }
            setTitle(getString(R.string.english_ime_name));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull final PreferenceFragmentCompat caller,
                                            @NonNull final Preference pref) {
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(), pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);

        CharSequence title = pref.getTitle();
        if (title == null) {
            title = getString(R.string.english_ime_name);
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, fragment)
                .addToBackStack(title.toString())
                .commit();

        setTitle(title);
        return true;
    }

    @Override
    public boolean onPreferenceStartScreen(@NonNull final PreferenceFragmentCompat caller,
                                          @NonNull final PreferenceScreen pref) {
        return false;
    }

    public boolean isValidFragment(final String fragmentName) {
        return FragmentUtils.isValidFragment(fragmentName);
    }
}
