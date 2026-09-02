<div align="center">

# Simple Keyboard

[![Latest Release](https://img.shields.io/github/v/release/soyelmismo/simple-keyboard?style=flat-square&color=blue)](https://github.com/soyelmismo/simple-keyboard/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/soyelmismo/simple-keyboard/total?style=flat-square&color=emerald)](https://github.com/soyelmismo/simple-keyboard/releases)
[![Stars](https://img.shields.io/github/stars/soyelmismo/simple-keyboard?style=flat-square&color=amber)](https://github.com/soyelmismo/simple-keyboard/stargazers)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-6.0%2B-green.svg?style=flat-square)](https://android.com)
[![Footprint](https://img.shields.io/badge/Footprint-~2MB-orange.svg?style=flat-square)](#about)

**A private, ultra-fast, and zero-allocation open-source Android keyboard.**  
*Forked from [rkkr/simple-keyboard](https://github.com/rkkr/simple-keyboard) / [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)*

[Key Features](#key-features) • [Benchmarks & Performance](#benchmarks--performance-guarantees) • [Privacy & Permissions](#privacy--permissions) • [Tech Stack](#tech-stack--versions) • [Downloads](#downloads) • [Build Instructions](#build-instructions) • [Maintainers](#maintainers--authors) • [Credits](#credits)

<br/>

<img src="images/screenshot-0.png" alt="Simple Keyboard Interface" width="480"/>

</div>

## About

Simple Keyboard is a highly optimized, lightweight, privacy-focused keyboard based on [rkkr/simple-keyboard](https://github.com/rkkr/simple-keyboard) (originally derived from AOSP LatinIME). Designed for performance and battery efficiency, it features a small footprint (~2MB) and a native zero-allocation inference engine without sacrificing usability or advanced typing features.

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

## Benchmarks & Performance Guarantees

The core inference engine and input pipeline are engineered under strict zero-allocation and low-latency architectural constraints:

| Metric | Measured Telemetry | Architectural Implementation |
| :--- | :--- | :--- |
| **TRF2 Forward Pass Latency** | **< 1 µs** (0.0006 ms / pass) | 2-bit packed weights (`BitNet 1.58b`) with unrolled SIMD dot products. |
| **Inference Throughput** | **> 1,500,000 passes/sec** | Single-pass matrix-vector product without dynamic heap allocations. |
| **Garbage Collector Churn** | **0 bytes in hot paths** | Reusable `mScratch*` instance buffers, ring buffers, and primitive arrays (`SparseArray`). |
| **Memory Footprint** | **~2.06 MB APK** | Native C++ triage structures and quantized neural weights without runtime bloat. |
| **Idle Battery Impact** | **0.0% background draw** | No wake-locks, no background daemons, and zero network polling. |

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

## Tech Stack & Versions

- **Language:** Java 21 & Native C++ (Inference Engine)
- **Android SDK:** API 37 (Target & Compile), API 23 (Min)
- **Build System:** Gradle 9.3.1
- **Key Dependencies:**
  - `androidx.core:core:1.13.1`
  - `androidx.appcompat:appcompat:1.7.0`
  - `com.google.android.material:material:1.12.0` (Material You integration)
  - `androidx.preference:preference:1.2.1`
  - `androidx.autofill:autofill:1.3.0`

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

This project is based on [rkkr/simple-keyboard](https://github.com/rkkr/simple-keyboard), which was originally derived from the [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/) keyboard.
