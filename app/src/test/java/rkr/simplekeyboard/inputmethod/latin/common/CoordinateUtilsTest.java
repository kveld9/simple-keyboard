package rkr.simplekeyboard.inputmethod.latin.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoordinateUtilsTest {

    @Test
    public void testNewInstanceAndSet() {
        int[] coords = CoordinateUtils.newInstance();
        assertNotNull(coords);
        assertEquals(2, coords.length);
        assertEquals(0, CoordinateUtils.x(coords));
        assertEquals(0, CoordinateUtils.y(coords));

        CoordinateUtils.set(coords, 150, 300);
        assertEquals(150, CoordinateUtils.x(coords));
        assertEquals(300, CoordinateUtils.y(coords));
    }

    @Test
    public void testCopy() {
        int[] source = CoordinateUtils.newInstance();
        CoordinateUtils.set(source, 42, 99);

        int[] destination = CoordinateUtils.newInstance();
        CoordinateUtils.copy(destination, source);

        assertEquals(42, CoordinateUtils.x(destination));
        assertEquals(99, CoordinateUtils.y(destination));
    }
}
