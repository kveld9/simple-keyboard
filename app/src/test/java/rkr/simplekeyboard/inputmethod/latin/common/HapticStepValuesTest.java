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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

@RunWith(JUnit4.class)
public class HapticStepValuesTest {

    @Test
    public void testConstants() {
        assertEquals(400, HapticStepValues.MAX_WEIGHT_WHOLE);
        assertEquals(100, HapticStepValues.MAX_REPS);
        assertArrayEquals(new double[] { 0.0, 0.25, 0.5, 0.75 },
                HapticStepValues.getDefaultFractions(), 0.0);
        assertArrayEquals(new double[] { 0.0, 0.5 },
                HapticStepValues.getHalfFractions(), 0.0);
    }

    @Test
    public void testSplitCanonicalValues() {
        final double[] out = new double[2];
        HapticStepValues.splitWeight(15.0, HapticStepValues.getDefaultFractions(), out);
        assertArrayEquals(new double[] { 15.0, 0.0 }, out, 0.0);
        HapticStepValues.splitWeight(17.5, HapticStepValues.getDefaultFractions(), out);
        assertArrayEquals(new double[] { 17.0, 0.5 }, out, 0.0);
    }

    @Test
    public void testSplitSnapsToClosestFraction() {
        final double[] out = new double[2];
        HapticStepValues.splitWeight(17.6, HapticStepValues.getDefaultFractions(), out);
        assertArrayEquals(new double[] { 17.0, 0.5 }, out, 0.0);
    }

    @Test
    public void testSplitGuardsAndCaps() {
        final double[] out = new double[2];
        HapticStepValues.splitWeight(0.0, HapticStepValues.getDefaultFractions(), out);
        assertArrayEquals(new double[] { 0.0, 0.0 }, out, 0.0);
        HapticStepValues.splitWeight(-4.5, HapticStepValues.getDefaultFractions(), out);
        assertArrayEquals(new double[] { 0.0, 0.0 }, out, 0.0);
        HapticStepValues.splitWeight(Double.NaN, HapticStepValues.getDefaultFractions(), out);
        assertArrayEquals(new double[] { 0.0, 0.0 }, out, 0.0);
        HapticStepValues.splitWeight(500.0, HapticStepValues.getDefaultFractions(), out);
        assertArrayEquals(new double[] { 400.0, 0.0 }, out, 0.0);
    }

    @Test
    public void testCombineWeight() {
        assertEquals(17.5, HapticStepValues.combineWeight(17, 0.5), 0.0);
        assertEquals(0.0, HapticStepValues.combineWeight(-3, Double.NaN), 0.0);
        assertEquals(0.0, HapticStepValues.combineWeight(0, -0.25), 0.0);
    }

    @Test
    public void testSplitCombineRoundtrip() {
        final double[] out = new double[2];
        HapticStepValues.splitWeight(42.25, HapticStepValues.getDefaultFractions(), out);
        assertEquals(42.25, HapticStepValues.combineWeight((int) out[0], out[1]), 0.0);
    }

    @Test
    public void testFormatFractionDisplay() {
        assertEquals(".00", HapticStepValues.formatFractionDisplay(0.0));
        assertEquals(".25", HapticStepValues.formatFractionDisplay(0.25));
        assertEquals(".50", HapticStepValues.formatFractionDisplay(0.5));
        assertEquals(".75", HapticStepValues.formatFractionDisplay(0.75));
    }

    @Test
    public void testFindClosestIndex() {
        assertEquals(2, HapticStepValues.findClosestIndex(
                HapticStepValues.getDefaultFractions(), 0.6));
        assertEquals(0, HapticStepValues.findClosestIndex(
                HapticStepValues.getDefaultFractions(), 0.1));
        assertEquals(0, HapticStepValues.findClosestIndex(new double[0], 0.5));
        assertEquals(0, HapticStepValues.findClosestIndex(null, 0.5));
        assertEquals(0, HapticStepValues.findClosestIndex(
                HapticStepValues.getDefaultFractions(), Double.NaN));
    }

    @Test
    public void testFractionAccessorsReturnCopies() {
        assertNotSame(HapticStepValues.getDefaultFractions(),
                HapticStepValues.getDefaultFractions());
        assertArrayEquals(new double[] { 0.0, 0.25, 0.5, 0.75 },
                HapticStepValues.getDefaultFractions(), 0.0);
    }

    @Test
    public void testCombineNormalizesWholeFractions() {
        assertEquals(5.5, HapticStepValues.combineWeight(5, 2.5), 0.0);
    }

    @Test
    public void testFormatOutOfDomainRendersZero() {
        assertEquals(".00", HapticStepValues.formatFractionDisplay(0.999));
        assertEquals(".00", HapticStepValues.formatFractionDisplay(1.5));
    }
}
