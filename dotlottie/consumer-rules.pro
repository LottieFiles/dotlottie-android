# Keep DotLottie classes for reflection and JNI
-keep class com.dotlottie.** { *; }
-keep class * implements com.dotlottie.** { *; }

# Keep JNI native methods
-keep class com.lottiefiles.dotlottie.core.jni.** { *; }
