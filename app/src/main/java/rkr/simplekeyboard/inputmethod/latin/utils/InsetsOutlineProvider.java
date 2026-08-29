/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0
 */
package rkr.simplekeyboard.inputmethod.latin.utils;

import android.graphics.Outline;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewOutlineProvider;

public class InsetsOutlineProvider extends ViewOutlineProvider {
    private static final int NO_DATA = -1;
    private final View mView;
    private int mLastVisibleTopInsets = NO_DATA;

    public InsetsOutlineProvider(View view) {
        mView = view;
        mView.setOutlineProvider(this);
    }

    public void setInsets(InputMethodService.Insets insets) {
        if (insets == null) return;
        final int visibleTopInsets = insets.visibleTopInsets;
        if (mLastVisibleTopInsets != visibleTopInsets) {
            mLastVisibleTopInsets = visibleTopInsets;
            mView.invalidateOutline();
        }
    }

    @Override
    public void getOutline(View view, Outline outline) {
        if (mLastVisibleTopInsets == NO_DATA) {
            BACKGROUND.getOutline(view, outline);
            return;
        }
        outline.setRect(view.getLeft(), mLastVisibleTopInsets, view.getRight(), view.getBottom());
    }
}
