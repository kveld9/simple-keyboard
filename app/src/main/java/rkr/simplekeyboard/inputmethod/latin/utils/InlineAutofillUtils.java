package rkr.simplekeyboard.inputmethod.latin.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Size;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.inline.InlineContentView;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.UiVersions.StylesBuilder;
import androidx.autofill.inline.common.ImageViewStyle;
import androidx.autofill.inline.common.TextViewStyle;
import androidx.autofill.inline.common.ViewStyle;
import androidx.autofill.inline.v1.InlineSuggestionUi;
import androidx.autofill.inline.v1.InlineSuggestionUi.Style;

import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.R;

@SuppressLint("RestrictedApi")
@RequiresApi(api = Build.VERSION_CODES.R)
public final class InlineAutofillUtils {

    private InlineAutofillUtils() {}

    public static InlineSuggestionsRequest createInlineSuggestionRequest(final Context context) {
        final int chipBgDrawableId = androidx.autofill.R.drawable.autofill_inline_suggestion_chip_background;
        final int textColor = ViewUtils.getThemeColor(context, R.attr.keyTextColor, 0xFFE0E0E0);
        final int hintTextColor = (textColor & 0x00FFFFFF) | 0x88000000;
        final int chipBgColor = (textColor & 0x00FFFFFF) | 0x1E000000;

        final StylesBuilder stylesBuilder = UiVersions.newStylesBuilder();
        final Style style = InlineSuggestionUi.newStyleBuilder()
                .setSingleIconChipStyle(
                        new ViewStyle.Builder()
                                .setBackground(Icon.createWithResource(context, chipBgDrawableId).setTint(chipBgColor))
                                .setPadding(0, 0, 0, 0)
                                .build())
                .setChipStyle(
                        new ViewStyle.Builder()
                                .setBackground(Icon.createWithResource(context, chipBgDrawableId).setTint(chipBgColor))
                                .build())
                .setStartIconStyle(new ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
                .setTitleStyle(
                        new TextViewStyle.Builder()
                                .setTextColor(textColor)
                                .setTextSize(13)
                                .build())
                .setSubtitleStyle(
                        new TextViewStyle.Builder()
                                .setTextColor(hintTextColor)
                                .setTextSize(11)
                                .build())
                .setEndIconStyle(new ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
                .build();
        stylesBuilder.addStyle(style);
        final Bundle stylesBundle = stylesBuilder.build();

        final int height = ViewUtils.dpToPx(context, 38);
        final Size min = new Size(100, height);
        final Size max = new Size(740, height);

        final ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());

        return new InlineSuggestionsRequest.Builder(presentationSpecs)
                .setMaxSuggestionCount(6)
                .build();
    }

    public static InlineContentClipView createView(final List<InlineSuggestion> inlineSuggestions,
                                                   final Context context) {
        final LinearLayout container = new LinearLayout(context);
        final HorizontalScrollView inlineSuggestionView = new HorizontalScrollView(context);
        inlineSuggestionView.setHorizontalScrollBarEnabled(false);
        inlineSuggestionView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        inlineSuggestionView.addView(container);

        final InlineContentClipView scrollableSuggestionsClip = new InlineContentClipView(context);
        scrollableSuggestionsClip.addView(inlineSuggestionView);

        for (InlineSuggestion inlineSuggestion : inlineSuggestions) {
            inlineSuggestion.inflate(context, new Size(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT), context.getMainExecutor(), (view) -> {
                if (view != null) {
                    container.addView(view);
                    scrollableSuggestionsClip.postInvalidate();
                }
            });
        }

        return scrollableSuggestionsClip;
    }

    public static class InlineContentClipView extends FrameLayout {
        @NonNull
        private final ViewTreeObserver.OnDrawListener mOnDrawListener =
                this::clipDescendantInlineContentViews;
        @NonNull
        private final Rect mParentBounds = new Rect();
        @NonNull
        private final Rect mContentBounds = new Rect();
        public InlineContentClipView(@NonNull Context context) {
            this(context, null, 0);
        }
        public InlineContentClipView(@NonNull Context context, @Nullable AttributeSet attrs,
                                     @AttrRes int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            SurfaceView mBackgroundView = new SurfaceView(context);
            mBackgroundView.setZOrderOnTop(true);
            mBackgroundView.getHolder().setFormat(PixelFormat.TRANSPARENT);
            addView(mBackgroundView);
        }
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnDrawListener(mOnDrawListener);
        }
        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
        }
        private void clipDescendantInlineContentViews() {
            final int width = getWidth();
            final int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            mParentBounds.set(0, 0, width, height);
            clipDescendantInlineContentViews(this);
        }
        private void clipDescendantInlineContentViews(@Nullable View root) {
            if (root == null) return;
            if (root instanceof InlineContentView) {
                InlineContentView inlineContentView = (InlineContentView) root;
                mContentBounds.set(mParentBounds);
                offsetRectIntoDescendantCoords(inlineContentView, mContentBounds);
                inlineContentView.setClipBounds(mContentBounds);
                return;
            }
            if (root instanceof ViewGroup) {
                ViewGroup rootGroup = (ViewGroup) root;
                final int childCount = rootGroup.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = rootGroup.getChildAt(i);
                    clipDescendantInlineContentViews(child);
                }
            }
        }
    }
}
