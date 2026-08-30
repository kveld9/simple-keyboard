/*
 * Copyright (C) 2011 The Android Open Source Project
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

package rkr.simplekeyboard.inputmethod.latin;

import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import rkr.simplekeyboard.inputmethod.latin.utils.InputTypeUtils;

/**
 * Class to hold attributes of the input field.
 */
public final class InputAttributes {
    private final String TAG = InputAttributes.class.getSimpleName();

    public final String mTargetApplicationPackageName;
    public final boolean mIsPasswordField;
    public final boolean mShouldShowSuggestions;
    public final boolean mIsUrlOrEmailField;
    public final boolean mNoPersonalizedLearning;
    private final int mInputType;

    public InputAttributes(final EditorInfo editorInfo, final boolean isFullscreenMode) {
        mTargetApplicationPackageName = null != editorInfo ? editorInfo.packageName : null;
        final int inputType = null != editorInfo ? editorInfo.inputType : 0;
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        mInputType = inputType;
        mIsPasswordField = InputTypeUtils.isPasswordInputType(inputType)
                || InputTypeUtils.isVisiblePasswordInputType(inputType);
        mNoPersonalizedLearning = 0 != (null != editorInfo
                ? (editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) : 0);

        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            mShouldShowSuggestions = false;
            mIsUrlOrEmailField = false;
            return;
        }

        final int variation = inputType & InputType.TYPE_MASK_VARIATION;
        mIsUrlOrEmailField = InputTypeUtils.isEmailVariation(variation)
                || InputType.TYPE_TEXT_VARIATION_URI == variation;

        // Suppress suggestions only for password fields, email addresses, or direct URLs
        final boolean shouldSuppressSuggestions = mIsPasswordField || mIsUrlOrEmailField;
        mShouldShowSuggestions = !shouldSuppressSuggestions;
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return editorInfo != null && editorInfo.inputType == mInputType;
    }

    // Pretty print
    @Override
    public String toString() {
        return String.format(
                "%s: inputType=0x%08x%s%s%s targetApp=%s\n", getClass().getSimpleName(),
                mInputType,
                (mIsPasswordField ? " password" : ""),
                (mShouldShowSuggestions ? " shouldShowSuggestions" : ""),
                (mNoPersonalizedLearning ? " noPersonalizedLearning" : ""),
                mTargetApplicationPackageName);
    }
}
