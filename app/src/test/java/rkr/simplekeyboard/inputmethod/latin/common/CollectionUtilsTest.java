package rkr.simplekeyboard.inputmethod.latin.common;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class CollectionUtilsTest {

    @Test
    public void testArrayAsListSubrange() {
        String[] array = {"zero", "one", "two", "three", "four"};
        ArrayList<String> sublist = CollectionUtils.arrayAsList(array, 1, 4);

        assertEquals(3, sublist.size());
        assertEquals("one", sublist.get(0));
        assertEquals("two", sublist.get(1));
        assertEquals("three", sublist.get(2));
    }

    @Test
    public void testArrayAsListEmpty() {
        String[] array = {"a", "b", "c"};
        ArrayList<String> emptyList = CollectionUtils.arrayAsList(array, 2, 2);
        assertTrue(emptyList.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArrayAsListNegativeStart() {
        String[] array = {"a", "b"};
        CollectionUtils.arrayAsList(array, -1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArrayAsListStartGreaterThanEnd() {
        String[] array = {"a", "b"};
        CollectionUtils.arrayAsList(array, 2, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArrayAsListEndOutOfBounds() {
        String[] array = {"a", "b"};
        CollectionUtils.arrayAsList(array, 0, 3);
    }
}
