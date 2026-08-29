package rkr.simplekeyboard.inputmethod.latin.utils;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class InputTypeUtilsTest {

    @Test
    public void testIsPasswordInputType() {
        int textPassword = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        int webPassword = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        int numberPassword = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        int normalText = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;

        assertTrue(InputTypeUtils.isPasswordInputType(textPassword));
        assertTrue(InputTypeUtils.isPasswordInputType(webPassword));
        assertTrue(InputTypeUtils.isPasswordInputType(numberPassword));
        assertFalse(InputTypeUtils.isPasswordInputType(normalText));
    }

    @Test
    public void testIsVisiblePasswordInputType() {
        int visiblePassword = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        int normalPassword = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;

        assertTrue(InputTypeUtils.isVisiblePasswordInputType(visiblePassword));
        assertFalse(InputTypeUtils.isVisiblePasswordInputType(normalPassword));
    }

    @Test
    public void testIsEmailVariation() {
        assertTrue(InputTypeUtils.isEmailVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        assertTrue(InputTypeUtils.isEmailVariation(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS));
        assertFalse(InputTypeUtils.isEmailVariation(InputType.TYPE_TEXT_VARIATION_URI));
    }

    @Test
    public void testIsAutoSpaceFriendlyType() {
        int normalText = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        int emailText = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        int passwordText = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        int numberClass = InputType.TYPE_CLASS_NUMBER;

        assertTrue(InputTypeUtils.isAutoSpaceFriendlyType(normalText));
        assertFalse(InputTypeUtils.isAutoSpaceFriendlyType(emailText));
        assertFalse(InputTypeUtils.isAutoSpaceFriendlyType(passwordText));
        assertFalse(InputTypeUtils.isAutoSpaceFriendlyType(numberClass));
    }

    @Test
    public void testGetImeOptionsActionIdFromEditorInfo() {
        EditorInfo editorInfo = new EditorInfo();
        editorInfo.imeOptions = EditorInfo.IME_ACTION_SEARCH;
        assertEquals(EditorInfo.IME_ACTION_SEARCH, InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo));

        editorInfo.imeOptions = EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        assertEquals(EditorInfo.IME_ACTION_NONE, InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo));

        editorInfo.imeOptions = EditorInfo.IME_ACTION_SEND;
        editorInfo.actionLabel = "Custom";
        assertEquals(InputTypeUtils.IME_ACTION_CUSTOM_LABEL, InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo));
    }
}
