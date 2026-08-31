/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2021 wittmane
 * Copyright (C) 2021 Raimondas Rimkus
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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager;
import rkr.simplekeyboard.inputmethod.latin.Subtype;
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils;
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils;

/**
 * "Languages" settings screen with direct switches for all supported languages.
 */
public final class LanguagesSettingsFragment extends SubScreenFragment {
    private static final String TAG = LanguagesSettingsFragment.class.getSimpleName();

    private RichInputMethodManager mRichImm;
    private final List<LanguageSwitchPreference> mLanguagePreferences = new ArrayList<>();

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        RichInputMethodManager.init(requireContext());
        mRichImm = RichInputMethodManager.getInstance();

        setPreferencesFromResource(R.xml.empty_settings, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        buildContent();
    }

    private void buildContent() {
        final Context context = getContext();
        if (context == null) {
            Log.w(TAG, "buildContent: context is null");
            return;
        }
        final PreferenceGroup group = getPreferenceScreen();
        if (group == null) {
            Log.w(TAG, "buildContent: preference group is null");
            return;
        }
        group.removeAll();
        mLanguagePreferences.clear();

        final PreferenceCategory languageCategory = new PreferenceCategory(context);
        languageCategory.setTitle(R.string.user_languages);
        group.addPreference(languageCategory);

        final Set<Subtype> enabledSubtypes = mRichImm.getEnabledSubtypes(false);
        final Set<String> enabledLocales = new HashSet<>();
        for (final Subtype subtype : enabledSubtypes) {
            enabledLocales.add(subtype.getLocale());
        }

        final List<String> supportedLocales = SubtypeLocaleUtils.getSupportedLocales();
        for (final String localeString : supportedLocales) {
            final Subtype defaultSubtype = SubtypeLocaleUtils.getDefaultSubtype(localeString, getResources());
            if (defaultSubtype == null) continue;

            final boolean isChecked = enabledLocales.contains(localeString);
            final LanguageSwitchPreference pref = new LanguageSwitchPreference(context, defaultSubtype);
            pref.setTitle(LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(localeString));
            pref.setChecked(isChecked);
            pref.setEnabled(true);

            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!(newValue instanceof Boolean)) {
                    return false;
                }
                final boolean isEnabling = (boolean) newValue;
                final LanguageSwitchPreference langPref = (LanguageSwitchPreference) preference;

                if (isEnabling) {
                    return mRichImm.addSubtype(langPref.getSubtype());
                } else {
                    int checkedCount = 0;
                    for (final LanguageSwitchPreference p : mLanguagePreferences) {
                        if (p.isChecked()) {
                            checkedCount++;
                        }
                    }
                    if (checkedCount <= 1) {
                        return false;
                    }
                    return mRichImm.removeSubtype(langPref.getSubtype());
                }
            });

            group.addPreference(pref);
            mLanguagePreferences.add(pref);
        }
    }

    private static class LanguageSwitchPreference extends SwitchPreferenceCompat {
        private final Subtype mSubtype;

        public LanguageSwitchPreference(final Context context, final Subtype subtype) {
            super(context);
            mSubtype = subtype;
            setPersistent(false);
        }

        public Subtype getSubtype() {
            return mSubtype;
        }
    }
}
