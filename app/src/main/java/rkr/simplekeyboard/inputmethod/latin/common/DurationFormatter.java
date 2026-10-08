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
 * Pure-Java duration formatting ported from trackGym's {@code DurationFormatter}.
 * Formats and parses {@code MM:SS} values for settings delays without
 * {@code SimpleDateFormat} or per-call allocations (reused {@code ThreadLocal}
 * buffer; the returned formatted {@code String} is the only allocation).
 */
public final class DurationFormatter {
    private DurationFormatter() {
        // This utility class is not publicly instantiable.
    }

    private static final ThreadLocal<StringBuilder> S_SCRATCH =
            new ThreadLocal<StringBuilder>() {
                @Override
                protected StringBuilder initialValue() {
                    return new StringBuilder(8);
                }
            };

    /**
     * Formats seconds as zero-padded {@code MM:SS}. Negative input counts as 0.
     * Minutes grow past two digits (e.g. 6000 seconds renders {@code "100:00"}).
     */
    public static String formatSecondsToMmSs(final int totalSeconds) {
        final int safe = Math.max(0, totalSeconds);
        final int minutes = safe / 60;
        final int seconds = safe % 60;
        final StringBuilder sb = S_SCRATCH.get();
        sb.setLength(0);
        if (minutes < 10) {
            sb.append('0');
        }
        sb.append(minutes);
        sb.append(':');
        if (seconds < 10) {
            sb.append('0');
        }
        sb.append(seconds);
        return sb.toString();
    }

    /**
     * Parses free-form input into seconds: every digit counts, the last two
     * digits are seconds and the rest are minutes ({@code "1:05"} reads 65).
     * Null, digit-free, or overflowing input reads 0.
     */
    public static int parseInputToSeconds(final String input) {
        if (input == null) {
            return 0;
        }
        final int len = input.length();
        int digits = 0;
        for (int i = 0; i < len; i++) {
            final char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
            }
        }
        if (digits == 0 || digits > 9) {
            return 0;
        }
        long minutes = 0;
        int seconds = 0;
        int seen = 0;
        final int minuteDigits = digits - 2;
        for (int i = 0; i < len; i++) {
            final char c = input.charAt(i);
            if (c < '0' || c > '9') {
                continue;
            }
            if (seen < minuteDigits) {
                minutes = minutes * 10 + (c - '0');
            } else {
                seconds = seconds * 10 + (c - '0');
            }
            seen++;
        }
        final long total = minutes * 60 + seconds;
        return total > Integer.MAX_VALUE ? 0 : (int) total;
    }
}
