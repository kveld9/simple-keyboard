package rkr.simplekeyboard.inputmethod.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardThemeTest {

    @Test
    public void testSearchKeyboardThemeById() {
        KeyboardTheme system = KeyboardTheme.searchKeyboardThemeById(KeyboardTheme.THEME_ID_SYSTEM);
        assertNotNull(system);
        assertEquals(KeyboardTheme.THEME_ID_SYSTEM, system.mThemeId);
        assertEquals("LXXSystem", system.mThemeName);

        KeyboardTheme light = KeyboardTheme.searchKeyboardThemeById(KeyboardTheme.THEME_ID_LIGHT);
        assertNotNull(light);
        assertEquals(KeyboardTheme.THEME_ID_LIGHT, light.mThemeId);

        KeyboardTheme dark = KeyboardTheme.searchKeyboardThemeById(KeyboardTheme.THEME_ID_DARK);
        assertNotNull(dark);
        assertEquals(KeyboardTheme.THEME_ID_DARK, dark.mThemeId);

        KeyboardTheme black = KeyboardTheme.searchKeyboardThemeById(KeyboardTheme.THEME_ID_BLACK);
        assertNotNull(black);
        assertEquals(KeyboardTheme.THEME_ID_BLACK, black.mThemeId);
        assertEquals("LXXBlack", black.mThemeName);
    }

    @Test
    public void testLegacyThemeIdsFallback() {
        KeyboardTheme lightBorder = KeyboardTheme.searchKeyboardThemeById(KeyboardTheme.THEME_ID_LIGHT_BORDER);
        assertNotNull(lightBorder);
        assertEquals(KeyboardTheme.THEME_ID_LIGHT_BORDER, lightBorder.mThemeId);

        KeyboardTheme darkBorder = KeyboardTheme.searchKeyboardThemeById(KeyboardTheme.THEME_ID_DARK_BORDER);
        assertNotNull(darkBorder);
        assertEquals(KeyboardTheme.THEME_ID_DARK_BORDER, darkBorder.mThemeId);

        KeyboardTheme systemBorder = KeyboardTheme.searchKeyboardThemeById(KeyboardTheme.THEME_ID_SYSTEM_BORDER);
        assertNotNull(systemBorder);
        assertEquals(KeyboardTheme.THEME_ID_SYSTEM_BORDER, systemBorder.mThemeId);
    }

    @Test
    public void testUnknownThemeReturnsNull() {
        assertNull(KeyboardTheme.searchKeyboardThemeById(9999));
        assertNull(KeyboardTheme.searchKeyboardThemeById(-1));
    }

    @Test
    public void testDefaultKeyboardTheme() {
        KeyboardTheme defaultTheme = KeyboardTheme.getDefaultKeyboardTheme();
        assertNotNull(defaultTheme);
        assertEquals(KeyboardTheme.DEFAULT_THEME_ID, defaultTheme.mThemeId);
    }
}
