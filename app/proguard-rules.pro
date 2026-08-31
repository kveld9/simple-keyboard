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
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}
-keep class rkr.simplekeyboard.inputmethod.latin.settings.*Fragment {
    public <init>();
}

# Strip all Log calls in release builds (debug APK retains them)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
