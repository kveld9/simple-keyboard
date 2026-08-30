package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

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
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }
}
