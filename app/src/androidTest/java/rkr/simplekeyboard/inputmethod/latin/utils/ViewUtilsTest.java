package rkr.simplekeyboard.inputmethod.latin.utils;

import android.content.Context;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import rkr.simplekeyboard.inputmethod.R;

import static org.junit.Assert.assertNotEquals;

@RunWith(AndroidJUnit4.class)
public class ViewUtilsTest {

    @Test
    public void testGetThemeColor_resolvesReferenceProperly() {
        Context appContext = ApplicationProvider.getApplicationContext();
        ContextThemeWrapper context = new ContextThemeWrapper(appContext, R.style.KeyboardTheme_LXX_Dark);
        
        int defaultColor = 0;
        int color = ViewUtils.getThemeColor(context, R.attr.keyPressedBackgroundColor, defaultColor);
        
        // It should NOT be the default color
        assertNotEquals("The color resolved should not be the default color", defaultColor, color);
        
        // If the buggy implementation is present, it returns the Resource ID (e.g. 0x7F05001A).
        // Resource IDs typically have 0x7F as the highest byte.
        // If it's a real color like #40000000, the highest byte is 0x40.
        int topByte = (color >> 24) & 0xFF;
        assertNotEquals("Regression detected! getThemeColor returned a Resource ID (starts with 0x7F) instead of a true ARGB color.", 0x7F, topByte);
    }
}
