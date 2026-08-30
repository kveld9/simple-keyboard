/*
 * Copyright (C) 2026 Simple Keyboard Authors
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

package rkr.simplekeyboard.inputmethod.compat;

import android.os.Build;

public final class BuildCompatUtils {
    private BuildCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    public static boolean isAtLeast(final int apiLevel) {
        return Build.VERSION.SDK_INT >= apiLevel;
    }

    public static boolean isAtLeastM() {
        return isAtLeast(Build.VERSION_CODES.M);
    }

    public static boolean isAtLeastN() {
        return isAtLeast(Build.VERSION_CODES.N);
    }

    public static boolean isAtLeastNMR1() {
        return isAtLeast(Build.VERSION_CODES.N_MR1);
    }

    public static boolean isAtLeastO() {
        return isAtLeast(Build.VERSION_CODES.O);
    }

    public static boolean isAtLeastP() {
        return isAtLeast(Build.VERSION_CODES.P);
    }

    public static boolean isAtLeastQ() {
        return isAtLeast(Build.VERSION_CODES.Q);
    }

    public static boolean isAtLeastR() {
        return isAtLeast(Build.VERSION_CODES.R);
    }

    public static boolean isAtLeastS() {
        return isAtLeast(Build.VERSION_CODES.S);
    }

    public static boolean isAtLeastSV2() {
        return isAtLeast(Build.VERSION_CODES.S_V2);
    }

    public static boolean isAtLeastTiramisu() {
        return isAtLeast(Build.VERSION_CODES.TIRAMISU);
    }

    public static boolean isAtLeastUpsideDownCake() {
        return isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
    }

    public static boolean isAtLeastBaklava() {
        return isAtLeast(Build.VERSION_CODES.BAKLAVA);
    }
}
