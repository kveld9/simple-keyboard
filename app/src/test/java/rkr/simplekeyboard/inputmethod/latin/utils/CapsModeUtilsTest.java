package rkr.simplekeyboard.inputmethod.latin.utils;

import android.text.TextUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class CapsModeUtilsTest {

    @Test
    public void testFlagsToString() {
        assertEquals("none", CapsModeUtils.flagsToString(0));
        assertEquals("characters", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_CHARACTERS));
        assertEquals("words", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_WORDS));
        assertEquals("sentences", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_SENTENCES));
        assertEquals("characters|words", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS));
        assertEquals("characters|words|sentences", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS | TextUtils.CAP_MODE_SENTENCES));
    }

    @Test
    public void testUnknownFlagsToString() {
        String result = CapsModeUtils.flagsToString(0x80000);
        assertTrue(result.startsWith("unknown<"));
    }
}
