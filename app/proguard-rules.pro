# Methods called from JavaScript via addJavascriptInterface are found by annotation at runtime.
-keepattributes *Annotation*
-keep class com.galiehneh.wordcraft.web.WordCraftSync {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# androidx.webkit ships its own consumer rules for the support_lib_boundary reflection (seen in R8 configuration.txt).
