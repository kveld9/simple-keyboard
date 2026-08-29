/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2018 Raimondas Rimkus
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

package rkr.simplekeyboard.inputmethod.latin;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public final class InputView extends FrameLayout {
    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    private static FrameLayout.LayoutParams ensureBottomGravity(final ViewGroup.LayoutParams params) {
        if (params instanceof FrameLayout.LayoutParams) {
            final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) params;
            flp.gravity = Gravity.BOTTOM;
            return flp;
        }
        if (params != null) {
            final FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(params);
            flp.gravity = Gravity.BOTTOM;
            return flp;
        }
        return null;
    }

    @Override
    public void addView(final View child, final int index, final ViewGroup.LayoutParams params) {
        final FrameLayout.LayoutParams flp = ensureBottomGravity(params);
        super.addView(child, index, flp != null ? flp : params);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            final FrameLayout.LayoutParams flp = ensureBottomGravity(lp);
            if (flp != null && flp != lp) {
                child.setLayoutParams(flp);
            }
        }
    }
}
