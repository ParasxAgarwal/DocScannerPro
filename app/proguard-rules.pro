# pdfbox-android (PDF merge/split/protect) uses reflection over its Apache
# packages and ships optional codecs that are absent on Android.
-keep class com.tom_roush.** { *; }
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn javax.imageio.**
-dontwarn com.gemalto.jp2.**
