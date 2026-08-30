package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SettingsSwipeSensitivityTest {

    private SharedPreferences createFakePreferences(final Map<String, Object> values) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("getString".equals(method.getName())) {
                    String key = (String) args[0];
                    String defValue = (String) args[1];
                    if (values.containsKey(key)) {
                        return values.get(key);
                    }
                    return defValue;
                }
                if ("getBoolean".equals(method.getName())) {
                    String key = (String) args[0];
                    boolean defValue = (boolean) args[1];
                    if (values.containsKey(key)) {
                        return values.get(key);
                    }
                    return defValue;
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

    @Test
    public void testReadSwipeSensitivityWithNullPrefs() {
        float sensitivity = Settings.readSwipeSensitivity(null);
        assertEquals(1.0f, sensitivity, 0.001f);
    }

    @Test
    public void testReadSwipeSensitivityDefault() {
        Map<String, Object> map = new HashMap<>();
        SharedPreferences prefs = createFakePreferences(map);
        float sensitivity = Settings.readSwipeSensitivity(prefs);
        assertEquals(1.0f, sensitivity, 0.001f);
    }

    @Test
    public void testReadSwipeSensitivityPresets() {
        Map<String, Object> map = new HashMap<>();

        map.put(Settings.PREF_SWIPE_SENSITIVITY, "0.6");
        assertEquals(0.6f, Settings.readSwipeSensitivity(createFakePreferences(map)), 0.001f);

        map.put(Settings.PREF_SWIPE_SENSITIVITY, "1.0");
        assertEquals(1.0f, Settings.readSwipeSensitivity(createFakePreferences(map)), 0.001f);

        map.put(Settings.PREF_SWIPE_SENSITIVITY, "1.5");
        assertEquals(1.5f, Settings.readSwipeSensitivity(createFakePreferences(map)), 0.001f);
    }

    @Test
    public void testReadSwipeSensitivityInvalid() {
        Map<String, Object> map = new HashMap<>();

        map.put(Settings.PREF_SWIPE_SENSITIVITY, "invalid");
        assertEquals(1.0f, Settings.readSwipeSensitivity(createFakePreferences(map)), 0.001f);

        map.put(Settings.PREF_SWIPE_SENSITIVITY, "");
        assertEquals(1.0f, Settings.readSwipeSensitivity(createFakePreferences(map)), 0.001f);
    }
}
