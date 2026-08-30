/*
 * Copyright (C) 2012 The Android Open Source Project
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

package rkr.simplekeyboard.inputmethod.latin.utils;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

public final class InputTypeUtils implements InputType {
    private static final int WEB_TEXT_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_PASSWORD;
    private static final int NUMBER_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD;
    private static final int TEXT_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
    private static final int TEXT_VISIBLE_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    public static final int IME_ACTION_CUSTOM_LABEL = EditorInfo.IME_MASK_ACTION + 1;

    private InputTypeUtils() {
        // This utility class is not publicly instantiable.
    }

    private static boolean isWebPasswordInputType(final int inputType) {
        return inputType == WEB_TEXT_PASSWORD_INPUT_TYPE;
    }

    private static boolean isNumberPasswordInputType(final int inputType) {
        return inputType == NUMBER_PASSWORD_INPUT_TYPE;
    }

    private static boolean isTextPasswordInputType(final int inputType) {
        return inputType == TEXT_PASSWORD_INPUT_TYPE;
    }

    private static boolean isWebEmailAddressVariation(int variation) {
        return variation == TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
    }

    public static boolean isEmailVariation(final int variation) {
        return variation == TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || isWebEmailAddressVariation(variation);
    }

    // Please refer to TextView.isPasswordInputType
    public static boolean isPasswordInputType(final int inputType) {
        final int maskedInputType =
                inputType & (TYPE_MASK_CLASS | TYPE_MASK_VARIATION);
        return isTextPasswordInputType(maskedInputType) || isWebPasswordInputType(maskedInputType)
                || isNumberPasswordInputType(maskedInputType);
    }

    // Please refer to TextView.isVisiblePasswordInputType
    public static boolean isVisiblePasswordInputType(final int inputType) {
        final int maskedInputType =
                inputType & (TYPE_MASK_CLASS | TYPE_MASK_VARIATION);
        return maskedInputType == TEXT_VISIBLE_PASSWORD_INPUT_TYPE;
    }

    public static int getImeOptionsActionIdFromEditorInfo(final EditorInfo editorInfo) {
        if ((editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE;
        } else if (editorInfo.actionLabel != null) {
            return IME_ACTION_CUSTOM_LABEL;
        } else {
            // Note: this is different from editorInfo.actionId, hence "ImeOptionsActionId"
            return editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        }
    }
}
