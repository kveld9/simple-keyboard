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
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme;

public final class DialogUtils {
    private DialogUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int getPlatformDialogThemeId(final Context context) {
        final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(context);
        if (keyboardTheme != null) {
            switch (keyboardTheme.mThemeId) {
                case KeyboardTheme.THEME_ID_BLACK:
                    return R.style.platformDialogTheme_Black;
                case KeyboardTheme.THEME_ID_DARK:
                case KeyboardTheme.THEME_ID_DARK_BORDER:
                    return R.style.platformDialogTheme_Dark;
                case KeyboardTheme.THEME_ID_LIGHT:
                case KeyboardTheme.THEME_ID_LIGHT_BORDER:
                    return R.style.platformDialogTheme_Light;
                case KeyboardTheme.THEME_ID_SYSTEM:
                case KeyboardTheme.THEME_ID_SYSTEM_BORDER:
                default:
                    final boolean isNight = (context.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                    return isNight ? R.style.platformDialogTheme_Dark : R.style.platformDialogTheme_Light;
            }
        }
        return R.style.platformDialogTheme;
    }

    public static MaterialAlertDialogBuilder createMaterialDialogBuilder(final Context context) {
        return new MaterialAlertDialogBuilder(context, getPlatformDialogThemeId(context));
    }

    public static Context getPlatformDialogThemeContext(final Context context) {
        return new ContextThemeWrapper(context, getPlatformDialogThemeId(context));
    }
}
