package rkr.simplekeyboard.inputmethod.latin.dict.spatial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rkr.simplekeyboard.inputmethod.keyboard.Key;
import rkr.simplekeyboard.inputmethod.keyboard.Keyboard;

public class SpatialTouchModel {

    private static class SpatialKey {
        final char codePoint;
        final float centerX;
        final float centerY;
        final float width;
        final float height;

        SpatialKey(char codePoint, float centerX, float centerY, float width, float height) {
            this.codePoint = codePoint;
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
        }
    }

    private final List<SpatialKey> mKeys = new ArrayList<>();

    public SpatialTouchModel() {
    }

    public void setKeyboard(Keyboard keyboard) {
        mKeys.clear();
        if (keyboard == null) {
            return;
        }
        for (Key key : keyboard.getSortedKeys()) {
            int code = key.getCode();
            // Only consider alphabetic keys
            if (Character.isLetter(code)) {
                mKeys.add(new SpatialKey((char) code, key.getCenterX(), key.getCenterY(), key.getWidth(), key.getHeight()));
            }
        }
    }

    public void addKey(char codePoint, float centerX, float centerY, float width, float height) {
        mKeys.add(new SpatialKey(codePoint, centerX, centerY, width, height));
    }

    public List<SpatialCandidate> getCandidatesForTouch(float touchX, float touchY, int fallbackCode) {
        List<SpatialCandidate> candidates = new ArrayList<>();

        for (SpatialKey key : mKeys) {
            float sigmaX = key.width * 0.40f;
            float sigmaY = key.height * 0.45f;
            
            if (sigmaX <= 0 || sigmaY <= 0) continue;

            float dx = touchX - key.centerX;
            float dy = touchY - key.centerY;

            float expPart = -((dx * dx) / (2 * sigmaX * sigmaX) + (dy * dy) / (2 * sigmaY * sigmaY));
            float prob = (float) Math.exp(expPart);

            if (prob > 0.05f) {
                candidates.add(new SpatialCandidate(key.codePoint, prob, expPart));
            }
        }

        // If no candidate met the threshold, try to find the fallback
        if (candidates.isEmpty() && fallbackCode > 0 && Character.isLetter(fallbackCode)) {
            candidates.add(new SpatialCandidate((char) fallbackCode, 1.0f, 0.0f));
        }

        Collections.sort(candidates);
        return candidates;
    }
}
