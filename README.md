# Simple Keyboard

<img src="images/screenshot-0.png" alt="closeup" width="500"/>
      
## About

Simple Keyboard is a highly optimized, lightweight, privacy-focused keyboard based on the AOSP LatinIME. Designed for performance and battery efficiency, it features a small footprint (~2MB) and a native zero-allocation inference engine without sacrificing usability or advanced typing features.

### Key Features

**Performance & Neural Core**
- **Ultra-lightweight:** Small footprint (around 2MB).
- **TRF2 BitNet 1.58-bit Ternary Neural Inference Engine:** State-of-the-art native C++ text prediction model integration.
- **Zero-allocation Tokenization & SIMD Unrolling:** Ensures no Garbage Collection pauses (GC) on hot paths, providing a buttery-smooth typing experience.
- **Golden Parity Tests:** Hardened inference engine with 100% loss parity with PyTorch training.

**Typing & Gestures**
- **Space Swipe:** Move the cursor seamlessly by swiping on the spacebar.
- **Delete Swipe:** Quickly delete text by swiping on the backspace key.
- **Customizable Swipe Sensitivity.**
- **Number Row:** Optional independent number row for faster typing.
- **Auto-Correction & Text Prediction:** Highly configurable auto-correction, auto-capitalization, and double-space period.

**Clipboard & Suggestions**
- **Smart Clipboard History:** SQLite-backed clipboard to pin, search, and manage copied text.
- **Image & Screenshot Suggestions:** Quickly paste recently taken screenshots or images directly from the top bar.

**Customization & Dictionary Management**
- **Custom Themes & Material You:** Supports Material You dynamic colors, Material Light, Material Dark, AMOLED Black, and System Default.
- **Adjustable Dimensions:** Tweak keyboard height and bottom offset (including independent landscape offset) for maximum screen space.
- **Emoji Picker:** Fast, integrated emoji selection.
- **External Dictionaries:** Import custom `.dict` or `.bin` language dictionaries and custom TRF2 neural models.
- **Backup & Restore:** Export and restore all your preferences and personal dictionary words to a JSON file.

### Privacy & Permissions

Simple Keyboard does not have internet access and respects your privacy. We don't store or transmit your data. Period.

**Requested Permissions:**
- `VIBRATE`: Required for keypress haptic feedback.
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`: Optional. Used exclusively for clipboard recent image and screenshot suggestions.

### What it doesn't have (and probably will never have)
To maintain its zero-allocation architecture, minimal footprint, and strict privacy, we omit:
- GIFs
- Swipe typing
- Cloud syncing or telemetry
- Ads

## Downloads

Download the latest APK release from [GitHub Releases](https://github.com/soyelmismo/simple-keyboard/releases/latest).

## Build Instructions

To build the project from source, simply run the Gradle wrapper:

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```
*Note: Make sure not to modify the core neural files without explicit approval, as it may break inference parity or cause VRAM crashes.*

## Maintainers & Authors
- **[kveld9](https://github.com/kveld9)**
- **[soyelmismo](https://github.com/soyelmismo)**

## Credits

Licensed under the **Apache License Version 2.0**.

This keyboard is originally based on the AOSP LatinIME keyboard. You can get the original source code here: [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/).
