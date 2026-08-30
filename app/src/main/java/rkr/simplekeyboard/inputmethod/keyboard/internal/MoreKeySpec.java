/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2020 wittmane
 * Copyright (C) 2019 Raimondas Rimkus
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

import android.text.TextUtils;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

import rkr.simplekeyboard.inputmethod.keyboard.Key;
import rkr.simplekeyboard.inputmethod.latin.common.CollectionUtils;
import rkr.simplekeyboard.inputmethod.latin.common.Constants;
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils;

/**
 * The more key specification object. The more keys are an array of {@link MoreKeySpec}.
 *
 * The more keys specification is comma separated "key specification" each of which represents one
 * "more key".
 * The key specification might have label or string resource reference in it. These references are
 * expanded before parsing comma.
 * Special character, comma ',' backslash '\' can be escaped by '\' character.
 * Note that the '\' is also parsed by XML parser and {@link MoreKeySpec#splitKeySpecs(String)}
 * as well.
 */
// TODO: Should extend the key specification object.
public final class MoreKeySpec {
    public final int mCode;
    public final String mLabel;
    public final String mOutputText;
    public final int mIconId;

    private static String resolveLabel(final String moreKeySpec, final boolean needsToUpperCase,
            final Locale locale) {
        final String label = KeySpecParser.getLabel(moreKeySpec);
        return needsToUpperCase ? StringUtils.toTitleCaseOfKeyLabel(label, locale) : label;
    }

    private static int resolveCode(final String moreKeySpec, final boolean needsToUpperCase,
            final Locale locale) {
        final int codeInSpec = KeySpecParser.getCode(moreKeySpec);
        return needsToUpperCase ? StringUtils.toTitleCaseOfKeyCode(codeInSpec, locale) : codeInSpec;
    }

    private static String resolveOutputText(final String moreKeySpec, final int code,
            final String label, final boolean needsToUpperCase, final Locale locale) {
        if (code == Constants.CODE_UNSPECIFIED) {
            return label;
        }
        final String outputText = KeySpecParser.getOutputText(moreKeySpec);
        return needsToUpperCase ? StringUtils.toTitleCaseOfKeyLabel(outputText, locale) : outputText;
    }

    public MoreKeySpec(final String moreKeySpec, boolean needsToUpperCase,
            final Locale locale) {
        if (moreKeySpec.isEmpty()) {
            throw new KeySpecParser.KeySpecParserError("Empty more key spec");
        }
        mLabel = resolveLabel(moreKeySpec, needsToUpperCase, locale);
        final int code = resolveCode(moreKeySpec, needsToUpperCase, locale);
        mCode = (code == Constants.CODE_UNSPECIFIED) ? Constants.CODE_OUTPUT_TEXT : code;
        mOutputText = resolveOutputText(moreKeySpec, code, mLabel, needsToUpperCase, locale);
        mIconId = KeySpecParser.getIconId(moreKeySpec);
    }

    public Key buildKey(final float x, final float y, final float width, final float height,
                        final float leftPadding, final float rightPadding, final float topPadding,
                        final float bottomPadding, final int labelFlags) {
        return new Key(mLabel, mIconId, mCode, mOutputText, null /* hintLabel */, labelFlags,
                Key.BACKGROUND_TYPE_NORMAL, x, y, width, height, leftPadding, rightPadding,
                topPadding, bottomPadding);
    }

    @Override
    public int hashCode() {
        int hashCode = 31 + mCode;
        hashCode = hashCode * 31 + mIconId;
        final String label = mLabel;
        hashCode = hashCode * 31 + (label == null ? 0 : label.hashCode());
        final String outputText = mOutputText;
        hashCode = hashCode * 31 + (outputText == null ? 0 : outputText.hashCode());
        return hashCode;
    }

    private boolean hasSameCodes(final MoreKeySpec other) {
        return mCode == other.mCode && mIconId == other.mIconId;
    }

    private boolean hasSameTexts(final MoreKeySpec other) {
        return TextUtils.equals(mLabel, other.mLabel)
                && TextUtils.equals(mOutputText, other.mOutputText);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MoreKeySpec)) {
            return false;
        }
        final MoreKeySpec other = (MoreKeySpec)o;
        return hasSameCodes(other) && hasSameTexts(other);
    }

    @Override
    public String toString() {
        final String label = (mIconId == KeyboardIconsSet.ICON_UNDEFINED ? mLabel
                : KeyboardIconsSet.PREFIX_ICON + KeyboardIconsSet.getIconName(mIconId));
        final String output = (mCode == Constants.CODE_OUTPUT_TEXT ? mOutputText
                : Constants.printableCode(mCode));
        if (StringUtils.getSingleCodePoint(label, Constants.CODE_UNSPECIFIED) == mCode) {
            return output;
        }
        return label + "|" + output;
    }

    public static class LettersOnBaseLayout {
        private final SparseIntArray mCodes = new SparseIntArray();
        private final HashSet<String> mTexts = new HashSet<>();

        public void addLetter(final Key key) {
            final int code = key.getCode();
            if (Character.isAlphabetic(code)) {
                mCodes.put(code, 0);
            } else if (code == Constants.CODE_OUTPUT_TEXT) {
                mTexts.add(key.getOutputText());
            }
        }

        public boolean contains(final MoreKeySpec moreKey) {
            final int code = moreKey.mCode;
            if (Character.isAlphabetic(code)) {
                return mCodes.indexOfKey(code) >= 0;
            }
            if (code == Constants.CODE_OUTPUT_TEXT) {
                return mTexts.contains(moreKey.mOutputText);
            }
            return false;
        }
    }

    private static ArrayList<MoreKeySpec> filterRedundantKeys(final MoreKeySpec[] moreKeys,
            final LettersOnBaseLayout lettersOnBaseLayout) {
        final ArrayList<MoreKeySpec> filtered = new ArrayList<>();
        for (final MoreKeySpec moreKey : moreKeys) {
            if (!lettersOnBaseLayout.contains(moreKey)) {
                filtered.add(moreKey);
            }
        }
        return filtered;
    }

    public static MoreKeySpec[] removeRedundantMoreKeys(final MoreKeySpec[] moreKeys,
            final LettersOnBaseLayout lettersOnBaseLayout) {
        if (moreKeys == null) {
            return null;
        }
        final ArrayList<MoreKeySpec> filtered = filterRedundantKeys(moreKeys, lettersOnBaseLayout);
        final int size = filtered.size();
        if (size == moreKeys.length) {
            return moreKeys;
        }
        return size == 0 ? null : filtered.toArray(new MoreKeySpec[size]);
    }

    // Constants for parsing.
    private static final char COMMA = Constants.CODE_COMMA;
    private static final char BACKSLASH = Constants.CODE_BACKSLASH;
    private static final String ADDITIONAL_MORE_KEY_MARKER =
            StringUtils.newSingleCodePointString(Constants.CODE_PERCENT);

    private static String[] handleSingleCharKeySpec(final String text) {
        return text.charAt(0) == COMMA ? null : new String[] { text };
    }

    private static ArrayList<String> addKeySpecToken(ArrayList<String> list, final String text,
            final int start, final int end) {
        if (end > start) {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(text.substring(start, end));
        }
        return list;
    }

    private static ArrayList<String> appendRemain(ArrayList<String> list, final String remain) {
        if (remain != null) {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(remain);
        }
        return list;
    }

    private static ArrayList<String> tokenizeKeySpecs(final String text, final int size) {
        ArrayList<String> list = null;
        int start = 0;
        for (int pos = 0; pos < size; pos++) {
            final char c = text.charAt(pos);
            if (c == COMMA) {
                list = addKeySpecToken(list, text, start, pos);
                start = pos + 1;
            } else if (c == BACKSLASH) {
                pos++;
            }
        }
        final String remain = (size > start) ? text.substring(start) : null;
        return appendRemain(list, remain);
    }

    /**
     * Split the text containing multiple key specifications separated by commas into an array of
     * key specifications.
     * A key specification can contain a character escaped by the backslash character, including a
     * comma character.
     * Note that an empty key specification will be eliminated from the result array.
     *
     * @param text the text containing multiple key specifications.
     * @return an array of key specification text. Null if the specified <code>text</code> is empty
     * or has no key specifications.
     */
    public static String[] splitKeySpecs(final String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        if (text.length() == 1) {
            return handleSingleCharKeySpec(text);
        }
        final ArrayList<String> list = tokenizeKeySpecs(text, text.length());
        return list != null ? list.toArray(new String[list.size()]) : null;
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static ArrayList<String> collectNonEmptyStrings(final String[] array) {
        ArrayList<String> out = null;
        for (int i = 0; i < array.length; i++) {
            final String entry = array[i];
            if (TextUtils.isEmpty(entry)) {
                if (out == null) {
                    out = CollectionUtils.arrayAsList(array, 0, i);
                }
            } else if (out != null) {
                out.add(entry);
            }
        }
        return out;
    }

    private static String[] filterOutEmptyString(final String[] array) {
        if (array == null) {
            return EMPTY_STRING_ARRAY;
        }
        final ArrayList<String> out = collectNonEmptyStrings(array);
        return out == null ? array : out.toArray(new String[out.size()]);
    }

    private static int countMarkers(final String[] moreKeys) {
        int count = 0;
        for (final String key : moreKeys) {
            if (ADDITIONAL_MORE_KEY_MARKER.equals(key)) {
                count++;
            }
        }
        return count;
    }

    private static ArrayList<String> processMarkerKey(final String[] moreKeys, final int index,
            final String[] additionalMoreKeys, final int additionalIndex,
            ArrayList<String> out) {
        if (additionalIndex < additionalMoreKeys.length) {
            final String additionalKey = additionalMoreKeys[additionalIndex];
            if (out != null) {
                out.add(additionalKey);
            } else {
                moreKeys[index] = additionalKey;
            }
        } else if (out == null) {
            out = CollectionUtils.arrayAsList(moreKeys, 0, index);
        }
        return out;
    }

    private static void processNonMarkerKey(final String key, final ArrayList<String> out) {
        if (out != null) {
            out.add(key);
        }
    }

    private static ArrayList<String> substituteMarkers(final String[] moreKeys,
            final String[] additionalMoreKeys) {
        ArrayList<String> out = null;
        int additionalIndex = 0;
        for (int i = 0; i < moreKeys.length; i++) {
            final String key = moreKeys[i];
            if (ADDITIONAL_MORE_KEY_MARKER.equals(key)) {
                out = processMarkerKey(moreKeys, i, additionalMoreKeys, additionalIndex, out);
                if (additionalIndex < additionalMoreKeys.length) {
                    additionalIndex++;
                }
            } else {
                processNonMarkerKey(key, out);
            }
        }
        return out;
    }

    private static ArrayList<String> prependAdditionalKeys(final String[] additionalMoreKeys,
            final String[] moreKeys) {
        final ArrayList<String> out = CollectionUtils.arrayAsList(additionalMoreKeys, 0,
                additionalMoreKeys.length);
        for (final String moreKey : moreKeys) {
            out.add(moreKey);
        }
        return out;
    }

    private static ArrayList<String> appendRemainingKeys(final String[] moreKeys,
            final String[] additionalMoreKeys, final int fromIndex) {
        final ArrayList<String> out = CollectionUtils.arrayAsList(moreKeys, 0, moreKeys.length);
        for (int i = fromIndex; i < additionalMoreKeys.length; i++) {
            out.add(additionalMoreKeys[i]);
        }
        return out;
    }

    private static ArrayList<String> combineUnmatchedKeys(final String[] moreKeys,
            final String[] additionalMoreKeys, final int additionalIndex,
            final ArrayList<String> currentOut) {
        final int additionalCount = additionalMoreKeys.length;
        if (additionalCount > 0 && additionalIndex == 0) {
            return prependAdditionalKeys(additionalMoreKeys, moreKeys);
        }
        if (additionalIndex < additionalCount) {
            return appendRemainingKeys(moreKeys, additionalMoreKeys, additionalIndex);
        }
        return currentOut;
    }

    private static String[] toMoreKeysResult(final String[] moreKeys, final ArrayList<String> out) {
        if (out == null && moreKeys.length > 0) {
            return moreKeys;
        }
        if (out != null && !out.isEmpty()) {
            return out.toArray(new String[out.size()]);
        }
        return null;
    }

    public static String[] insertAdditionalMoreKeys(final String[] moreKeySpecs,
            final String[] additionalMoreKeySpecs) {
        final String[] moreKeys = filterOutEmptyString(moreKeySpecs);
        final String[] additionalMoreKeys = filterOutEmptyString(additionalMoreKeySpecs);
        final int markersCount = countMarkers(moreKeys);
        final int additionalIndex = Math.min(markersCount, additionalMoreKeys.length);
        final ArrayList<String> out = substituteMarkers(moreKeys, additionalMoreKeys);
        final ArrayList<String> finalOut = combineUnmatchedKeys(moreKeys, additionalMoreKeys,
                additionalIndex, out);
        return toMoreKeysResult(moreKeys, finalOut);
    }

    private static boolean isKeySpecMatching(final String moreKeySpec, final String prefix) {
        return moreKeySpec != null && moreKeySpec.startsWith(prefix);
    }

    private static int parseValueFromSpec(final String moreKeySpec, final int keyLen,
            final String key) {
        try {
            return Integer.parseInt(moreKeySpec.substring(keyLen));
        } catch (NumberFormatException e) {
            throw new RuntimeException("integer should follow after " + key + ": " + moreKeySpec);
        }
    }

    public static int getIntValue(final String[] moreKeys, final String key,
            final int defaultValue) {
        if (moreKeys == null) {
            return defaultValue;
        }
        final int keyLen = key.length();
        boolean foundValue = false;
        int value = defaultValue;
        for (int i = 0; i < moreKeys.length; i++) {
            final String moreKeySpec = moreKeys[i];
            if (isKeySpecMatching(moreKeySpec, key)) {
                moreKeys[i] = null;
                if (!foundValue) {
                    value = parseValueFromSpec(moreKeySpec, keyLen, key);
                    foundValue = true;
                }
            }
        }
        return value;
    }

    public static boolean getBooleanValue(final String[] moreKeys, final String key) {
        if (moreKeys == null) {
            return false;
        }
        boolean value = false;
        for (int i = 0; i < moreKeys.length; i++) {
            if (key.equals(moreKeys[i])) {
                moreKeys[i] = null;
                value = true;
            }
        }
        return value;
    }
}
