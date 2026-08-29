/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.SwitchPreference;

import rkr.simplekeyboard.inputmethod.R;

public final class AutocorrectSettingsFragment extends SubScreenFragment {
    private static final int PERMISSION_REQUEST_CONTACTS = 102;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_autocorrect);

        final Preference contactsPref = findPreference(Settings.PREF_USE_CONTACTS);
        if (contactsPref != null) {
            contactsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (Boolean.TRUE.equals(newValue)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (getActivity() != null && getActivity().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSION_REQUEST_CONTACTS);
                            return false;
                        }
                    }
                }
                return true;
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CONTACTS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            final SwitchPreference pref = (SwitchPreference) findPreference(Settings.PREF_USE_CONTACTS);
            if (pref != null) {
                pref.setChecked(true);
            }
        }
    }
}
