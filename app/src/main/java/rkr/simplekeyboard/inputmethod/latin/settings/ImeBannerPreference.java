package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.util.List;

import rkr.simplekeyboard.inputmethod.R;

public class ImeBannerPreference extends Preference {

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
        if (context == null) return;

        final InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        if (!isImeEnabled(context, imm)) {
            final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e) {
                imm.showInputMethodPicker();
            }
        } else {
            imm.showInputMethodPicker();
        }
    }

    private boolean isImeEnabled(Context context, InputMethodManager imm) {
        final String imePackageName = context.getPackageName();
        final List<InputMethodInfo> enabledImes = imm.getEnabledInputMethodList();
        if (enabledImes != null) {
            for (final InputMethodInfo imi : enabledImes) {
                if (imi.getPackageName().equals(imePackageName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

