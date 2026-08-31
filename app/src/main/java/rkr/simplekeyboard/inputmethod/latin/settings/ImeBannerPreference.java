package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.utils.ApplicationUtils;

public class ImeBannerPreference extends Preference {
    private static final String TAG = ImeBannerPreference.class.getSimpleName();

    public ImeBannerPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_ime_banner);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setOnClickListener(v -> handleClick());
        final View card = holder.findViewById(R.id.ime_banner_card);
        if (card != null) {
            card.setOnClickListener(v -> handleClick());
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        handleClick();
    }

    private void handleClick() {
        final Context context = getContext();
        if (context == null) {
            Log.w(TAG, "handleClick: context is null");
            return;
        }

        final InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            Log.w(TAG, "handleClick: InputMethodManager is null");
            return;
        }

        if (!ApplicationUtils.isImeEnabled(context, imm)) {
            final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "handleClick: Failed to start ACTION_INPUT_METHOD_SETTINGS, falling back to showInputMethodPicker", e);
                imm.showInputMethodPicker();
            }
        } else {
            imm.showInputMethodPicker();
        }
    }
}

