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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class DurationFormatterTest {

    @Test
    public void testFormatKnownValues() {
        assertEquals("00:00", DurationFormatter.formatSecondsToMmSs(0));
        assertEquals("00:05", DurationFormatter.formatSecondsToMmSs(5));
        assertEquals("01:05", DurationFormatter.formatSecondsToMmSs(65));
        assertEquals("59:59", DurationFormatter.formatSecondsToMmSs(3599));
        assertEquals("100:00", DurationFormatter.formatSecondsToMmSs(6000));
    }

    @Test
    public void testFormatNegativeCountsAsZero() {
        assertEquals("00:00", DurationFormatter.formatSecondsToMmSs(-1));
        assertEquals("00:00", DurationFormatter.formatSecondsToMmSs(Integer.MIN_VALUE));
    }

    @Test
    public void testParseFreeFormInput() {
        assertEquals(65, DurationFormatter.parseInputToSeconds("1:05"));
        assertEquals(65, DurationFormatter.parseInputToSeconds("105"));
        assertEquals(5, DurationFormatter.parseInputToSeconds("5"));
        assertEquals(5, DurationFormatter.parseInputToSeconds("05"));
        assertEquals(6000, DurationFormatter.parseInputToSeconds("100:00"));
        assertEquals(150, DurationFormatter.parseInputToSeconds(" 2m30s "));
    }

    @Test
    public void testParseEmptyAndGarbageReadsZero() {
        assertEquals(0, DurationFormatter.parseInputToSeconds(null));
        assertEquals(0, DurationFormatter.parseInputToSeconds(""));
        assertEquals(0, DurationFormatter.parseInputToSeconds("abc"));
        assertEquals(0, DurationFormatter.parseInputToSeconds("9999999999"));
    }

    @Test
    public void testFormatParseRoundtrip() {
        assertEquals(65, DurationFormatter.parseInputToSeconds(
                DurationFormatter.formatSecondsToMmSs(65)));
        assertEquals(3599, DurationFormatter.parseInputToSeconds(
                DurationFormatter.formatSecondsToMmSs(3599)));
    }
}
