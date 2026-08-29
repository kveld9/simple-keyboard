package rkr.simplekeyboard.inputmethod.keyboard.internal;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AlphabetShiftStateTest {

    private AlphabetShiftState mShiftState;

    @Before
    public void setUp() {
        mShiftState = new AlphabetShiftState();
    }

    @Test
    public void testInitialState() {
        assertFalse(mShiftState.isShiftedOrShiftLocked());
        assertFalse(mShiftState.isShiftLocked());
        assertFalse(mShiftState.isManualShifted());
        assertFalse(mShiftState.isAutomaticShifted());
    }

    @Test
    public void testManualShiftTransition() {
        mShiftState.setShifted(true);
        assertTrue(mShiftState.isShiftedOrShiftLocked());
        assertTrue(mShiftState.isManualShifted());
        assertFalse(mShiftState.isShiftLocked());

        mShiftState.setShifted(false);
        assertFalse(mShiftState.isShiftedOrShiftLocked());
        assertFalse(mShiftState.isManualShifted());
    }

    @Test
    public void testAutomaticShiftTransition() {
        mShiftState.setAutomaticShifted();
        assertTrue(mShiftState.isShiftedOrShiftLocked());
        assertTrue(mShiftState.isAutomaticShifted());
        assertFalse(mShiftState.isManualShifted());
        assertFalse(mShiftState.isShiftLocked());

        mShiftState.setShifted(false);
        assertFalse(mShiftState.isShiftedOrShiftLocked());
        assertFalse(mShiftState.isAutomaticShifted());
    }

    @Test
    public void testShiftLockTransition() {
        mShiftState.setShiftLocked(true);
        assertTrue(mShiftState.isShiftedOrShiftLocked());
        assertTrue(mShiftState.isShiftLocked());
        assertFalse(mShiftState.isShiftLockShifted());

        // While shift locked, pressing shift toggles shift lock shifted state
        mShiftState.setShifted(true);
        assertTrue(mShiftState.isShiftLockShifted());
        assertTrue(mShiftState.isShiftLocked());

        mShiftState.setShifted(false);
        assertTrue(mShiftState.isShiftLocked());
        assertFalse(mShiftState.isShiftLockShifted());

        mShiftState.setShiftLocked(false);
        assertFalse(mShiftState.isShiftedOrShiftLocked());
        assertFalse(mShiftState.isShiftLocked());
    }
}
