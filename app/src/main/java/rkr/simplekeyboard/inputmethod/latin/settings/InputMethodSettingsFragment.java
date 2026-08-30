/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2021 wittmane
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

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

/**
 * This is a helper class for an IME's settings preference fragment. It's recommended for every
 * IME to have its own settings preference fragment which inherits this class.
 */
public abstract class InputMethodSettingsFragment extends PreferenceFragmentCompat {
    private final InputMethodSettingsImpl mSettings = new InputMethodSettingsImpl();

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setStorageDeviceProtected();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(null);
        setDividerHeight(0);

        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            recyclerView.setItemAnimator(null);
            recyclerView.setClipToPadding(false);
            recyclerView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            float density = getResources().getDisplayMetrics().density;
            int paddingBottom = (int) (16 * density);
            recyclerView.setPadding(0, 0, 0, paddingBottom);
        }
    }

    public void initSettings(Context context) {
        mSettings.init(context, getPreferenceScreen());
    }

    @Override
    public void onResume() {
        super.onResume();
        mSettings.updateEnabledSubtypeList();
    }
}
