package rkr.simplekeyboard.inputmethod.latin;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputAttributesTest {

    @Test
    public void testUrlAndEmailFieldDetection() {
        EditorInfo uriInfo = new EditorInfo();
        uriInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
        InputAttributes uriAttr = new InputAttributes(uriInfo, false);
        assertTrue(uriAttr.mIsUrlOrEmailField);
        assertFalse(uriAttr.mIsPasswordField);
        assertFalse(uriAttr.mShouldShowSuggestions);

        EditorInfo emailInfo = new EditorInfo();
        emailInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        InputAttributes emailAttr = new InputAttributes(emailInfo, false);
        assertTrue(emailAttr.mIsUrlOrEmailField);
        assertFalse(emailAttr.mIsPasswordField);
        assertFalse(emailAttr.mShouldShowSuggestions);

        EditorInfo webEmailInfo = new EditorInfo();
        webEmailInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
        InputAttributes webEmailAttr = new InputAttributes(webEmailInfo, false);
        assertTrue(webEmailAttr.mIsUrlOrEmailField);
        assertFalse(webEmailAttr.mIsPasswordField);
        assertFalse(webEmailAttr.mShouldShowSuggestions);

        EditorInfo textInfo = new EditorInfo();
        textInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;
        InputAttributes textAttr = new InputAttributes(textInfo, false);
        assertFalse(textAttr.mIsUrlOrEmailField);
        assertFalse(textAttr.mIsPasswordField);
        assertTrue(textAttr.mShouldShowSuggestions);

        EditorInfo passwordInfo = new EditorInfo();
        passwordInfo.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        InputAttributes passwordAttr = new InputAttributes(passwordInfo, false);
        assertFalse(passwordAttr.mIsUrlOrEmailField);
        assertTrue(passwordAttr.mIsPasswordField);
        assertFalse(passwordAttr.mShouldShowSuggestions);
    }
}
