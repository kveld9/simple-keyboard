# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/rkr/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class rkr.simplekeyboard.inputmethod.R

# Keep Fragments instantiated via FragmentFactory reflection
-keep class rkr.simplekeyboard.inputmethod.latin.settings.AppearanceSettingsFragment { public <init>(); }
-keep class rkr.simplekeyboard.inputmethod.latin.settings.AutocorrectSettingsFragment { public <init>(); }
-keep class rkr.simplekeyboard.inputmethod.latin.settings.ClipboardSettingsFragment { public <init>(); }
-keep class rkr.simplekeyboard.inputmethod.latin.settings.KeyPressSettingsFragment { public <init>(); }
-keep class rkr.simplekeyboard.inputmethod.latin.settings.LanguagesSettingsFragment { public <init>(); }
-keep class rkr.simplekeyboard.inputmethod.latin.settings.PreferencesSettingsFragment { public <init>(); }
-keep class rkr.simplekeyboard.inputmethod.latin.settings.SettingsFragment { public <init>(); }
-keep class rkr.simplekeyboard.inputmethod.latin.settings.ThemeSettingsFragment { public <init>(); }
