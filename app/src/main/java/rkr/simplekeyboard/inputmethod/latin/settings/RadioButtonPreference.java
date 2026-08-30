/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2017 Raimondas Rimkus
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
import android.util.AttributeSet;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import rkr.simplekeyboard.inputmethod.R;

/**
 * Radio Button preference
 */
public class RadioButtonPreference extends Preference {
    interface OnRadioButtonClickedListener {
        /**
         * Called when this preference needs to be saved its state.
         *
         * @param preference This preference.
         */
        void onRadioButtonClicked(RadioButtonPreference preference);
    }

    private boolean mIsSelected;
    private RadioButton mRadioButton;
    private OnRadioButtonClickedListener mListener;

    public RadioButtonPreference(@NonNull final Context context) {
        this(context, null);
    }

    public RadioButtonPreference(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    public RadioButtonPreference(@NonNull final Context context, @Nullable final AttributeSet attrs,
            final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWidgetLayoutResource(R.layout.radio_button_preference_widget);
        setOnPreferenceClickListener(preference -> {
            callListenerOnRadioButtonClicked();
            return true;
        });
    }

    public void setOnRadioButtonClickedListener(final OnRadioButtonClickedListener listener) {
        mListener = listener;
    }

    void callListenerOnRadioButtonClicked() {
        if (mListener != null) {
            mListener.onRadioButtonClicked(this);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mRadioButton = (RadioButton) holder.findViewById(R.id.radio_button);
        if (mRadioButton != null) {
            mRadioButton.setChecked(mIsSelected);
            mRadioButton.setOnClickListener(v -> callListenerOnRadioButtonClicked());
        }
    }

    public void setSelected(final boolean selected) {
        if (selected == mIsSelected) {
            return;
        }
        mIsSelected = selected;
        if (mRadioButton != null) {
            mRadioButton.setChecked(selected);
        }
        notifyChanged();
    }
}
