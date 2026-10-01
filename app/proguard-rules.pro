# Keep JavaScript interface members callable from WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
