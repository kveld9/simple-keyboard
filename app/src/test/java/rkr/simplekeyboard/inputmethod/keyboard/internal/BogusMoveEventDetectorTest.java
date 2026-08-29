package rkr.simplekeyboard.inputmethod.keyboard.internal;

import org.junit.Test;
import static org.junit.Assert.*;

public class BogusMoveEventDetectorTest {

    @Test
    public void testDistanceAccumulation() {
        BogusMoveEventDetector detector = new BogusMoveEventDetector();
        detector.setKeyboardGeometry(100, 150);

        detector.onActualDownEvent(50, 50);
        detector.onDownKey();
        assertEquals(0, detector.getAccumulatedDistanceFromDownKey());

        detector.onMoveKey(15);
        detector.onMoveKey(25);
        assertEquals(40, detector.getAccumulatedDistanceFromDownKey());

        detector.onDownKey();
        assertEquals(0, detector.getAccumulatedDistanceFromDownKey());
    }

    @Test
    public void testHasTraveledLongDistanceWhenHackDisabled() {
        BogusMoveEventDetector detector = new BogusMoveEventDetector();
        detector.setKeyboardGeometry(100, 150);
        detector.onActualDownEvent(50, 50);
        detector.onDownKey();
        detector.onMoveKey(500);

        // When hack is disabled (default on standard phones), it always returns false
        assertFalse(detector.hasTraveledLongDistance(200, 50));
    }
}
