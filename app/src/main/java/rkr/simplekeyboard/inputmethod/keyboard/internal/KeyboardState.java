/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2024 Raimondas Rimkus
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

import android.text.TextUtils;
import android.util.Log;

import rkr.simplekeyboard.inputmethod.event.Event;
import rkr.simplekeyboard.inputmethod.latin.common.Constants;
import rkr.simplekeyboard.inputmethod.latin.utils.CapsModeUtils;
import rkr.simplekeyboard.inputmethod.latin.utils.RecapitalizeStatus;

/**
 * Keyboard state machine.
 *
 * This class contains all keyboard state transition logic.
 *
 * The input events are {@link #onLoadKeyboard(int, int)},
 * {@link #onPressKey(int,boolean,int,int)}, {@link #onReleaseKey(int,boolean,int,int)},
 * {@link #onEvent(Event,int,int)}, {@link #onFinishSlidingInput(int,int)},
 * {@link #onUpdateShiftState(int,int)}, {@link #onResetKeyboardStateToAlphabet(int,int)}.
 *
 * The actions are {@link SwitchActions}'s methods.
 */
public final class KeyboardState {
    private static final String TAG = KeyboardState.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_INTERNAL_ACTION = false;

    public interface SwitchActions {
        boolean DEBUG_ACTION = false;

        void setAlphabetKeyboard();
        void setAlphabetManualShiftedKeyboard();
        void setAlphabetAutomaticShiftedKeyboard();
        void setAlphabetShiftLockedKeyboard();
        void setSymbolsKeyboard();
        void setSymbolsShiftedKeyboard();

        /**
         * Request to call back {@link KeyboardState#onUpdateShiftState(int, int)}.
         */
        void requestUpdatingShiftState(final int autoCapsFlags, final int recapitalizeMode);

        boolean DEBUG_TIMER_ACTION = false;

        void startDoubleTapShiftKeyTimer();
        boolean isInDoubleTapShiftKeyTimeout();
        void cancelDoubleTapShiftKeyTimer();
    }

    private final SwitchActions mSwitchActions;

    private ShiftKeyState mShiftKeyState = new ShiftKeyState("Shift");
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState("Symbol");

    // TODO: Merge {@link #mSwitchState}, {@link #mIsAlphabetMode}, {@link #mAlphabetShiftState},
    // {@link #mIsSymbolShifted}, {@link #mPrevMainKeyboardWasShiftLocked}, and
    // {@link #mPrevSymbolsKeyboardWasShifted} into single state variable.
    private static final int SWITCH_STATE_ALPHA = 0;
    private static final int SWITCH_STATE_SYMBOL_BEGIN = 1;
    private static final int SWITCH_STATE_SYMBOL = 2;
    private static final int SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL = 3;
    private static final int SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE = 4;
    private int mSwitchState = SWITCH_STATE_ALPHA;

    private boolean mIsAlphabetMode;
    private AlphabetShiftState mAlphabetShiftState = new AlphabetShiftState();
    private boolean mIsSymbolShifted;
    private boolean mPrevMainKeyboardWasShiftLocked;
    private boolean mPrevSymbolsKeyboardWasShifted;
    private int mRecapitalizeMode;

    // For handling double tap.
    private boolean mIsInAlphabetUnshiftedFromShifted;
    private boolean mIsInDoubleTapShiftKey;

    public KeyboardState(final SwitchActions switchActions) {
        mSwitchActions = switchActions;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
    }

    public void onLoadKeyboard(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onLoadKeyboard: " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mPrevMainKeyboardWasShiftLocked = false;
        mPrevSymbolsKeyboardWasShifted = false;
        mShiftKeyState.onRelease();
        mSymbolKeyState.onRelease();

        setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
    }

    // Constants for {@link SavedKeyboardState#mShiftMode} and {@link #setShifted(int)}.
    private static final int UNSHIFT = 0;
    private static final int MANUAL_SHIFT = 1;
    private static final int AUTOMATIC_SHIFT = 2;
    private static final int SHIFT_LOCK_SHIFTED = 3;

    private int getPrevShiftMode() {
        if (mAlphabetShiftState.isAutomaticShifted()) {
            return AUTOMATIC_SHIFT;
        }
        if (mAlphabetShiftState.isManualShifted()) {
            return MANUAL_SHIFT;
        }
        return UNSHIFT;
    }

    private void applyShiftModeState(final int shiftMode) {
        if (shiftMode == AUTOMATIC_SHIFT) {
            mAlphabetShiftState.setAutomaticShifted();
        } else if (shiftMode == UNSHIFT) {
            mAlphabetShiftState.setShifted(false);
        } else {
            mAlphabetShiftState.setShifted(true);
        }
    }

    private void updateShiftKeyboardAction(final int shiftMode) {
        if (shiftMode == AUTOMATIC_SHIFT) {
            mSwitchActions.setAlphabetAutomaticShiftedKeyboard();
        } else if (shiftMode == MANUAL_SHIFT) {
            mSwitchActions.setAlphabetManualShiftedKeyboard();
        } else if (shiftMode == UNSHIFT) {
            mSwitchActions.setAlphabetKeyboard();
        }
    }

    private void setShifted(final int shiftMode) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setShifted: shiftMode=" + shiftModeToString(shiftMode) + " " + this);
        }
        if (!mIsAlphabetMode) return;
        final int prevShiftMode = getPrevShiftMode();
        applyShiftModeState(shiftMode);
        if (shiftMode != SHIFT_LOCK_SHIFTED && shiftMode != prevShiftMode) {
            updateShiftKeyboardAction(shiftMode);
        }
    }

    private boolean needsShiftLockedAction() {
        return !mAlphabetShiftState.isShiftLocked() || mAlphabetShiftState.isShiftLockShifted();
    }

    private void updateShiftLockedKeyboardAction(final boolean shiftLocked) {
        if (shiftLocked) {
            if (needsShiftLockedAction()) {
                mSwitchActions.setAlphabetShiftLockedKeyboard();
            }
        } else if (mAlphabetShiftState.isShiftLocked()) {
            mSwitchActions.setAlphabetKeyboard();
        }
    }

    private void setShiftLocked(final boolean shiftLocked) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setShiftLocked: shiftLocked=" + shiftLocked + " " + this);
        }
        if (!mIsAlphabetMode) return;
        updateShiftLockedKeyboardAction(shiftLocked);
        mAlphabetShiftState.setShiftLocked(shiftLocked);
    }

    private void toggleAlphabetAndSymbols(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "toggleAlphabetAndSymbols: "
                    + stateToString(autoCapsFlags, recapitalizeMode));
        }
        if (mIsAlphabetMode) {
            mPrevMainKeyboardWasShiftLocked = mAlphabetShiftState.isShiftLocked();
            setSymbolsKeyboard(mPrevSymbolsKeyboardWasShifted);
            mPrevSymbolsKeyboardWasShifted = false;
        } else {
            mPrevSymbolsKeyboardWasShifted = mIsSymbolShifted;
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
            if (mPrevMainKeyboardWasShiftLocked) {
                setShiftLocked(true);
            }
            mPrevMainKeyboardWasShiftLocked = false;
        }
    }

    private void toggleShiftInSymbols() {
        setSymbolsKeyboard(!mIsSymbolShifted);
    }

    private void setAlphabetKeyboard(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard: " + stateToString(autoCapsFlags, recapitalizeMode));
        }

        mSwitchActions.setAlphabetKeyboard();
        mIsAlphabetMode = true;
        mIsSymbolShifted = false;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        mSwitchState = SWITCH_STATE_ALPHA;
        mSwitchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode);
    }

    private void setSymbolsKeyboard(final boolean isShifted) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, isShifted ? "setSymbolsShiftedKeyboard" : "setSymbolsKeyboard");
        }
        if (isShifted) {
            mSwitchActions.setSymbolsShiftedKeyboard();
        } else {
            mSwitchActions.setSymbolsKeyboard();
        }
        mIsAlphabetMode = false;
        mIsSymbolShifted = isShifted;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
    }

    private boolean isShiftStateResettableForAutoCaps() {
        if (mAlphabetShiftState.isAutomaticShifted()) {
            return !mShiftKeyState.isChording();
        }
        if (mAlphabetShiftState.isManualShifted()) {
            return mShiftKeyState.isReleasing();
        }
        return false;
    }

    private boolean shouldResetAutoCapsOnOtherKey(final boolean isSinglePointer,
            final int autoCapsFlags) {
        if (isSinglePointer || !mIsAlphabetMode || autoCapsFlags == TextUtils.CAP_MODE_CHARACTERS) {
            return false;
        }
        return isShiftStateResettableForAutoCaps();
    }

    private void onPressOtherKey(final boolean isSinglePointer, final int autoCapsFlags) {
        mShiftKeyState.onOtherKeyPressed();
        mSymbolKeyState.onOtherKeyPressed();
        if (shouldResetAutoCapsOnOtherKey(isSinglePointer, autoCapsFlags)) {
            mSwitchActions.setAlphabetKeyboard();
        }
    }

    private void handlePressKey(final int code, final boolean isSinglePointer,
            final int autoCapsFlags, final int recapitalizeMode) {
        if (code == Constants.CODE_SHIFT) {
            onPressShift();
        } else if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            onPressSymbol(autoCapsFlags, recapitalizeMode);
        } else if (code != Constants.CODE_CAPSLOCK) {
            onPressOtherKey(isSinglePointer, autoCapsFlags);
        }
    }

    public void onPressKey(final int code, final boolean isSinglePointer, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onPressKey: code=" + Constants.printableCode(code)
                    + " single=" + isSinglePointer
                    + " " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        if (code != Constants.CODE_SHIFT) {
            mSwitchActions.cancelDoubleTapShiftKeyTimer();
        }
        handlePressKey(code, isSinglePointer, autoCapsFlags, recapitalizeMode);
    }

    public void onReleaseKey(final int code, final boolean withSliding, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onReleaseKey: code=" + Constants.printableCode(code)
                    + " sliding=" + withSliding
                    + " " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        if (code == Constants.CODE_SHIFT) {
            onReleaseShift(withSliding, autoCapsFlags, recapitalizeMode);
        } else if (code == Constants.CODE_CAPSLOCK) {
            setShiftLocked(!mAlphabetShiftState.isShiftLocked());
        } else if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            onReleaseSymbol(withSliding, autoCapsFlags, recapitalizeMode);
        }
    }

    private void onPressSymbol(final int autoCapsFlags,
            final int recapitalizeMode) {
        toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
        mSymbolKeyState.onPress();
        mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL;
    }

    private void onReleaseSymbol(final boolean withSliding, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (mSymbolKeyState.isChording()) {
            // Switch back to the previous keyboard mode if the user chords the mode change key and
            // another key, then releases the mode change key.
            toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
        } else if (!withSliding) {
            // If the mode change key is being released without sliding, we should forget the
            // previous symbols keyboard shift state and simply switch back to symbols layout
            // (never symbols shifted) next time the mode gets changed to symbols layout.
            mPrevSymbolsKeyboardWasShifted = false;
        }
        mSymbolKeyState.onRelease();
    }

    public void onUpdateShiftState(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onUpdateShiftState: " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        mRecapitalizeMode = recapitalizeMode;
        updateAlphabetShiftState(autoCapsFlags, recapitalizeMode);
    }

    private void updateShiftStateForRecapitalize(final int recapitalizeMode) {
        switch (recapitalizeMode) {
        case RecapitalizeStatus.CAPS_MODE_ALL_UPPER:
            setShifted(SHIFT_LOCK_SHIFTED);
            break;
        case RecapitalizeStatus.CAPS_MODE_FIRST_WORD_UPPER:
            setShifted(AUTOMATIC_SHIFT);
            break;
        case RecapitalizeStatus.CAPS_MODE_ALL_LOWER:
        case RecapitalizeStatus.CAPS_MODE_ORIGINAL_MIXED_CASE:
        default:
            setShifted(UNSHIFT);
        }
    }

    private void updateNormalAlphabetShiftState(final int autoCapsFlags) {
        if (mAlphabetShiftState.isShiftLocked() || mShiftKeyState.isIgnoring()) {
            return;
        }
        if (autoCapsFlags != Constants.TextUtils.CAP_MODE_OFF) {
            setShifted(AUTOMATIC_SHIFT);
        } else {
            setShifted(mShiftKeyState.isChording() ? MANUAL_SHIFT : UNSHIFT);
        }
    }

    private void updateAlphabetShiftState(final int autoCapsFlags, final int recapitalizeMode) {
        if (!mIsAlphabetMode) return;
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != recapitalizeMode) {
            updateShiftStateForRecapitalize(recapitalizeMode);
            return;
        }
        if (!mShiftKeyState.isReleasing()) {
            return;
        }
        updateNormalAlphabetShiftState(autoCapsFlags);
    }

    private void onPressShiftInSymbolMode() {
        toggleShiftInSymbols();
        mSwitchState = SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
        mShiftKeyState.onPress();
    }

    private void handleDoubleTapShiftInAlphabetMode() {
        if (mAlphabetShiftState.isManualShifted() || mIsInAlphabetUnshiftedFromShifted) {
            setShiftLocked(true);
        }
    }

    private void handleSingleTapShiftInAlphabetMode() {
        if (mAlphabetShiftState.isShiftLocked()) {
            setShifted(SHIFT_LOCK_SHIFTED);
            mShiftKeyState.onPress();
        } else if (mAlphabetShiftState.isAutomaticShifted()) {
            mShiftKeyState.onPress();
        } else if (mAlphabetShiftState.isShiftedOrShiftLocked()) {
            mShiftKeyState.onPressOnShifted();
        } else {
            setShifted(MANUAL_SHIFT);
            mShiftKeyState.onPress();
        }
    }

    private void onPressShiftInAlphabetMode() {
        mIsInDoubleTapShiftKey = mSwitchActions.isInDoubleTapShiftKeyTimeout();
        if (!mIsInDoubleTapShiftKey) {
            mSwitchActions.startDoubleTapShiftKeyTimer();
            handleSingleTapShiftInAlphabetMode();
        } else {
            handleDoubleTapShiftInAlphabetMode();
        }
    }

    private void onPressShift() {
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != mRecapitalizeMode) {
            return;
        }
        if (mIsAlphabetMode) {
            onPressShiftInAlphabetMode();
        } else {
            onPressShiftInSymbolMode();
        }
    }

    private void handleReleaseShiftChordingInAlphabetMode(final int autoCapsFlags,
            final int recapitalizeMode) {
        if (mAlphabetShiftState.isShiftLockShifted()) {
            setShiftLocked(true);
        } else {
            setShifted(UNSHIFT);
        }
        mShiftKeyState.onRelease();
        mSwitchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode);
    }

    private boolean isShiftLockedLongPressed() {
        return !mAlphabetShiftState.isShiftLockShifted()
                && (mShiftKeyState.isPressing() || mShiftKeyState.isPressingOnShifted());
    }

    private void handleReleaseShiftLockedInAlphabetMode() {
        if (isShiftLockedLongPressed()) {
            return;
        }
        if (!mShiftKeyState.isIgnoring()) {
            setShiftLocked(false);
        }
    }

    private boolean shouldUnshiftFromShiftedPress() {
        if (mAlphabetShiftState.isShiftedOrShiftLocked() && mShiftKeyState.isPressingOnShifted()) {
            return true;
        }
        return mAlphabetShiftState.isAutomaticShifted() && mShiftKeyState.isPressing();
    }

    private void handleReleaseShiftNonChordingInAlphabetMode(final boolean withSliding) {
        if (withSliding) {
            return;
        }
        if (mAlphabetShiftState.isShiftLocked()) {
            handleReleaseShiftLockedInAlphabetMode();
        } else if (shouldUnshiftFromShiftedPress()) {
            setShifted(UNSHIFT);
            mIsInAlphabetUnshiftedFromShifted = true;
        }
    }

    private boolean handleReleaseShiftInAlphabetMode(final boolean withSliding,
            final int autoCapsFlags, final int recapitalizeMode) {
        mIsInAlphabetUnshiftedFromShifted = false;
        if (mIsInDoubleTapShiftKey) {
            mIsInDoubleTapShiftKey = false;
            return false;
        }
        if (mShiftKeyState.isChording()) {
            handleReleaseShiftChordingInAlphabetMode(autoCapsFlags, recapitalizeMode);
            return true;
        }
        handleReleaseShiftNonChordingInAlphabetMode(withSliding);
        return false;
    }

    private void onReleaseShift(final boolean withSliding, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != mRecapitalizeMode) {
            updateShiftStateForRecapitalize(mRecapitalizeMode);
        } else if (mIsAlphabetMode) {
            if (handleReleaseShiftInAlphabetMode(withSliding, autoCapsFlags, recapitalizeMode)) {
                return;
            }
        } else if (mShiftKeyState.isChording()) {
            toggleShiftInSymbols();
        }
        mShiftKeyState.onRelease();
    }

    public void onFinishSlidingInput(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onFinishSlidingInput: " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        // Switch back to the previous keyboard mode if the user cancels sliding input.
        switch (mSwitchState) {
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL:
            toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
            break;
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE:
            toggleShiftInSymbols();
            break;
        }
    }

    private static boolean isSpaceOrEnter(final int c) {
        return c == Constants.CODE_SPACE || c == Constants.CODE_ENTER;
    }

    private void handleMomentaryAlphaSymbolEvent(final int code) {
        if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            mSwitchState = mIsAlphabetMode ? SWITCH_STATE_ALPHA : SWITCH_STATE_SYMBOL_BEGIN;
        }
    }

    private void handleMomentarySymbolMoreEvent(final int code) {
        if (code == Constants.CODE_SHIFT) {
            mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
        }
    }

    private void handleSymbolBeginEvent(final int code) {
        if (!isSpaceOrEnter(code) && (Constants.isLetterCode(code) || code == Constants.CODE_OUTPUT_TEXT)) {
            mSwitchState = SWITCH_STATE_SYMBOL;
        }
    }

    private void handleSymbolEvent(final int code, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (isSpaceOrEnter(code)) {
            toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
            mPrevSymbolsKeyboardWasShifted = false;
        }
    }

    private void updateSwitchState(final int code, final int autoCapsFlags,
            final int recapitalizeMode) {
        switch (mSwitchState) {
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL:
            handleMomentaryAlphaSymbolEvent(code);
            break;
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE:
            handleMomentarySymbolMoreEvent(code);
            break;
        case SWITCH_STATE_SYMBOL_BEGIN:
            handleSymbolBeginEvent(code);
            break;
        case SWITCH_STATE_SYMBOL:
            handleSymbolEvent(code, autoCapsFlags, recapitalizeMode);
            break;
        }
    }

    public void onEvent(final Event event, final int autoCapsFlags, final int recapitalizeMode) {
        final int code = event.isFunctionalKeyEvent() ? event.mKeyCode : event.mCodePoint;
        if (DEBUG_EVENT) {
            Log.d(TAG, "onEvent: code=" + Constants.printableCode(code)
                    + " " + stateToString(autoCapsFlags, recapitalizeMode));
        }

        updateSwitchState(code, autoCapsFlags, recapitalizeMode);

        if (Constants.isLetterCode(code)) {
            updateAlphabetShiftState(autoCapsFlags, recapitalizeMode);
        }
    }

    private static final String[] SHIFT_MODE_NAMES = {
        "UNSHIFT",
        "MANUAL",
        "AUTOMATIC"
    };

    static String shiftModeToString(final int shiftMode) {
        if (shiftMode >= 0 && shiftMode < SHIFT_MODE_NAMES.length) {
            return SHIFT_MODE_NAMES[shiftMode];
        }
        return null;
    }

    private static final String[] SWITCH_STATE_NAMES = {
        "ALPHA",
        "SYMBOL-BEGIN",
        "SYMBOL",
        "MOMENTARY-ALPHA-SYMBOL",
        "MOMENTARY-SYMBOL-MORE"
    };

    private static String switchStateToString(final int switchState) {
        if (switchState >= 0 && switchState < SWITCH_STATE_NAMES.length) {
            return SWITCH_STATE_NAMES[switchState];
        }
        return null;
    }

    @Override
    public String toString() {
        return "[keyboard=" + (mIsAlphabetMode ? mAlphabetShiftState.toString()
                : (mIsSymbolShifted ? "SYMBOLS_SHIFTED" : "SYMBOLS"))
                + " shift=" + mShiftKeyState
                + " symbol=" + mSymbolKeyState
                + " switch=" + switchStateToString(mSwitchState) + "]";
    }

    private String stateToString(final int autoCapsFlags, final int recapitalizeMode) {
        return this + " autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags)
                + " recapitalizeMode=" + RecapitalizeStatus.modeToString(recapitalizeMode);
    }
}
