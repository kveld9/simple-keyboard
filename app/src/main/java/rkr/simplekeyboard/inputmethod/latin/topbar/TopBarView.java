package rkr.simplekeyboard.inputmethod.latin.topbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class TopBarView extends FrameLayout {
    public static final int MODE_NORMAL = 0;
    public static final int MODE_TOOL_TRAY = 1;

    private int mCurrentMode = MODE_NORMAL;

    private LinearLayout mNormalModeContainer;
    private ImageView mExpandButton;
    private View mRightSpacer;
    private LinearLayout mSuggestionsContainer;
    private TextView mLeftSlot;
    private TextView mCenterSlot;
    private TextView mRightSlot;

    private LinearLayout mToolTrayContainer;
    private ImageView mCloseButton;
    private ImageView mClipboardButton;
    private ImageView mSettingsButton;
    private ImageView mLanguageButton;

    private TopBarListener mListener;

    private int mTextColor = 0xFFCCCCCC;

    public TopBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setListener(TopBarListener listener) {
        mListener = listener;
    }

    private void init(Context context) {
        android.content.res.TypedArray a = context.obtainStyledAttributes(null, new int[]{android.R.attr.background}, rkr.simplekeyboard.inputmethod.R.attr.keyboardViewStyle, rkr.simplekeyboard.inputmethod.R.style.KeyboardView);
        android.graphics.drawable.Drawable bg = a.getDrawable(0);
        a.recycle();
        if (bg != null) {
            setBackground(bg);
        }

        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(rkr.simplekeyboard.inputmethod.R.attr.keyTextColor, typedValue, true)) {
            mTextColor = typedValue.data;
        }

        mNormalModeContainer = new LinearLayout(context);
        mNormalModeContainer.setOrientation(LinearLayout.HORIZONTAL);
        mNormalModeContainer.setGravity(Gravity.CENTER_VERTICAL);
        mNormalModeContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        mExpandButton = createIconButton(context, rkr.simplekeyboard.inputmethod.R.drawable.ic_more_horiz);
        mExpandButton.setOnClickListener(v -> setMode(MODE_TOOL_TRAY));
        mNormalModeContainer.addView(mExpandButton);

        // 3-slot centered suggestions container
        mSuggestionsContainer = new LinearLayout(context);
        mSuggestionsContainer.setOrientation(LinearLayout.HORIZONTAL);
        mSuggestionsContainer.setGravity(Gravity.CENTER_VERTICAL);
        mSuggestionsContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f));

        mLeftSlot = createSuggestionSlot(context, 14.0f, false);
        mCenterSlot = createSuggestionSlot(context, 15.5f, true);
        mRightSlot = createSuggestionSlot(context, 14.0f, false);

        mSuggestionsContainer.addView(mLeftSlot);
        mSuggestionsContainer.addView(mCenterSlot);
        mSuggestionsContainer.addView(mRightSlot);

        mNormalModeContainer.addView(mSuggestionsContainer);

        // Right spacer matching expand button width to keep center slot perfectly balanced
        mRightSpacer = new View(context);
        int iconWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44, context.getResources().getDisplayMetrics());
        mRightSpacer.setLayoutParams(new LinearLayout.LayoutParams(iconWidth, LayoutParams.MATCH_PARENT));
        mNormalModeContainer.addView(mRightSpacer);

        addView(mNormalModeContainer);

        mToolTrayContainer = new LinearLayout(context);
        mToolTrayContainer.setOrientation(LinearLayout.HORIZONTAL);
        mToolTrayContainer.setGravity(Gravity.CENTER_VERTICAL);
        mToolTrayContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        mCloseButton = createIconButton(context, rkr.simplekeyboard.inputmethod.R.drawable.ic_close_vector);
        mCloseButton.setOnClickListener(v -> setMode(MODE_NORMAL));
        mToolTrayContainer.addView(mCloseButton);

        mClipboardButton = createIconButton(context, rkr.simplekeyboard.inputmethod.R.drawable.sym_keyboard_paste);
        mClipboardButton.setOnClickListener(v -> {
            setMode(MODE_NORMAL);
            if (mListener != null) {
                mListener.onClipboardClicked();
            }
        });
        mToolTrayContainer.addView(mClipboardButton);

        mSettingsButton = createIconButton(context, rkr.simplekeyboard.inputmethod.R.drawable.sym_keyboard_settings);
        mSettingsButton.setOnClickListener(v -> {
            if (mListener != null) mListener.onSettingsClicked();
        });
        mToolTrayContainer.addView(mSettingsButton);

        mLanguageButton = createIconButton(context, rkr.simplekeyboard.inputmethod.R.drawable.sym_keyboard_language_switch);
        mLanguageButton.setOnClickListener(v -> {
            if (mListener != null) mListener.onLanguageClicked();
        });
        mToolTrayContainer.addView(mLanguageButton);
        
        addView(mToolTrayContainer);

        setMode(MODE_NORMAL);
    }

    private TextView createSuggestionSlot(Context context, float textSizeSp, boolean isBold) {
        TextView tv = new TextView(context);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f));
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(mTextColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        tv.setTypeface(isBold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        int paddingH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
        tv.setPadding(paddingH, 0, paddingH, 0);
        tv.setClickable(true);
        tv.setFocusable(false);
        tv.setVisibility(View.INVISIBLE);

        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
            tv.setBackgroundResource(outValue.resourceId);
        }
        return tv;
    }

    private ImageView createIconButton(Context context, int drawableResId) {
        ImageView iv = new ImageView(context);
        iv.setImageResource(drawableResId);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int widthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44, context.getResources().getDisplayMetrics());
        iv.setLayoutParams(new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setClickable(true);
        iv.setFocusable(false);
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
            iv.setBackgroundResource(outValue.resourceId);
        }
        return iv;
    }

    public void setMode(int mode) {
        mCurrentMode = mode;
        mNormalModeContainer.setVisibility(mode == MODE_NORMAL ? View.VISIBLE : View.GONE);
        mToolTrayContainer.setVisibility(mode == MODE_TOOL_TRAY ? View.VISIBLE : View.GONE);
    }

    public boolean isToolTrayOpen() {
        return mCurrentMode == MODE_TOOL_TRAY;
    }

    public void closeToolTray() {
        if (mCurrentMode != MODE_NORMAL) {
            setMode(MODE_NORMAL);
        }
    }

    public void setSuggestions(List<CharSequence> suggestions) {
        setSuggestions(suggestions, -1);
    }

    public void setSuggestions(List<CharSequence> suggestions, int boldIndex) {
        if (suggestions == null || suggestions.isEmpty()) {
            mLeftSlot.setText("");
            mLeftSlot.setVisibility(View.INVISIBLE);
            mLeftSlot.setOnClickListener(null);

            mCenterSlot.setText("");
            mCenterSlot.setVisibility(View.INVISIBLE);
            mCenterSlot.setOnClickListener(null);

            mRightSlot.setText("");
            mRightSlot.setVisibility(View.INVISIBLE);
            mRightSlot.setOnClickListener(null);
            return;
        }

        final int count = suggestions.size();
        if (count >= 3) {
            bindSlot(mLeftSlot, suggestions.get(0), false);
            bindSlot(mCenterSlot, suggestions.get(1), true);
            bindSlot(mRightSlot, suggestions.get(2), false);
        } else if (count == 2) {
            bindSlot(mLeftSlot, suggestions.get(0), false);
            bindSlot(mCenterSlot, suggestions.get(1), true);
            bindSlot(mRightSlot, null, false);
        } else {
            bindSlot(mLeftSlot, null, false);
            bindSlot(mCenterSlot, suggestions.get(0), boldIndex == 0);
            bindSlot(mRightSlot, null, false);
        }
    }

    private void bindSlot(TextView slot, final CharSequence text, boolean isHighlighted) {
        if (text == null || TextUtils.isEmpty(text)) {
            slot.setText("");
            slot.setVisibility(View.INVISIBLE);
            slot.setOnClickListener(null);
            return;
        }

        slot.setText(text);
        slot.setVisibility(View.VISIBLE);
        slot.setTypeface(isHighlighted ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        slot.setAlpha(isHighlighted ? 1.0f : 0.85f);

        slot.setOnClickListener(v -> {
            if (mListener != null) {
                String clean = text.toString();
                if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 2) {
                    clean = clean.substring(1, clean.length() - 1);
                }
                mListener.onSuggestionClicked(clean);
            }
        });
    }
    
    public void setLanguageButtonVisible(boolean visible) {
        if (mLanguageButton != null) {
            mLanguageButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 38, getContext().getResources().getDisplayMetrics());
        int spec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, spec);
    }
}

