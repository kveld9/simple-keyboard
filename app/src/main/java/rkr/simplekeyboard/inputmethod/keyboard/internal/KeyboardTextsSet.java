/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2021 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
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

package rkr.simplekeyboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import java.util.Locale;

import rkr.simplekeyboard.inputmethod.latin.common.Constants;

// TODO: Make this an immutable class.
public final class KeyboardTextsSet {
    public static final String PREFIX_TEXT = "!text/";
    private static final String PREFIX_RESOURCE = "!string/";

    private static final char BACKSLASH = Constants.CODE_BACKSLASH;
    private static final int MAX_REFERENCE_INDIRECTION = 10;

    private Resources mResources;
    private String mResourcePackageName;
    private String[] mTextsTable;

    public void setLocale(final Locale locale, final Context context) {
        final Resources res = context.getResources();
        // Null means the current system locale.
        final String resourcePackageName = res.getResourcePackageName(
                context.getApplicationInfo().labelRes);
        setLocale(locale, res, resourcePackageName);
    }

    public void setLocale(final Locale locale, final Resources res,
            final String resourcePackageName) {
        mResources = res;
        // Null means the current system locale.
        mResourcePackageName = resourcePackageName;
        mTextsTable = KeyboardTextsTable.getTextsTable(locale);
    }

    public String getText(final String name) {
        return KeyboardTextsTable.getText(name, mTextsTable);
    }

    private static boolean isLowerAlpha(final char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isValidTextNameChar(final char c) {
        return c == '_' || isLowerAlpha(c) || isDigit(c);
    }

    private static int searchTextNameEnd(final String text, final int start) {
        final int size = text.length();
        for (int pos = start; pos < size; pos++) {
            if (!isValidTextNameChar(text.charAt(pos))) {
                return pos;
            }
        }
        return size;
    }

    private static void checkIndirectionLimit(final int level, final String text) {
        if (level >= MAX_REFERENCE_INDIRECTION) {
            throw new RuntimeException("Too many " + PREFIX_TEXT + " or " + PREFIX_RESOURCE +
                    " reference indirection: " + text);
        }
    }

    private static int findFirstReference(final String text) {
        final int size = text.length();
        for (int pos = 0; pos < size; pos++) {
            if (text.startsWith(PREFIX_TEXT, pos) || text.startsWith(PREFIX_RESOURCE, pos)) {
                return pos;
            }
        }
        return -1;
    }

    private static void appendNonReferenceChar(final String text, final int pos, final int size,
            final StringBuilder sb) {
        if (text.charAt(pos) == BACKSLASH) {
            sb.append(text.substring(pos, Math.min(pos + 2, size)));
        } else {
            sb.append(text.charAt(pos));
        }
    }

    private int expandAtPos(final String text, final int pos, final StringBuilder sb) {
        if (text.startsWith(PREFIX_TEXT, pos)) {
            return expandReference(text, pos, PREFIX_TEXT, sb);
        }
        if (text.startsWith(PREFIX_RESOURCE, pos)) {
            return expandReference(text, pos, PREFIX_RESOURCE, sb);
        }
        return -1;
    }

    private void expandRemainder(final String text, final int startPos, final StringBuilder sb) {
        final int size = text.length();
        for (int pos = startPos; pos < size; pos++) {
            final int nextPos = expandAtPos(text, pos, sb);
            if (nextPos >= 0) {
                pos = nextPos;
            } else {
                appendNonReferenceChar(text, pos, size, sb);
                if (text.charAt(pos) == BACKSLASH) {
                    pos++;
                }
            }
        }
    }

    private String expandPass(final String text) {
        if (text.length() < PREFIX_TEXT.length()) {
            return null;
        }
        final int firstRef = findFirstReference(text);
        if (firstRef < 0) {
            return null;
        }
        final StringBuilder sb = new StringBuilder(text.substring(0, firstRef));
        expandRemainder(text, firstRef, sb);
        return sb.toString();
    }

    // TODO: Resolve text reference when creating {@link KeyboardTextsTable} class.
    public String resolveTextReference(final String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            return null;
        }
        int level = 0;
        String text = rawText;
        while (true) {
            checkIndirectionLimit(++level, text);
            final String expanded = expandPass(text);
            if (expanded == null) {
                break;
            }
            text = expanded;
        }
        return TextUtils.isEmpty(text) ? null : text;
    }

    private int expandReference(final String text, final int pos, final String prefix,
            final StringBuilder sb) {
        final int prefixLength = prefix.length();
        final int end = searchTextNameEnd(text, pos + prefixLength);
        final String name = text.substring(pos + prefixLength, end);
        if (prefix.equals(PREFIX_TEXT)) {
            sb.append(getText(name));
        } else { // PREFIX_RESOURCE
            final int resId = mResources.getIdentifier(name, "string", mResourcePackageName);
            sb.append(mResources.getString(resId));
        }
        return end - 1;
    }
}
