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

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BackupRestoreDeviceTest {

    private Context mContext;
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mPrefs = PreferenceManagerCompat.getDeviceSharedPreferences(mContext);
    }

    @Test
    public void testFullDeviceBackupRestoreCycleAndRollback() throws Exception {
        // Step A: Set and capture initial state of 10 different preferences
        mPrefs.edit()
                .putBoolean(Settings.PREF_AUTO_CAP, true)
                .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
                .putBoolean(Settings.PREF_VIBRATE_ON, true)
                .putBoolean(Settings.PREF_SOUND_ON, false)
                .putBoolean(Settings.PREF_POPUP_ON, true)
                .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
                .putBoolean(Settings.PREF_CLIPBOARD_ENABLED, true)
                .putString(Settings.PREF_SWIPE_SENSITIVITY, "0.6")
                .putString(KeyboardTheme.KEYBOARD_THEME_KEY, "2")
                .putFloat(Settings.PREF_KEYBOARD_HEIGHT, 1.25f)
                .putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 450)
                .commit();

        // Step B: Export real backup JSON
        final String backupJson = BackupHelper.exportToJson(mPrefs);
        assertNotNull(backupJson);
        assertTrue(backupJson.length() > 50);

        // Step C: Verify JSON content & types
        final JSONObject root = new JSONObject(backupJson);
        assertEquals(BackupHelper.CURRENT_VERSION, root.getInt(BackupHelper.KEY_VERSION));
        assertEquals(BackupHelper.APP_IDENTIFIER, root.getString(BackupHelper.KEY_APP));
        assertTrue(root.has(BackupHelper.KEY_TIMESTAMP));

        final JSONObject prefsObj = root.getJSONObject(BackupHelper.KEY_PREFERENCES);
        assertTrue(prefsObj.length() >= 10);
        assertEquals(1.25, prefsObj.getJSONObject(Settings.PREF_KEYBOARD_HEIGHT).getDouble(BackupHelper.KEY_VALUE), 0.001);
        assertEquals("float", prefsObj.getJSONObject(Settings.PREF_KEYBOARD_HEIGHT).getString(BackupHelper.KEY_TYPE));
        assertEquals(450, prefsObj.getJSONObject(Settings.PREF_KEY_LONGPRESS_TIMEOUT).getInt(BackupHelper.KEY_VALUE));
        assertEquals("int", prefsObj.getJSONObject(Settings.PREF_KEY_LONGPRESS_TIMEOUT).getString(BackupHelper.KEY_TYPE));

        // Step D: Mutate preferences to completely different values
        mPrefs.edit()
                .putBoolean(Settings.PREF_AUTO_CAP, false)
                .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
                .putBoolean(Settings.PREF_VIBRATE_ON, false)
                .putBoolean(Settings.PREF_SOUND_ON, true)
                .putBoolean(Settings.PREF_POPUP_ON, false)
                .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, false)
                .putBoolean(Settings.PREF_CLIPBOARD_ENABLED, false)
                .putString(Settings.PREF_SWIPE_SENSITIVITY, "1.5")
                .putString(KeyboardTheme.KEYBOARD_THEME_KEY, "0")
                .putFloat(Settings.PREF_KEYBOARD_HEIGHT, 0.75f)
                .putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 200)
                .commit();

        // Step E: Verify mutated values in SharedPreferences
        assertEquals(false, mPrefs.getBoolean(Settings.PREF_AUTO_CAP, true));
        assertEquals(false, mPrefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true));
        assertEquals(0.75f, mPrefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT, 1.0f), 0.001f);
        assertEquals(200, mPrefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 0));

        // Step F: Restore backup JSON on device
        final BackupHelper.ValidationResult parseResult = BackupHelper.validateAndParseJson(backupJson);
        assertTrue(parseResult.success);
        boolean applied = BackupHelper.applyValidatedBackup(mPrefs, parseResult);
        assertTrue(applied);

        // Step G: Verify every single preference restored to exact state A
        assertEquals(true, mPrefs.getBoolean(Settings.PREF_AUTO_CAP, false));
        assertEquals(true, mPrefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false));
        assertEquals(true, mPrefs.getBoolean(Settings.PREF_VIBRATE_ON, false));
        assertEquals(false, mPrefs.getBoolean(Settings.PREF_SOUND_ON, true));
        assertEquals(true, mPrefs.getBoolean(Settings.PREF_POPUP_ON, false));
        assertEquals(true, mPrefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, false));
        assertEquals(true, mPrefs.getBoolean(Settings.PREF_CLIPBOARD_ENABLED, false));
        assertEquals("0.6", mPrefs.getString(Settings.PREF_SWIPE_SENSITIVITY, ""));
        assertEquals("2", mPrefs.getString(KeyboardTheme.KEYBOARD_THEME_KEY, ""));
        assertEquals(1.25f, mPrefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT, 1.0f), 0.001f);
        assertEquals(450, mPrefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 0));

        // Step H: Rollback Test: Valid preferences + 1 malformed at the end
        final String corruptAtEndJson = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"auto_cap\": {\"type\": \"boolean\", \"value\": false},\n" +
                "    \"pref_keyboard_height\": {\"type\": \"float\", \"value\": 1.99},\n" +
                "    \"pref_key_longpress_timeout\": {\"type\": \"int\", \"value\": \"NOT_AN_INT\"}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult corruptResult = BackupHelper.validateAndParseJson(corruptAtEndJson);
        assertFalse(corruptResult.success);
        assertNotNull(corruptResult.errorMessage);

        boolean corruptApplied = BackupHelper.applyValidatedBackup(mPrefs, corruptResult);
        assertFalse(corruptApplied);

        // Verify ZERO changes were made to device preferences (atomic rollback guarantee)
        assertEquals(true, mPrefs.getBoolean(Settings.PREF_AUTO_CAP, false));
        assertEquals(1.25f, mPrefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT, 1.0f), 0.001f);
        assertEquals(450, mPrefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 0));
    }
}
