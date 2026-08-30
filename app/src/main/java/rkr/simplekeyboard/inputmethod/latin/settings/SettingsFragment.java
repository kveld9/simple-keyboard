/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.utils.ApplicationUtils;

public final class SettingsFragment extends InputMethodSettingsFragment {
    private static final String TAG = "SettingsFragment";

    private Preference mImeBannerPref;

    private final ContentObserver mSettingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateImeBanner();
        }
    };

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.prefs, rootKey);
        initSettings(requireContext());

        final Resources res = getResources();

        mImeBannerPref = findPreference("ime_not_active_banner");

        final Preference privacyPref = findPreference("privacy_policy");
        if (privacyPref != null) {
            privacyPref.setOnPreferenceClickListener(preference -> {
                ApplicationUtils.openUrl(getContext(), res.getString(R.string.privacy_policy_url));
                return true;
            });
        }

        final Preference licensePref = findPreference("license");
        if (licensePref != null) {
            licensePref.setOnPreferenceClickListener(preference -> {
                ApplicationUtils.openUrl(getContext(), res.getString(R.string.license_url));
                return true;
            });
        }

        final Preference versionPref = findPreference("version_info");
        if (versionPref != null) {
            versionPref.setSummary(rkr.simplekeyboard.inputmethod.BuildConfig.VERSION_NAME);
            versionPref.setOnPreferenceClickListener(preference -> {
                ApplicationUtils.openUrl(getContext(), res.getString(R.string.check_for_updates_url));
                return true;
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        final Context context = getContext();
        if (context != null) {
            try {
                context.getContentResolver().registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                        false,
                        mSettingsObserver
                );
                context.getContentResolver().registerContentObserver(
                        Settings.Secure.getUriFor("enabled_input_methods"),
                        false,
                        mSettingsObserver
                );
            } catch (Exception ignored) {
            }
        }
        updateImeBanner();
    }

    @Override
    public void onStop() {
        super.onStop();
        final Context context = getContext();
        if (context != null) {
            try {
                context.getContentResolver().unregisterContentObserver(mSettingsObserver);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateImeBanner();
    }

    public void updateImeBanner() {
        if (mImeBannerPref == null) return;
        final Context context = getContext();
        if (context == null) return;

        final InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        final String currentIme = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
        final String pkg = context.getPackageName();
        final boolean isCurrentDefault = currentIme != null && (currentIme.startsWith(pkg + "/") || currentIme.contains(pkg));

        if (isCurrentDefault) {
            mImeBannerPref.setVisible(false);
            return;
        }

        mImeBannerPref.setVisible(true);
        final boolean isEnabled = ApplicationUtils.isImeEnabled(context, imm);
        if (!isEnabled) {
            mImeBannerPref.setTitle(R.string.ime_not_enabled_title);
            mImeBannerPref.setSummary(R.string.ime_not_enabled_summary);
        } else {
            mImeBannerPref.setTitle(R.string.ime_not_active_title);
            mImeBannerPref.setSummary(R.string.ime_not_active_summary);
        }
    }
}
