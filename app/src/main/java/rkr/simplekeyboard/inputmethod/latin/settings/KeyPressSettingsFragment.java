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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.AudioAndHapticFeedbackManager;

/**
 * "Preferences" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Vibrate on keypress
 * - Keypress vibration duration
 * - Sound on keypress
 * - Keypress sound volume
 * - Popup on keypress
 * - Key long press delay
 */
public final class KeyPressSettingsFragment extends SubScreenFragment {
    private static final String TAG = KeyPressSettingsFragment.class.getSimpleName();

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.prefs_screen_key_press, rootKey);

        final Context context = requireContext();

        AudioAndHapticFeedbackManager.init(context);

        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATE_ON);
        }

        setupKeypressSoundVolumeSettings();
        setupKeyLongpressTimeoutSettings();
    }

    private void setupKeypressSoundVolumeSettings() {
        final SeekBarDialogPreference pref = findPreference(Settings.PREF_KEYPRESS_SOUND_VOLUME);
        if (pref == null) {
            Log.w(TAG, "setupKeypressSoundVolumeSettings: " + Settings.PREF_KEYPRESS_SOUND_VOLUME + " preference not found");
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.PercentageFloatProxy(prefs) {
            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(Settings.readKeypressSoundVolume(prefs));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(Settings.readDefaultKeypressSoundVolume());
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return Integer.toString(value);
            }

            @Override
            public void feedbackValue(final int value) {
                AudioAndHapticFeedbackManager.getInstance().playSoundEffect(
                        AudioManager.FX_KEYPRESS_STANDARD, getValueFromPercentage(value));
            }
        });
    }

    private void setupKeyLongpressTimeoutSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = findPreference(Settings.PREF_KEY_LONGPRESS_TIMEOUT);
        if (pref == null) {
            Log.w(TAG, "setupKeyLongpressTimeoutSettings: " + Settings.PREF_KEY_LONGPRESS_TIMEOUT + " preference not found");
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.SimpleIntProxy(prefs) {
            @Override
            public int readValue(final String key) {
                return Settings.readKeyLongpressTimeout(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeyLongpressTimeout(res);
            }

            @Override
            public String getValueText(final int value) {
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }
        });
    }
}
