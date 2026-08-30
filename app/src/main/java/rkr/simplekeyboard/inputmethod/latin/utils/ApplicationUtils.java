/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

public final class ApplicationUtils {
    private static final String TAG = ApplicationUtils.class.getSimpleName();

    private ApplicationUtils() {
        // This utility class is not publicly instantiable.
    }

    /**
     * A utility method to get the application's PackageInfo.versionName
     * @return the application's PackageInfo.versionName
     */
    public static String getVersionName(final Context context) {
        try {
            if (context == null) {
                return "";
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return "";
    }

    /**
     * A utility method to get the application's PackageInfo.versionCode
     * @return the application's PackageInfo.versionCode
     */
    public static int getVersionCode(final Context context) {
        try {
            if (context == null) {
                return 0;
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionCode;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return 0;
    }

    public static boolean isImeEnabled(final Context context, final InputMethodManager imm) {
        if (context == null || imm == null) {
            return false;
        }
        final String imePackageName = context.getPackageName();
        final List<InputMethodInfo> enabledImes = imm.getEnabledInputMethodList();
        if (enabledImes != null) {
            for (final InputMethodInfo imi : enabledImes) {
                if (imi.getPackageName().equals(imePackageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void openUrl(final Context context, final String uri) {
        if (context == null || uri == null) {
            return;
        }
        try {
            final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            if (!(context instanceof Activity)) {
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(browserIntent);
        } catch (final ActivityNotFoundException e) {
            Log.e(TAG, "Browser not found", e);
        }
    }
}
