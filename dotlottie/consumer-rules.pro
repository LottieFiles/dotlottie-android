-dontwarn java.awt.*
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

-keep class com.dotlottie.** { *; }
-keep class * implements com.dotlottie.** { *; }
