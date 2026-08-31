/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2020 Raimondas Rimkus
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

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import java.util.Set;

import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;

/**
 * A base abstract class for a {@link PreferenceFragmentCompat} that implements a nested
 * {@link PreferenceScreen} of the main preference screen.
 */
public abstract class SubScreenFragment extends BasePreferenceFragment
        implements OnSharedPreferenceChangeListener {
    private static final String TAG = SubScreenFragment.class.getSimpleName();

    static void removePreference(final String prefKey, final PreferenceScreen screen) {
        if (screen == null) {
            Log.w(TAG, "removePreference: preference screen is null");
            return;
        }
        final Preference preference = screen.findPreference(prefKey);
        if (preference != null) {
            screen.removePreference(preference);
        }
    }

    final void removePreference(final String prefKey) {
        removePreference(prefKey, getPreferenceScreen());
    }

    final SharedPreferences getSharedPreferences() {
        return PreferenceManagerCompat.getDeviceSharedPreferences(requireActivity());
    }

    @Override
    public void addPreferencesFromResource(final int preferencesResId) {
        super.addPreferencesFromResource(preferencesResId);

        final Context context = getContext();
        if (context == null) {
            Log.w(TAG, "addPreferencesFromResource: context is null");
            return;
        }
        final Set<String> restrictionKeys = getSharedPreferences().getStringSet(Settings.ACTIVE_RESTRICTIONS, null);
        if (restrictionKeys != null && !restrictionKeys.isEmpty()) {
            final PreferenceGroup group = getPreferenceScreen();
            if (group != null) {
                final int count = group.getPreferenceCount();
                for (int index = 0; index < count; index++) {
                    final Preference preference = group.getPreference(index);
                    if (restrictionKeys.contains(preference.getKey())) {
                        preference.setEnabled(false);
                    }
                }
            }
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof SeekBarDialogPreference) {
            ((SeekBarDialogPreference) preference).showDialog(requireContext());
            return;
        }
        if (preference instanceof androidx.preference.ListPreference) {
            final androidx.preference.ListPreference listPref = (androidx.preference.ListPreference) preference;
            final CharSequence[] entries = listPref.getEntries();
            final CharSequence[] entryValues = listPref.getEntryValues();
            if (entries == null || entryValues == null) {
                super.onDisplayPreferenceDialog(preference);
                return;
            }
            int selectedIndex = listPref.findIndexOfValue(listPref.getValue());
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(listPref.getDialogTitle() != null ? listPref.getDialogTitle() : listPref.getTitle())
                    .setSingleChoiceItems(entries, selectedIndex, (dialog, which) -> {
                        if (which >= 0 && which < entryValues.length) {
                            String val = entryValues[which].toString();
                            if (listPref.callChangeListener(val)) {
                                listPref.setValue(val);
                            }
                        }
                        dialog.dismiss();
                    })
                    .show();
            return;

        }
        super.onDisplayPreferenceDialog(preference);
    }


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        final Context context = getActivity();
        if (context == null || getPreferenceScreen() == null) {
            final String tag = getClass().getSimpleName();
            Log.w(tag, "onSharedPreferenceChanged called before activity starts.");
            return;
        }
        new BackupManager(context).dataChanged();
    }
}
