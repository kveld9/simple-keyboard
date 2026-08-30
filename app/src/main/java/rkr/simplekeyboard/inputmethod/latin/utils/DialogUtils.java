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

package rkr.simplekeyboard.inputmethod.latin.utils;

import android.content.Context;
import android.view.ContextThemeWrapper;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme;

public final class DialogUtils {
    private DialogUtils() {
        // This utility class is not publicly instantiable.
    }

    public static Context getPlatformDialogThemeContext(final Context context) {
        final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(context);
        final int dialogThemeResId;
        if (keyboardTheme != null) {
            switch (keyboardTheme.mThemeId) {
                case KeyboardTheme.THEME_ID_BLACK:
                    dialogThemeResId = R.style.platformDialogTheme_Black;
                    break;
                case KeyboardTheme.THEME_ID_DARK:
                case KeyboardTheme.THEME_ID_DARK_BORDER:
                    dialogThemeResId = R.style.platformDialogTheme_Dark;
                    break;
                case KeyboardTheme.THEME_ID_LIGHT:
                case KeyboardTheme.THEME_ID_LIGHT_BORDER:
                    dialogThemeResId = R.style.platformDialogTheme_Light;
                    break;
                default:
                    dialogThemeResId = R.style.platformDialogTheme;
                    break;
            }
        } else {
            dialogThemeResId = R.style.platformDialogTheme;
        }
        return new ContextThemeWrapper(context, dialogThemeResId);
    }
}
