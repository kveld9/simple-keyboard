package rkr.simplekeyboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.SharedPreferences;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;

/**
 * Centralized helper for key shape geometries, corner radius, and insets.
 */
public final class KeyShapeHelper {

    private KeyShapeHelper() {
        // Utility class: not instantiable
    }

    public static String getActiveKeyShape(final Context context) {
        final SharedPreferences prefs = PreferenceManagerCompat.getDeviceSharedPreferences(context);
        return Settings.readKeyShape(prefs);
    }

    public static float getCornerRadius(final Context context, final String keyShape) {
        if (Settings.KEY_SHAPE_ROUNDED.equals(keyShape) || Settings.KEY_SHAPE_BORDERLESS.equals(keyShape)) {
            return context.getResources().getDimension(R.dimen.button_corner_radius_rounded);
        }
        return context.getResources().getDimension(R.dimen.button_corner_radius_lxx);
    }

    public static float getRoundedInsetRatioX(final String keyShape) {
        return Settings.KEY_SHAPE_ROUNDED.equals(keyShape) ? 0.08f : 0.0f;
    }

    public static float getRoundedInsetRatioY(final String keyShape) {
        return Settings.KEY_SHAPE_ROUNDED.equals(keyShape) ? 0.02f : 0.0f;
    }
}
