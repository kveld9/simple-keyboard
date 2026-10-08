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

package rkr.simplekeyboard.inputmethod.latin.common;

/**
 * Pure-Java stepped-value engine ported from trackGym's
 * {@code HapticWheelValuesEngine}. Splits decimal settings (vibration strength,
 * gesture thresholds, delays) into whole and fractional steps and formats the
 * fraction for settings rows. Uses primitive {@code double[]} constants, never
 * boxed collections. All methods are allocation-free; the fraction display
 * {@code String} is cold-path only (settings UI, never per keypress).
 */
public final class HapticStepValues {
    private HapticStepValues() {
        // This utility class is not publicly instantiable.
    }

    /** Canonical quarter-step fractions. Do not mutate. */
    public static final double[] DEFAULT_FRACTIONS = { 0.0, 0.25, 0.5, 0.75 };
    /** Coarse half-step fractions. Do not mutate. */
    public static final double[] HALF_FRACTIONS = { 0.0, 0.5 };

    public static final int MAX_WEIGHT_WHOLE = 400;
    public static final int MAX_REPS = 100;

    /**
     * Splits {@code value} into {@code out[0] = whole} and
     * {@code out[1] = closest fraction}. {@code out} must hold at least two
     * slots. Non-positive or non-finite input writes {@code {0, 0.0}}.
     * The whole part caps at {@link #MAX_WEIGHT_WHOLE}.
     */
    public static void splitWeight(final double value, final double[] fractions,
            final double[] out) {
        if (out == null || out.length < 2) {
            throw new IllegalArgumentException("out must hold at least 2 slots");
        }
        final double[] steps = fractions == null || fractions.length == 0
                ? DEFAULT_FRACTIONS : fractions;
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
            out[0] = 0;
            out[1] = 0.0;
            return;
        }
        final double safe = Math.min(value, MAX_WEIGHT_WHOLE);
        final int whole = (int) safe;
        final double remainder = safe - whole;
        double closest = steps[0];
        double bestDiff = Math.abs(closest - remainder);
        for (int i = 1; i < steps.length; i++) {
            final double diff = Math.abs(steps[i] - remainder);
            if (diff < bestDiff) {
                bestDiff = diff;
                closest = steps[i];
            }
        }
        out[0] = whole;
        out[1] = closest;
    }

    /**
     * Combines a whole part and a fraction into a value rounded to two decimals.
     * Negative input counts as 0.
     */
    public static double combineWeight(final int whole, final double fraction) {
        final int safeWhole = Math.max(0, whole);
        final double safeFraction =
                (Double.isNaN(fraction) || Double.isInfinite(fraction) || fraction < 0.0)
                        ? 0.0 : fraction;
        return Math.round((safeWhole + safeFraction) * 100.0) / 100.0;
    }

    /**
     * Formats a fraction for settings display ({@code .00}, {@code .25},
     * {@code .50}, {@code .75}). Cold path only.
     */
    public static String formatFractionDisplay(final double fraction) {
        if (!(fraction > 0.001)) {
            return ".00";
        }
        if (Math.abs(fraction - 0.25) < 0.01) {
            return ".25";
        }
        if (Math.abs(fraction - 0.5) < 0.01) {
            return ".50";
        }
        if (Math.abs(fraction - 0.75) < 0.01) {
            return ".75";
        }
        int hundredths = (int) Math.round(fraction * 100.0);
        if (hundredths < 0) {
            hundredths = 0;
        } else if (hundredths > 99) {
            hundredths = 99;
        }
        final String digits = Integer.toString(hundredths);
        return digits.length() < 2 ? ".0" + digits : "." + digits;
    }

    /**
     * Returns the index of the entry nearest to {@code target}, or 0 when
     * {@code items} is null, empty, or {@code target} is not finite.
     */
    public static int findClosestIndex(final double[] items, final double target) {
        if (items == null || items.length == 0
                || Double.isNaN(target) || Double.isInfinite(target)) {
            return 0;
        }
        int best = 0;
        double bestDiff = Math.abs(items[0] - target);
        for (int i = 1; i < items.length; i++) {
            final double diff = Math.abs(items[i] - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }
}
