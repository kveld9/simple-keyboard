/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2022 Raimondas Rimkus
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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import rkr.simplekeyboard.inputmethod.R;

public final class SeekBarDialogPreference extends DialogPreference
        implements SeekBar.OnSeekBarChangeListener {
    public interface ValueProxy {
        int readValue(final String key);
        int readDefaultValue(final String key);
        void writeValue(final int value, final String key);
        void writeDefaultValue(final String key);
        String getValueText(final int value);
        void feedbackValue(final int value);
    }

    public static abstract class SimpleIntProxy implements ValueProxy {
        private final SharedPreferences mPrefs;

        public SimpleIntProxy(final SharedPreferences prefs) {
            mPrefs = prefs;
        }

        protected SharedPreferences getSharedPreferences() {
            return mPrefs;
        }

        @Override
        public void writeValue(final int value, final String key) {
            if (mPrefs.getInt(key, Integer.MIN_VALUE) != value) {
                mPrefs.edit().putInt(key, value).apply();
            }
        }

        @Override
        public void writeDefaultValue(final String key) {
            mPrefs.edit().remove(key).apply();
        }

        @Override
        public void feedbackValue(final int value) {}
    }

    public static abstract class PercentageFloatProxy implements ValueProxy {
        public static final float PERCENTAGE_FLOAT = 100.0f;
        private final SharedPreferences mPrefs;

        public PercentageFloatProxy(final SharedPreferences prefs) {
            mPrefs = prefs;
        }

        protected SharedPreferences getSharedPreferences() {
            return mPrefs;
        }

        public static float getValueFromPercentage(final int percentage) {
            return percentage / PERCENTAGE_FLOAT;
        }

        public static int getPercentageFromValue(final float floatValue) {
            return Math.round(floatValue * PERCENTAGE_FLOAT);
        }

        @Override
        public void writeValue(final int value, final String key) {
            final float floatValue = getValueFromPercentage(value);
            if (Float.compare(mPrefs.getFloat(key, Float.NaN), floatValue) != 0) {
                mPrefs.edit().putFloat(key, floatValue).apply();
            }
        }

        @Override
        public void writeDefaultValue(final String key) {
            mPrefs.edit().remove(key).apply();
        }

        @Override
        public void feedbackValue(final int value) {}
    }

    private final int mMaxValue;
    private final int mMinValue;
    private final int mStepValue;

    private TextView mValueView;
    private SeekBar mSeekBar;

    private ValueProxy mValueProxy;

    public SeekBarDialogPreference(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SeekBarDialogPreference, 0, 0);
        mMaxValue = a.getInt(R.styleable.SeekBarDialogPreference_maxValue, 0);
        mMinValue = a.getInt(R.styleable.SeekBarDialogPreference_minValue, 0);
        mStepValue = a.getInt(R.styleable.SeekBarDialogPreference_stepValue, 0);
        a.recycle();
        setDialogLayoutResource(R.layout.seek_bar_dialog);
        setWidgetLayoutResource(R.layout.preference_chevron);
    }

    public void setInterface(final ValueProxy proxy) {
        mValueProxy = proxy;
        final int value = clipValue(mValueProxy.readValue(getKey()));
        setSummary(mValueProxy.getValueText(value));
    }

    public void showDialog(@NonNull final Context context) {
        if (mValueProxy == null) return;
        final View view = LayoutInflater.from(context).inflate(R.layout.seek_bar_dialog, null);
        mSeekBar = view.findViewById(R.id.seek_bar_dialog_bar);
        mSeekBar.setMax(mMaxValue - mMinValue);
        mSeekBar.setOnSeekBarChangeListener(this);
        mValueView = view.findViewById(R.id.seek_bar_dialog_value);

        final int initialValue = mValueProxy.readValue(getKey());
        mValueView.setText(mValueProxy.getValueText(initialValue));
        mSeekBar.setProgress(getProgressFromValue(clipValue(initialValue)));

        new MaterialAlertDialogBuilder(context)
                .setTitle(getTitle())
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final int value = getClippedValueFromProgress(mSeekBar.getProgress());
                    setSummary(mValueProxy.getValueText(value));
                    mValueProxy.writeValue(value, getKey());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.button_default, (dialog, which) -> {
                    final int value = mValueProxy.readDefaultValue(getKey());
                    setSummary(mValueProxy.getValueText(value));
                    mValueProxy.writeDefaultValue(getKey());
                })
                .show();
    }

    private int getProgressFromValue(final int value) {
        return value - mMinValue;
    }

    private int getValueFromProgress(final int progress) {
        return progress + mMinValue;
    }

    private int clipValue(final int value) {
        final int clippedValue = Math.min(mMaxValue, Math.max(mMinValue, value));
        if (mStepValue <= 1) {
            return clippedValue;
        }
        return clippedValue - (clippedValue % mStepValue);
    }

    private int getClippedValueFromProgress(final int progress) {
        return clipValue(getValueFromProgress(progress));
    }

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
        if (mValueProxy != null) {
            final int value = getClippedValueFromProgress(progress);
            if (mValueView != null) {
                mValueView.setText(mValueProxy.getValueText(value));
            }
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        if (mValueProxy != null) {
            mValueProxy.feedbackValue(getClippedValueFromProgress(seekBar.getProgress()));
        }
    }
}
