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

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BackupHelperTest {

    private SharedPreferences createFakePreferences(final Map<String, Object> values) {
        final Map<String, Object> storage = new HashMap<>(values);

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                final String methodName = method.getName();

                if ("getAll".equals(methodName)) {
                    return new HashMap<>(storage);
                }
                if ("getInt".equals(methodName)) {
                    String key = (String) args[0];
                    int defValue = (int) args[1];
                    Object val = storage.get(key);
                    return (val instanceof Number) ? ((Number) val).intValue() : defValue;
                }
                if ("getFloat".equals(methodName)) {
                    String key = (String) args[0];
                    float defValue = (float) args[1];
                    Object val = storage.get(key);
                    return (val instanceof Number) ? ((Number) val).floatValue() : defValue;
                }
                if ("getBoolean".equals(methodName)) {
                    String key = (String) args[0];
                    boolean defValue = (boolean) args[1];
                    Object val = storage.get(key);
                    return (val instanceof Boolean) ? (Boolean) val : defValue;
                }
                if ("getString".equals(methodName)) {
                    String key = (String) args[0];
                    String defValue = (String) args[1];
                    Object val = storage.get(key);
                    return (val instanceof String) ? (String) val : defValue;
                }
                if ("edit".equals(methodName)) {
                    return createFakeEditor(storage);
                }
                return null;
            }
        };

        return (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                handler
        );
    }

    private SharedPreferences.Editor createFakeEditor(final Map<String, Object> storage) {
        final Map<String, Object> pending = new HashMap<>();

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                final String name = method.getName();
                if (name.startsWith("put")) {
                    String key = (String) args[0];
                    Object val = args[1];
                    pending.put(key, val);
                    return proxy;
                }
                if ("remove".equals(name)) {
                    String key = (String) args[0];
                    pending.put(key, null);
                    return proxy;
                }
                if ("clear".equals(name)) {
                    pending.clear();
                    return proxy;
                }
                if ("apply".equals(name) || "commit".equals(name)) {
                    for (Map.Entry<String, Object> entry : pending.entrySet()) {
                        if (entry.getValue() == null) {
                            storage.remove(entry.getKey());
                        } else {
                            storage.put(entry.getKey(), entry.getValue());
                        }
                    }
                    pending.clear();
                    return true;
                }
                return null;
            }
        };

        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class},
                handler
        );
    }

    @Test
    public void testExportAndRoundTripIntegrity() throws Exception {
        final Map<String, Object> initialPrefs = new HashMap<>();
        initialPrefs.put(Settings.PREF_AUTO_CAP, true);
        initialPrefs.put(Settings.PREF_AUTO_PERIOD, false);
        initialPrefs.put(Settings.PREF_KEYBOARD_HEIGHT, 1.15f);
        initialPrefs.put(Settings.PREF_KEYPRESS_SOUND_VOLUME, 0.45f);
        initialPrefs.put(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 350);
        initialPrefs.put(Settings.PREF_ENABLED_SUBTYPES, "en_US:qwerty;es_US:qwerty");
        initialPrefs.put(KeyboardTheme.KEYBOARD_THEME_KEY, "3");

        final SharedPreferences prefs = createFakePreferences(initialPrefs);
        final String json = BackupHelper.exportToJson(prefs);
        assertNotNull(json);

        // Verify JSON structure
        final JSONObject root = new JSONObject(json);
        assertEquals(BackupHelper.CURRENT_VERSION, root.getInt(BackupHelper.KEY_VERSION));
        assertEquals(BackupHelper.APP_IDENTIFIER, root.getString(BackupHelper.KEY_APP));
        assertTrue(root.has(BackupHelper.KEY_TIMESTAMP));

        final JSONObject prefsObj = root.getJSONObject(BackupHelper.KEY_PREFERENCES);
        assertEquals(true, prefsObj.getJSONObject(Settings.PREF_AUTO_CAP).getBoolean(BackupHelper.KEY_VALUE));
        assertEquals("boolean", prefsObj.getJSONObject(Settings.PREF_AUTO_CAP).getString(BackupHelper.KEY_TYPE));

        assertEquals(1.15, prefsObj.getJSONObject(Settings.PREF_KEYBOARD_HEIGHT).getDouble(BackupHelper.KEY_VALUE), 0.001);
        assertEquals("float", prefsObj.getJSONObject(Settings.PREF_KEYBOARD_HEIGHT).getString(BackupHelper.KEY_TYPE));

        assertEquals(350, prefsObj.getJSONObject(Settings.PREF_KEY_LONGPRESS_TIMEOUT).getInt(BackupHelper.KEY_VALUE));
        assertEquals("int", prefsObj.getJSONObject(Settings.PREF_KEY_LONGPRESS_TIMEOUT).getString(BackupHelper.KEY_TYPE));

        assertEquals("en_US:qwerty;es_US:qwerty", prefsObj.getJSONObject(Settings.PREF_ENABLED_SUBTYPES).getString(BackupHelper.KEY_VALUE));
        assertEquals("string", prefsObj.getJSONObject(Settings.PREF_ENABLED_SUBTYPES).getString(BackupHelper.KEY_TYPE));

        // Test restore
        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(json);
        assertTrue(result.success);
        assertEquals(7, result.validEntriesCount);

        final Map<String, Object> restoredTarget = new HashMap<>();
        final SharedPreferences targetPrefs = createFakePreferences(restoredTarget);
        boolean applied = BackupHelper.applyValidatedBackup(targetPrefs, result);
        assertTrue(applied);

        assertEquals(true, targetPrefs.getBoolean(Settings.PREF_AUTO_CAP, false));
        assertEquals(false, targetPrefs.getBoolean(Settings.PREF_AUTO_PERIOD, true));
        assertEquals(1.15f, targetPrefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT, 1.0f), 0.001f);
        assertEquals(0.45f, targetPrefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, 0.0f), 0.001f);
        assertEquals(350, targetPrefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 0));
        assertEquals("en_US:qwerty;es_US:qwerty", targetPrefs.getString(Settings.PREF_ENABLED_SUBTYPES, null));
        assertEquals("3", targetPrefs.getString(KeyboardTheme.KEYBOARD_THEME_KEY, null));
    }

    @Test
    public void testIgnoreUnknownAndBlacklistedKeys() throws Exception {
        final String jsonWithUnknown = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"auto_cap\": {\"type\": \"boolean\", \"value\": true},\n" +
                "    \"active_restrictions\": {\"type\": \"string\", \"value\": \"restricted\"},\n" +
                "    \"some_future_key\": {\"type\": \"string\", \"value\": \"future_value\"}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(jsonWithUnknown);
        assertTrue(result.success);
        assertEquals(1, result.validEntriesCount);
        assertTrue(result.validatedEntries.containsKey(Settings.PREF_AUTO_CAP));
        assertFalse(result.validatedEntries.containsKey(Settings.ACTIVE_RESTRICTIONS));
        assertFalse(result.validatedEntries.containsKey("some_future_key"));
        assertTrue(result.ignoredKeys.contains(Settings.ACTIVE_RESTRICTIONS));
        assertTrue(result.ignoredKeys.contains("some_future_key"));
    }

    @Test
    public void testRejectUnsupportedVersion() {
        final String higherVersionJson = "{\n" +
                "  \"version\": 2,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"auto_cap\": {\"type\": \"boolean\", \"value\": true}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(higherVersionJson);
        assertFalse(result.success);
        assertNotNull(result.errorMessage);
        assertTrue(result.errorMessage.contains("Unsupported backup version"));
    }

    @Test
    public void testRejectMissingVersionOrPreferences() {
        final String noVersionJson = "{\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {}\n" +
                "}";

        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(noVersionJson);
        assertFalse(result.success);
        assertTrue(result.errorMessage.contains("version"));

        final String noPrefsJson = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\"\n" +
                "}";
        final BackupHelper.ValidationResult result2 = BackupHelper.validateAndParseJson(noPrefsJson);
        assertFalse(result2.success);
        assertTrue(result2.errorMessage.contains("preferences"));
    }

    @Test
    public void testRejectMalformedAndEmptyJson() {
        final BackupHelper.ValidationResult emptyResult = BackupHelper.validateAndParseJson("");
        assertFalse(emptyResult.success);

        final BackupHelper.ValidationResult malformedResult = BackupHelper.validateAndParseJson("{ truncated json");
        assertFalse(malformedResult.success);
    }

    @Test
    public void testRejectOversizedStream() {
        final byte[] hugeData = new byte[600 * 1024]; // 600 KB > 512 KB
        final ByteArrayInputStream bais = new ByteArrayInputStream(hugeData);
        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseStream(bais);
        assertFalse(result.success);
        assertTrue(result.errorMessage.contains("512 KB"));
    }

    @Test
    public void testTypeMismatchAndNullHandling() {
        // Known key with wrong type should fail validation
        final String badTypeJson = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"auto_cap\": {\"type\": \"string\", \"value\": \"true\"}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(badTypeJson);
        assertFalse(result.success);
        assertTrue(result.errorMessage.contains("Type mismatch"));

        // Known key with null value should fail validation
        final String nullValueJson = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"pref_key_longpress_timeout\": {\"type\": \"int\", \"value\": null}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult nullResult = BackupHelper.validateAndParseJson(nullValueJson);
        assertFalse(nullResult.success);
        assertTrue(nullResult.errorMessage.contains("Null value"));
    }

    @Test
    public void testFloatForIntKeyMismatch() {
        // pref_key_longpress_timeout is INT, but declared as float in JSON
        final String floatForIntJson = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"pref_key_longpress_timeout\": {\"type\": \"float\", \"value\": 300.5}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(floatForIntJson);
        assertFalse(result.success);
        assertTrue(result.errorMessage.contains("Type mismatch"));
    }

    @Test
    public void testRejectOverlyLongStrings() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append('a');
        }
        final String longStringJson = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"pref_enabled_subtypes\": {\"type\": \"string\", \"value\": \"" + sb.toString() + "\"}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(longStringJson);
        assertFalse(result.success);
        assertTrue(result.errorMessage.contains("maximum allowed length"));
    }

    @Test
    public void testAllOrNothingRollbackWhenOneEntryFails() {
        // Initial state
        final Map<String, Object> storage = new HashMap<>();
        storage.put(Settings.PREF_AUTO_CAP, false);
        storage.put(Settings.PREF_KEYBOARD_HEIGHT, 0.8f);
        storage.put(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 200);
        final SharedPreferences prefs = createFakePreferences(storage);

        // JSON has 2 valid entries (auto_cap: true, pref_keyboard_height: 1.5)
        // and 1 invalid entry at the end (pref_key_longpress_timeout with invalid boolean type)
        final String partiallyInvalidJson = "{\n" +
                "  \"version\": 1,\n" +
                "  \"app\": \"rkr.simplekeyboard.inputmethod\",\n" +
                "  \"preferences\": {\n" +
                "    \"auto_cap\": {\"type\": \"boolean\", \"value\": true},\n" +
                "    \"pref_keyboard_height\": {\"type\": \"float\", \"value\": 1.5},\n" +
                "    \"pref_key_longpress_timeout\": {\"type\": \"boolean\", \"value\": true}\n" +
                "  }\n" +
                "}";

        final BackupHelper.ValidationResult result = BackupHelper.validateAndParseJson(partiallyInvalidJson);
        assertFalse(result.success);
        assertNotNull(result.errorMessage);

        // Attempt restore
        boolean applied = BackupHelper.applyValidatedBackup(prefs, result);
        assertFalse(applied);

        // Verify ZERO changes were made to preferences
        assertEquals(false, prefs.getBoolean(Settings.PREF_AUTO_CAP, true));
        assertEquals(0.8f, prefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT, 1.0f), 0.001f);
        assertEquals(200, prefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, 0));
    }
}
