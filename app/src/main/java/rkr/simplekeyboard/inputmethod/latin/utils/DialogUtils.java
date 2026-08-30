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
import android.os.IBinder;
import android.view.ContextThemeWrapper;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme;

public final class DialogUtils {
    private DialogUtils() {
        // This utility class is not publicly instantiable.
    }

    public static Context getPlatformDialogThemeContext(final Context context) {
        final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(context);
        final int baseThemeResId;
        if (keyboardTheme != null) {
            switch (keyboardTheme.mThemeId) {
                case KeyboardTheme.THEME_ID_BLACK:
                    baseThemeResId = R.style.platformDialogBaseTheme_Black;
                    break;
                case KeyboardTheme.THEME_ID_DARK:
                case KeyboardTheme.THEME_ID_DARK_BORDER:
                    baseThemeResId = R.style.platformDialogBaseTheme_Dark;
                    break;
                case KeyboardTheme.THEME_ID_LIGHT:
                case KeyboardTheme.THEME_ID_LIGHT_BORDER:
                    baseThemeResId = R.style.platformDialogBaseTheme_Light;
                    break;
                case KeyboardTheme.THEME_ID_SYSTEM:
                case KeyboardTheme.THEME_ID_SYSTEM_BORDER:
                default:
                    final boolean isNight = (context.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                    baseThemeResId = isNight ? R.style.platformDialogBaseTheme_Dark : R.style.platformDialogBaseTheme_Light;
                    break;
            }
        } else {
            baseThemeResId = R.style.platformDialogBaseTheme_System;
        }
        return new ContextThemeWrapper(context, baseThemeResId);
    }

    public static MaterialAlertDialogBuilder createMaterialDialogBuilder(final Context context) {
        return new MaterialAlertDialogBuilder(getPlatformDialogThemeContext(context));
    }

    public static void setupAndShowDialog(final AlertDialog dialog, final IBinder windowToken) {
        if (dialog == null) {
            return;
        }
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        setupDialogWindow(dialog.getWindow(), windowToken);
        dialog.show();
    }

    public static void setupDialogWindow(final Window window, final IBinder windowToken) {
        if (window == null) {
            return;
        }
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = windowToken;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
}
