---
"dotlottie-android": minor
---

Add `performanceMode` and `cacheId` to `DotLottieGLSurfaceAnimation` and `DotLottieGLAnimation` to support keeping the player alive across surface recreation (CPU mode). Add `onSurfaceReady` callback to `DotLottieEventListener` and implement player caching and frame restoration. Fix safe GL context destruction and player lifecycle management in CPU mode.
