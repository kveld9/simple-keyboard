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

package rkr.simplekeyboard.inputmethod.latin.dict;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class ProximityKeyMapTest {

    @Test
    public void testAdjacencyForSpecifiedKeys() {
        // 'a' está adyacente a 'q', 'w', 's', 'z'
        assertTrue(ProximityKeyMap.areAdjacent('a', 'q'));
        assertTrue(ProximityKeyMap.areAdjacent('a', 'w'));
        assertTrue(ProximityKeyMap.areAdjacent('a', 's'));
        assertTrue(ProximityKeyMap.areAdjacent('a', 'z'));
        assertFalse(ProximityKeyMap.areAdjacent('a', 'p'));
        assertFalse(ProximityKeyMap.areAdjacent('a', 'm'));

        // 's' a 'a', 'w', 'e', 'd', 'x', 'z'
        assertTrue(ProximityKeyMap.areAdjacent('s', 'a'));
        assertTrue(ProximityKeyMap.areAdjacent('s', 'w'));
        assertTrue(ProximityKeyMap.areAdjacent('s', 'e'));
        assertTrue(ProximityKeyMap.areAdjacent('s', 'd'));
        assertTrue(ProximityKeyMap.areAdjacent('s', 'x'));
        assertTrue(ProximityKeyMap.areAdjacent('s', 'z'));
        assertFalse(ProximityKeyMap.areAdjacent('s', 'k'));

        // 'c' a 'x', 'd', 'f', 'v'
        assertTrue(ProximityKeyMap.areAdjacent('c', 'x'));
        assertTrue(ProximityKeyMap.areAdjacent('c', 'd'));
        assertTrue(ProximityKeyMap.areAdjacent('c', 'f'));
        assertTrue(ProximityKeyMap.areAdjacent('c', 'v'));
        assertFalse(ProximityKeyMap.areAdjacent('c', 'p'));

        // 'n' a 'b', 'h', 'j', 'm'
        assertTrue(ProximityKeyMap.areAdjacent('n', 'b'));
        assertTrue(ProximityKeyMap.areAdjacent('n', 'h'));
        assertTrue(ProximityKeyMap.areAdjacent('n', 'j'));
        assertTrue(ProximityKeyMap.areAdjacent('n', 'm'));
        assertFalse(ProximityKeyMap.areAdjacent('n', 'q'));

        // 'ñ' a 'l', 'p'
        assertTrue(ProximityKeyMap.areAdjacent('ñ', 'l'));
        assertTrue(ProximityKeyMap.areAdjacent('ñ', 'p'));
        assertFalse(ProximityKeyMap.areAdjacent('ñ', 'z'));
    }

    @Test
    public void testDistanceWeight() {
        assertEquals(0.0f, ProximityKeyMap.getDistanceWeight('a', 'a'), 0.001f);
        assertEquals(0.0f, ProximityKeyMap.getDistanceWeight('A', 'a'), 0.001f);
        assertEquals(0.5f, ProximityKeyMap.getDistanceWeight('a', 's'), 0.001f);
        assertEquals(0.5f, ProximityKeyMap.getDistanceWeight('s', 'a'), 0.001f);
        assertEquals(0.5f, ProximityKeyMap.getDistanceWeight('ñ', 'p'), 0.001f);
        assertEquals(1.0f, ProximityKeyMap.getDistanceWeight('a', 'p'), 0.001f);
        assertEquals(1.0f, ProximityKeyMap.getDistanceWeight('ñ', 'z'), 0.001f);
    }

    @Test
    public void testCaseAndAccentInsensitiveAdjacency() {
        assertTrue(ProximityKeyMap.areAdjacent('A', 'S'));
        assertTrue(ProximityKeyMap.areAdjacent('á', 's'));
        assertTrue(ProximityKeyMap.areAdjacent('Ñ', 'L'));
    }
}
