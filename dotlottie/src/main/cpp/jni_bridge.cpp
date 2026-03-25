#include "dotlottie_player.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/bitmap.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "DotLottieJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Forward declare all JNI functions
extern "C" {

// Player lifecycle
static jlong nativeNewPlayer(JNIEnv *env, jclass, jint threads);
static jlong nativeNewPlayerWithConfig(JNIEnv *env, jclass, jint threads,
                                       jint mode, jfloat speed, jboolean loop,
                                       jint loopCount, jboolean autoplay,
                                       jboolean useFrameInterpolation,
                                       jint backgroundColor,
                                       jboolean hasSegment, jfloat segmentStart,
                                       jfloat segmentEnd, jstring marker,
                                       jint fit, jfloat alignX, jfloat alignY,
                                       jstring themeId);
static jint nativeApplyConfig(JNIEnv *env, jclass, jlong ptr, jint mode,
                              jfloat speed, jboolean loop, jint loopCount,
                              jboolean autoplay,
                              jboolean useFrameInterpolation,
                              jint backgroundColor, jboolean hasSegment,
                              jfloat segmentStart, jfloat segmentEnd,
                              jstring marker, jint fit, jfloat alignX,
                              jfloat alignY, jstring themeId);
static jint nativeDestroy(JNIEnv *env, jclass, jlong ptr);

// Loading
static jint nativeLoadAnimationData(JNIEnv *env, jclass, jlong ptr,
                                    jstring data, jint width, jint height);
static jint nativeLoadAnimationPath(JNIEnv *env, jclass, jlong ptr,
                                    jstring path, jint width, jint height);
static jint nativeLoadAnimation(JNIEnv *env, jclass, jlong ptr, jstring id,
                                jint width, jint height);
static jint nativeLoadDotLottieData(JNIEnv *env, jclass, jlong ptr,
                                    jbyteArray data, jint width, jint height);

// Playback control
static jint nativePlay(JNIEnv *env, jclass, jlong ptr);
static jint nativePause(JNIEnv *env, jclass, jlong ptr);
static jint nativeStop(JNIEnv *env, jclass, jlong ptr);
static jint nativeRender(JNIEnv *env, jclass, jlong ptr);
static jint nativeTick(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeRequestFrame(JNIEnv *env, jclass, jlong ptr);
static jint nativeSetFrame(JNIEnv *env, jclass, jlong ptr, jfloat frame);
static jint nativeSeek(JNIEnv *env, jclass, jlong ptr, jfloat time);
static jint nativeResize(JNIEnv *env, jclass, jlong ptr, jint width,
                         jint height);
static jint nativeClear(JNIEnv *env, jclass, jlong ptr);

// State queries
static jboolean nativeIsLoaded(JNIEnv *env, jclass, jlong ptr);
static jboolean nativeIsPlaying(JNIEnv *env, jclass, jlong ptr);
static jboolean nativeIsPaused(JNIEnv *env, jclass, jlong ptr);
static jboolean nativeIsStopped(JNIEnv *env, jclass, jlong ptr);
static jboolean nativeIsComplete(JNIEnv *env, jclass, jlong ptr);

// Getters
static jfloat nativeCurrentFrame(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeTotalFrames(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeDuration(JNIEnv *env, jclass, jlong ptr);
static jint nativeLoopCount(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeSegmentDuration(JNIEnv *env, jclass, jlong ptr);
static jfloatArray nativeAnimationSize(JNIEnv *env, jclass, jlong ptr);

// Buffer management (caller-managed)
static jlong nativeAllocateBuffer(JNIEnv *env, jclass, jint width, jint height);
static void nativeFreeBuffer(JNIEnv *env, jclass, jlong bufferPtr);
static jint nativeSetSwTarget(JNIEnv *env, jclass, jlong playerPtr,
                              jlong bufferPtr, jint width, jint height);
static jint nativeSetGlTarget(JNIEnv *env, jclass, jlong playerPtr,
                              jint framebufferId, jint width, jint height);

// Config setters
static jint nativeSetMode(JNIEnv *env, jclass, jlong ptr, jint mode);
static jint nativeSetSpeed(JNIEnv *env, jclass, jlong ptr, jfloat speed);
static jint nativeSetLoop(JNIEnv *env, jclass, jlong ptr, jboolean loop);
static jint nativeSetLoopCount(JNIEnv *env, jclass, jlong ptr, jint count);
static jint nativeSetAutoplay(JNIEnv *env, jclass, jlong ptr,
                              jboolean autoplay);
static jint nativeSetUseFrameInterpolation(JNIEnv *env, jclass, jlong ptr,
                                           jboolean enabled);
static jint nativeSetBackgroundColor(JNIEnv *env, jclass, jlong ptr,
                                     jint color);
static jint nativeSetSegment(JNIEnv *env, jclass, jlong ptr, jfloat start,
                             jfloat end);
static jint nativeClearSegment(JNIEnv *env, jclass, jlong ptr);
static jint nativeSetMarker(JNIEnv *env, jclass, jlong ptr, jstring marker);
static jint nativeSetLayout(JNIEnv *env, jclass, jlong ptr, jint fit,
                            jfloat alignX, jfloat alignY);

// Config getters
static jint nativeGetMode(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeGetSpeed(JNIEnv *env, jclass, jlong ptr);
static jboolean nativeGetLoop(JNIEnv *env, jclass, jlong ptr);
static jint nativeGetLoopCount(JNIEnv *env, jclass, jlong ptr);
static jboolean nativeGetAutoplay(JNIEnv *env, jclass, jlong ptr);
static jboolean nativeGetUseFrameInterpolation(JNIEnv *env, jclass, jlong ptr);
static jint nativeGetBackgroundColor(JNIEnv *env, jclass, jlong ptr);
static jfloatArray nativeGetSegment(JNIEnv *env, jclass, jlong ptr);
static jstring nativeGetActiveMarker(JNIEnv *env, jclass, jlong ptr);
static jfloatArray nativeGetLayout(JNIEnv *env, jclass, jlong ptr);

// Manifest & markers
static jstring nativeManifest(JNIEnv *env, jclass, jlong ptr);
static jint nativeMarkersCount(JNIEnv *env, jclass, jlong ptr);
static jobjectArray nativeMarker(JNIEnv *env, jclass, jlong ptr, jint idx);

// Theme
static jint nativeSetTheme(JNIEnv *env, jclass, jlong ptr, jstring themeId);
static jint nativeResetTheme(JNIEnv *env, jclass, jlong ptr);
static jint nativeSetThemeData(JNIEnv *env, jclass, jlong ptr,
                               jstring themeData);
static jstring nativeActiveThemeId(JNIEnv *env, jclass, jlong ptr);
static jstring nativeActiveAnimationId(JNIEnv *env, jclass, jlong ptr);

// Slots
static jint nativeSetSlotsStr(JNIEnv *env, jclass, jlong ptr,
                              jstring slotsJson);
static jint nativeClearSlots(JNIEnv *env, jclass, jlong ptr);
static jint nativeClearSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId);
static jint nativeSetColorSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                               jfloat r, jfloat g, jfloat b);
static jint nativeSetScalarSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                                jfloat value);
static jint nativeSetTextSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                              jstring text);
static jint nativeSetVectorSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                                jfloat x, jfloat y);
static jint nativeSetPositionSlot(JNIEnv *env, jclass, jlong ptr,
                                  jstring slotId, jfloat x, jfloat y);
static jint nativeSetImageSlotPath(JNIEnv *env, jclass, jlong ptr,
                                   jstring slotId, jstring path);
static jint nativeSetImageSlotDataUrl(JNIEnv *env, jclass, jlong ptr,
                                      jstring slotId, jstring dataUrl);

// Layer bounds
static jfloatArray nativeGetLayerBounds(JNIEnv *env, jclass, jlong ptr,
                                        jstring layerName);

// Viewport
static jint nativeSetViewport(JNIEnv *env, jclass, jlong ptr, jint x, jint y,
                              jint w, jint h);

// Poll events
static jobject nativePollEvent(JNIEnv *env, jclass, jlong ptr);

// State machine
static jlong nativeStateMachineLoad(JNIEnv *env, jclass, jlong playerPtr,
                                    jstring stateMachineId);
static jlong nativeStateMachineLoadData(JNIEnv *env, jclass, jlong playerPtr,
                                        jstring data);
static void nativeStateMachineRelease(JNIEnv *env, jclass, jlong smPtr);
static jint nativeStateMachineStart(JNIEnv *env, jclass, jlong smPtr,
                                    jstring whitelist,
                                    jboolean requireUserInteraction);
static jint nativeStateMachineStop(JNIEnv *env, jclass, jlong smPtr);
static jint nativeStateMachineTick(JNIEnv *env, jclass, jlong smPtr);
static jint nativeStateMachineSetNumericInput(JNIEnv *env, jclass, jlong smPtr,
                                              jstring key, jfloat value);
static jint nativeStateMachineSetStringInput(JNIEnv *env, jclass, jlong smPtr,
                                             jstring key, jstring value);
static jint nativeStateMachineSetBooleanInput(JNIEnv *env, jclass, jlong smPtr,
                                              jstring key, jboolean value);
static jfloat nativeStateMachineGetNumericInput(JNIEnv *env, jclass,
                                                jlong smPtr, jstring key);
static jstring nativeStateMachineGetStringInput(JNIEnv *env, jclass,
                                                jlong smPtr, jstring key);
static jboolean nativeStateMachineGetBooleanInput(JNIEnv *env, jclass,
                                                  jlong smPtr, jstring key);
static jstring nativeStateMachineCurrentState(JNIEnv *env, jclass, jlong smPtr);
static jstring nativeStateMachineStatus(JNIEnv *env, jclass, jlong smPtr);
static jint nativeStateMachineFireEvent(JNIEnv *env, jclass, jlong smPtr,
                                        jstring eventName);
static jint nativeStateMachinePostEvent(JNIEnv *env, jclass, jlong smPtr,
                                        jint eventTag, jfloat x, jfloat y);
static jint nativeStateMachineFrameworkSetup(JNIEnv *env, jclass, jlong smPtr);
static jobject nativeStateMachinePollEvent(JNIEnv *env, jclass, jlong smPtr);
static jstring nativeStateMachinePollInternalEvent(JNIEnv *env, jclass,
                                                   jlong smPtr);
static jstring nativeGetStateMachine(JNIEnv *env, jclass, jlong playerPtr,
                                     jstring id);

// Bitmap pixel access
static jlong nativeLockBitmapPixels(JNIEnv *env, jclass, jobject bitmap);
static void nativeUnlockBitmapPixels(JNIEnv *env, jclass, jobject bitmap);
static void nativeFlushBitmapPixels(JNIEnv *env, jclass, jobject bitmap);
static void nativeCopyBufferToBitmap(JNIEnv *env, jclass, jlong bufferPtr,
                                     jobject bitmap, jint sizeBytes);

// Pointer helper
static jobject nativeGetByteBuffer(JNIEnv *env, jclass, jlong address,
                                   jint length);

// HardwareBuffer FBO (API 26+, runtime-resolved)
static jintArray nativeCreateFboFromHardwareBuffer(JNIEnv *env, jclass,
                                                   jobject hwBuffer);
static void nativeDestroyFboResources(JNIEnv *env, jclass, jint fboId,
                                      jint textureId, jlong eglImagePtr);
static void nativeGlFinish(JNIEnv *env, jclass);

} // extern "C"

// ==================== Helper: size-query string pattern ====================

// Helper to call a size-query-then-fill C API and return a jstring.
// func_query: calls with buffer=NULL to get size
// func_fill: calls with allocated buffer to fill
// Both must have the same signature.
template <typename Func>
static jstring sizeQueryString(JNIEnv *env, Func func, void *obj) {
  uintptr_t size = 0;
  auto res = func(obj, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    return env->NewStringUTF("");
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    return env->NewStringUTF("");
  }
  func(obj, buf, nullptr);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

// ==================== Player Lifecycle ====================

jlong nativeNewPlayer(JNIEnv *env, jclass, jint threads) {
  dotlottieDotLottiePlayer *player =
      dotlottie_new_player(static_cast<uint32_t>(threads));
  return reinterpret_cast<jlong>(player);
}

// Helper: apply all config properties to a player in a single C++ scope.
static void applyConfigToPlayer(JNIEnv *env,
                                dotlottieDotLottiePlayer *player, jint mode,
                                jfloat speed, jboolean loop, jint loopCount,
                                jboolean autoplay,
                                jboolean useFrameInterpolation,
                                jint backgroundColor, jboolean hasSegment,
                                jfloat segmentStart, jfloat segmentEnd,
                                jstring marker, jint fit, jfloat alignX,
                                jfloat alignY, jstring themeId) {
  dotlottie_set_mode(player, static_cast<dotlottieMode>(mode));
  dotlottie_set_speed(player, speed);
  dotlottie_set_loop(player, loop == JNI_TRUE);
  dotlottie_set_loop_count(player, static_cast<uint32_t>(loopCount));
  dotlottie_set_autoplay(player, autoplay == JNI_TRUE);
  dotlottie_set_use_frame_interpolation(player,
                                        useFrameInterpolation == JNI_TRUE);
  dotlottie_set_background_color(player, static_cast<uint32_t>(backgroundColor));

  if (hasSegment == JNI_TRUE) {
    float segment[2] = {segmentStart, segmentEnd};
    dotlottie_set_segment(player, &segment);
  } else {
    dotlottie_set_segment(player, nullptr);
  }

  if (marker != nullptr) {
    const char *cMarker = env->GetStringUTFChars(marker, nullptr);
    dotlottie_set_marker(player, cMarker);
    env->ReleaseStringUTFChars(marker, cMarker);
  } else {
    dotlottie_set_marker(player, nullptr);
  }

  dotlottieLayout layout;
  layout.fit = static_cast<dotlottieFit>(fit);
  layout.align[0] = alignX;
  layout.align[1] = alignY;
  dotlottie_set_layout(player, layout);

  if (themeId != nullptr) {
    const char *cThemeId = env->GetStringUTFChars(themeId, nullptr);
    if (strlen(cThemeId) > 0) {
      dotlottie_set_theme(player, cThemeId);
    } else {
      dotlottie_reset_theme(player);
    }
    env->ReleaseStringUTFChars(themeId, cThemeId);
  } else {
    dotlottie_reset_theme(player);
  }
}

jlong nativeNewPlayerWithConfig(JNIEnv *env, jclass, jint threads, jint mode,
                                jfloat speed, jboolean loop, jint loopCount,
                                jboolean autoplay,
                                jboolean useFrameInterpolation,
                                jint backgroundColor, jboolean hasSegment,
                                jfloat segmentStart, jfloat segmentEnd,
                                jstring marker, jint fit, jfloat alignX,
                                jfloat alignY, jstring themeId) {
  dotlottieDotLottiePlayer *player =
      dotlottie_new_player(static_cast<uint32_t>(threads));
  if (player != nullptr) {
    applyConfigToPlayer(env, player, mode, speed, loop, loopCount, autoplay,
                        useFrameInterpolation, backgroundColor, hasSegment,
                        segmentStart, segmentEnd, marker, fit, alignX, alignY,
                        themeId);
  }
  return reinterpret_cast<jlong>(player);
}

jint nativeApplyConfig(JNIEnv *env, jclass, jlong ptr, jint mode, jfloat speed,
                       jboolean loop, jint loopCount, jboolean autoplay,
                       jboolean useFrameInterpolation, jint backgroundColor,
                       jboolean hasSegment, jfloat segmentStart,
                       jfloat segmentEnd, jstring marker, jint fit,
                       jfloat alignX, jfloat alignY, jstring themeId) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  applyConfigToPlayer(env, player, mode, speed, loop, loopCount, autoplay,
                      useFrameInterpolation, backgroundColor, hasSegment,
                      segmentStart, segmentEnd, marker, fit, alignX, alignY,
                      themeId);
  return 0;
}

jint nativeDestroy(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_destroy(player));
}

// ==================== Loading ====================

jint nativeLoadAnimationData(JNIEnv *env, jclass, jlong ptr, jstring data,
                             jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cData = env->GetStringUTFChars(data, nullptr);
  auto result = dotlottie_load_animation_data(player, cData, width, height);
  env->ReleaseStringUTFChars(data, cData);
  return static_cast<jint>(result);
}

jint nativeLoadAnimationPath(JNIEnv *env, jclass, jlong ptr, jstring path,
                             jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cPath = env->GetStringUTFChars(path, nullptr);
  auto result = dotlottie_load_animation_path(player, cPath, width, height);
  env->ReleaseStringUTFChars(path, cPath);
  return static_cast<jint>(result);
}

jint nativeLoadAnimation(JNIEnv *env, jclass, jlong ptr, jstring id, jint width,
                         jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cId = env->GetStringUTFChars(id, nullptr);
  auto result = dotlottie_load_animation(player, cId, width, height);
  env->ReleaseStringUTFChars(id, cId);
  return static_cast<jint>(result);
}

jint nativeLoadDotLottieData(JNIEnv *env, jclass, jlong ptr, jbyteArray data,
                             jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  jbyte *bytes = env->GetByteArrayElements(data, nullptr);
  jsize len = env->GetArrayLength(data);
  auto result = dotlottie_load_dotlottie_data(
      player, reinterpret_cast<const char *>(bytes), len, width, height);
  env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
  return static_cast<jint>(result);
}

// ==================== Playback Control ====================

jint nativePlay(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_play(player));
}

jint nativePause(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_pause(player));
}

jint nativeStop(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_stop(player));
}

jint nativeRender(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_render(player));
}

jint nativeTick(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_tick(player));
}

jfloat nativeRequestFrame(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float frame = 0.0f;
  dotlottie_request_frame(player, &frame);
  return frame;
}

jint nativeSetFrame(JNIEnv *env, jclass, jlong ptr, jfloat frame) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_set_frame(player, frame));
}

jint nativeSeek(JNIEnv *env, jclass, jlong ptr, jfloat time) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_seek(player, time));
}

jint nativeResize(JNIEnv *env, jclass, jlong ptr, jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_resize(player, width, height));
}

jint nativeClear(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_clear(player));
}

// ==================== State Queries ====================

jboolean nativeIsLoaded(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_is_loaded(player) ? JNI_TRUE : JNI_FALSE;
}

jboolean nativeIsPlaying(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_playback_status(player) == dotlottiePlaybackStatus::Playing
             ? JNI_TRUE
             : JNI_FALSE;
}

jboolean nativeIsPaused(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_playback_status(player) == dotlottiePlaybackStatus::Paused
             ? JNI_TRUE
             : JNI_FALSE;
}

jboolean nativeIsStopped(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_playback_status(player) == dotlottiePlaybackStatus::Stopped
             ? JNI_TRUE
             : JNI_FALSE;
}

jboolean nativeIsComplete(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_is_complete(player) ? JNI_TRUE : JNI_FALSE;
}

// ==================== Getters ====================

jfloat nativeCurrentFrame(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float frame = 0.0f;
  dotlottie_current_frame(player, &frame);
  return frame;
}

jfloat nativeTotalFrames(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float frames = 0.0f;
  dotlottie_total_frames(player, &frames);
  return frames;
}

jfloat nativeDuration(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float duration = 0.0f;
  dotlottie_duration(player, &duration);
  return duration;
}

jint nativeLoopCount(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  uint32_t count = 0;
  dotlottie_current_loop_count(player, &count);
  return static_cast<jint>(count);
}

jfloat nativeSegmentDuration(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float duration = 0.0f;
  dotlottie_segment_duration(player, &duration);
  return duration;
}

jfloatArray nativeAnimationSize(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float width = 0.0f, height = 0.0f;
  dotlottie_animation_size(player, &width, &height);

  jfloatArray result = env->NewFloatArray(2);
  jfloat values[2] = {width, height};
  env->SetFloatArrayRegion(result, 0, 2, values);
  return result;
}

// ==================== Buffer Management (caller-managed) ====================

jlong nativeAllocateBuffer(JNIEnv *env, jclass, jint width, jint height) {
  size_t size = (size_t)width * (size_t)height;
  uint32_t *buffer = (uint32_t *)calloc(size, sizeof(uint32_t));
  return reinterpret_cast<jlong>(buffer);
}

void nativeFreeBuffer(JNIEnv *env, jclass, jlong bufferPtr) {
  if (bufferPtr != 0) {
    free(reinterpret_cast<void *>(bufferPtr));
  }
}

jint nativeSetSwTarget(JNIEnv *env, jclass, jlong playerPtr, jlong bufferPtr,
                       jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(playerPtr);
  auto *buffer = reinterpret_cast<uint32_t *>(bufferPtr);
  return static_cast<jint>(dotlottie_set_sw_target(
      player, buffer, width, height, dotlottieColorSpace::ABGR8888));
}

jint nativeSetGlTarget(JNIEnv *env, jclass, jlong playerPtr,
                       jint framebufferId, jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(playerPtr);
  // Must be called from the GL thread — eglGetCurrentContext() returns
  // the EGLContext that is current on the calling thread.
  void *context = reinterpret_cast<void *>(eglGetCurrentContext());
  auto result = dotlottie_set_gl_target(
      player, context, static_cast<int32_t>(framebufferId), width, height);
  return static_cast<jint>(result);
}

// ==================== Config Setters ====================

jint nativeSetMode(JNIEnv *env, jclass, jlong ptr, jint mode) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(
      dotlottie_set_mode(player, static_cast<dotlottieMode>(mode)));
}

jint nativeSetSpeed(JNIEnv *env, jclass, jlong ptr, jfloat speed) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_set_speed(player, speed));
}

jint nativeSetLoop(JNIEnv *env, jclass, jlong ptr, jboolean loop) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(
      dotlottie_set_loop(player, loop == JNI_TRUE));
}

jint nativeSetLoopCount(JNIEnv *env, jclass, jlong ptr, jint count) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(
      dotlottie_set_loop_count(player, static_cast<uint32_t>(count)));
}

jint nativeSetAutoplay(JNIEnv *env, jclass, jlong ptr, jboolean autoplay) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(
      dotlottie_set_autoplay(player, autoplay == JNI_TRUE));
}

jint nativeSetUseFrameInterpolation(JNIEnv *env, jclass, jlong ptr,
                                    jboolean enabled) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(
      dotlottie_set_use_frame_interpolation(player, enabled == JNI_TRUE));
}

jint nativeSetBackgroundColor(JNIEnv *env, jclass, jlong ptr, jint color) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(
      dotlottie_set_background_color(player, static_cast<uint32_t>(color)));
}

jint nativeSetSegment(JNIEnv *env, jclass, jlong ptr, jfloat start,
                      jfloat end) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float segment[2] = {start, end};
  return static_cast<jint>(dotlottie_set_segment(player, &segment));
}

jint nativeClearSegment(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_set_segment(player, nullptr));
}

jint nativeSetMarker(JNIEnv *env, jclass, jlong ptr, jstring marker) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  if (marker == nullptr) {
    return static_cast<jint>(dotlottie_set_marker(player, nullptr));
  }
  const char *cMarker = env->GetStringUTFChars(marker, nullptr);
  auto result = dotlottie_set_marker(player, cMarker);
  env->ReleaseStringUTFChars(marker, cMarker);
  return static_cast<jint>(result);
}

jint nativeSetLayout(JNIEnv *env, jclass, jlong ptr, jint fit, jfloat alignX,
                     jfloat alignY) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  dotlottieLayout layout;
  layout.fit = static_cast<dotlottieFit>(fit);
  layout.align[0] = alignX;
  layout.align[1] = alignY;
  return static_cast<jint>(dotlottie_set_layout(player, layout));
}

// ==================== Config Getters ====================

jint nativeGetMode(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_get_mode(player));
}

jfloat nativeGetSpeed(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_get_speed(player);
}

jboolean nativeGetLoop(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_get_loop(player) ? JNI_TRUE : JNI_FALSE;
}

jint nativeGetLoopCount(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_get_loop_count(player));
}

jboolean nativeGetAutoplay(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_get_autoplay(player) ? JNI_TRUE : JNI_FALSE;
}

jboolean nativeGetUseFrameInterpolation(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_get_use_frame_interpolation(player) ? JNI_TRUE : JNI_FALSE;
}

jint nativeGetBackgroundColor(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_get_background_color(player));
}

jfloatArray nativeGetSegment(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float segment[2] = {0.0f, 0.0f};
  auto res = dotlottie_get_segment(player, &segment);

  jfloatArray result = env->NewFloatArray(2);
  if (res == dotlottieDotLottieResult::Success) {
    env->SetFloatArrayRegion(result, 0, 2, segment);
  }
  return result;
}

jstring nativeGetActiveMarker(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  uintptr_t size = 0;
  auto res = dotlottie_get_active_marker(player, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    return env->NewStringUTF("");
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    return env->NewStringUTF("");
  }
  dotlottie_get_active_marker(player, buf, nullptr);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

jfloatArray nativeGetLayout(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  dotlottieLayout layout;
  layout.fit = dotlottieFit::Contain;
  layout.align[0] = 0.5f;
  layout.align[1] = 0.5f;
  dotlottie_get_layout(player, &layout);

  // Return [fit, alignX, alignY]
  jfloatArray result = env->NewFloatArray(3);
  jfloat values[3] = {static_cast<jfloat>(layout.fit), layout.align[0],
                      layout.align[1]};
  env->SetFloatArrayRegion(result, 0, 3, values);
  return result;
}

// ==================== Manifest & Markers ====================

jstring nativeManifest(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  uintptr_t size = 0;
  auto res = dotlottie_manifest(player, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    return nullptr;
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    return nullptr;
  }
  dotlottie_manifest(player, buf, nullptr);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

jint nativeMarkersCount(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  uint32_t count = 0;
  dotlottie_markers_count(player, &count);
  return static_cast<jint>(count);
}

// Returns String[] { name, time, duration } for marker at index
jobjectArray nativeMarker(JNIEnv *env, jclass, jlong ptr, jint idx) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *name = nullptr;
  float time = 0.0f;
  float duration = 0.0f;
  auto res = dotlottie_marker(player, static_cast<uint32_t>(idx), &name, &time,
                              &duration);
  if (res != dotlottieDotLottieResult::Success || name == nullptr) {
    return nullptr;
  }

  jclass stringClass = env->FindClass("java/lang/String");
  jobjectArray arr = env->NewObjectArray(3, stringClass, nullptr);

  env->SetObjectArrayElement(arr, 0, env->NewStringUTF(name));

  char timeBuf[32], durBuf[32];
  snprintf(timeBuf, sizeof(timeBuf), "%f", time);
  snprintf(durBuf, sizeof(durBuf), "%f", duration);
  env->SetObjectArrayElement(arr, 1, env->NewStringUTF(timeBuf));
  env->SetObjectArrayElement(arr, 2, env->NewStringUTF(durBuf));

  return arr;
}

// ==================== Theme ====================

jint nativeSetTheme(JNIEnv *env, jclass, jlong ptr, jstring themeId) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cThemeId = env->GetStringUTFChars(themeId, nullptr);
  auto result = dotlottie_set_theme(player, cThemeId);
  env->ReleaseStringUTFChars(themeId, cThemeId);
  return static_cast<jint>(result);
}

jint nativeResetTheme(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_reset_theme(player));
}

jint nativeSetThemeData(JNIEnv *env, jclass, jlong ptr, jstring themeData) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cThemeData = env->GetStringUTFChars(themeData, nullptr);
  auto result = dotlottie_set_theme_data(player, cThemeData);
  env->ReleaseStringUTFChars(themeData, cThemeData);
  return static_cast<jint>(result);
}

jstring nativeActiveThemeId(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  uintptr_t size = 0;
  auto res = dotlottie_theme_id(player, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    return env->NewStringUTF("");
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    return env->NewStringUTF("");
  }
  dotlottie_theme_id(player, buf, nullptr);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

jstring nativeActiveAnimationId(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  uintptr_t size = 0;
  auto res = dotlottie_animation_id(player, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    return env->NewStringUTF("");
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    return env->NewStringUTF("");
  }
  dotlottie_animation_id(player, buf, nullptr);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

// ==================== Slots ====================

jint nativeSetSlotsStr(JNIEnv *env, jclass, jlong ptr, jstring slotsJson) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cJson = env->GetStringUTFChars(slotsJson, nullptr);
  auto result = dotlottie_set_slots_str(player, cJson);
  env->ReleaseStringUTFChars(slotsJson, cJson);
  return static_cast<jint>(result);
}

jint nativeClearSlots(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_clear_slots(player));
}

jint nativeClearSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  auto result = dotlottie_clear_slot(player, cSlotId);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return static_cast<jint>(result);
}

jint nativeSetColorSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                        jfloat r, jfloat g, jfloat b) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  auto result = dotlottie_set_color_slot(player, cSlotId, r, g, b);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return static_cast<jint>(result);
}

jint nativeSetScalarSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                         jfloat value) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  auto result = dotlottie_set_scalar_slot(player, cSlotId, value);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return static_cast<jint>(result);
}

jint nativeSetTextSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                       jstring text) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  const char *cText = env->GetStringUTFChars(text, nullptr);
  auto result = dotlottie_set_text_slot(player, cSlotId, cText);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  env->ReleaseStringUTFChars(text, cText);
  return static_cast<jint>(result);
}

jint nativeSetVectorSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                         jfloat x, jfloat y) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  auto result = dotlottie_set_vector_slot(player, cSlotId, x, y);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return static_cast<jint>(result);
}

jint nativeSetPositionSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                           jfloat x, jfloat y) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  auto result = dotlottie_set_position_slot(player, cSlotId, x, y);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return static_cast<jint>(result);
}

jint nativeSetImageSlotPath(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                            jstring path) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  const char *cPath = env->GetStringUTFChars(path, nullptr);
  auto result = dotlottie_set_image_slot_path(player, cSlotId, cPath);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  env->ReleaseStringUTFChars(path, cPath);
  return static_cast<jint>(result);
}

jint nativeSetImageSlotDataUrl(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                               jstring dataUrl) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  const char *cDataUrl = env->GetStringUTFChars(dataUrl, nullptr);
  auto result = dotlottie_set_image_slot_data_url(player, cSlotId, cDataUrl);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  env->ReleaseStringUTFChars(dataUrl, cDataUrl);
  return static_cast<jint>(result);
}

// ==================== Layer Bounds ====================

jfloatArray nativeGetLayerBounds(JNIEnv *env, jclass, jlong ptr,
                                 jstring layerName) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cName = env->GetStringUTFChars(layerName, nullptr);
  dotlottieLayerBoundingBox bbox;
  auto res = dotlottie_get_layer_bounds(player, cName, &bbox);
  env->ReleaseStringUTFChars(layerName, cName);

  if (res != dotlottieDotLottieResult::Success) {
    return nullptr;
  }

  jfloatArray result = env->NewFloatArray(8);
  jfloat values[8] = {bbox.x1, bbox.y1, bbox.x2, bbox.y2,
                      bbox.x3, bbox.y3, bbox.x4, bbox.y4};
  env->SetFloatArrayRegion(result, 0, 8, values);
  return result;
}

// ==================== Viewport ====================

jint nativeSetViewport(JNIEnv *env, jclass, jlong ptr, jint x, jint y, jint w,
                       jint h) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return static_cast<jint>(dotlottie_set_viewport(player, x, y, w, h));
}

// ==================== Poll Events ====================

jobject nativePollEvent(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  dotlottieDotLottiePlayerEvent event;

  int32_t result = dotlottie_poll_event(player, &event);
  if (result != 1) {
    return nullptr;
  }

  jintArray arr = env->NewIntArray(3);
  jint values[3];
  values[0] = static_cast<jint>(event.event_type);

  if (event.event_type == dotlottieDotLottiePlayerEventType::Frame ||
      event.event_type == dotlottieDotLottiePlayerEventType::Render) {
    union {
      float f;
      int32_t i;
    } conv;
    conv.f = event.data.frame_no;
    values[1] = conv.i;
    values[2] = 0;
  } else if (event.event_type == dotlottieDotLottiePlayerEventType::Loop) {
    values[1] = static_cast<jint>(event.data.loop_count);
    values[2] = 0;
  } else {
    values[1] = 0;
    values[2] = 0;
  }

  env->SetIntArrayRegion(arr, 0, 3, values);
  return arr;
}

// ==================== State Machine ====================

jlong nativeStateMachineLoad(JNIEnv *env, jclass, jlong playerPtr,
                             jstring stateMachineId) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(playerPtr);
  const char *cId = env->GetStringUTFChars(stateMachineId, nullptr);
  dotlottieDotLottieStateMachine *sm = dotlottie_state_machine_load(player, cId);
  env->ReleaseStringUTFChars(stateMachineId, cId);
  return reinterpret_cast<jlong>(sm);
}

jlong nativeStateMachineLoadData(JNIEnv *env, jclass, jlong playerPtr,
                                 jstring data) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(playerPtr);
  const char *cData = env->GetStringUTFChars(data, nullptr);
  dotlottieDotLottieStateMachine *sm =
      dotlottie_state_machine_load_data(player, cData);
  env->ReleaseStringUTFChars(data, cData);
  return reinterpret_cast<jlong>(sm);
}

void nativeStateMachineRelease(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  if (sm != nullptr) {
    dotlottie_state_machine_release(sm);
  }
}

jint nativeStateMachineStart(JNIEnv *env, jclass, jlong smPtr,
                             jstring whitelist,
                             jboolean requireUserInteraction) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cWhitelist = nullptr;
  if (whitelist != nullptr) {
    cWhitelist = env->GetStringUTFChars(whitelist, nullptr);
  }
  auto result = dotlottie_state_machine_start(
      sm, cWhitelist, requireUserInteraction == JNI_TRUE);
  if (cWhitelist != nullptr) {
    env->ReleaseStringUTFChars(whitelist, cWhitelist);
  }
  return static_cast<jint>(result);
}

jint nativeStateMachineStop(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  return static_cast<jint>(dotlottie_state_machine_stop(sm));
}

jint nativeStateMachineTick(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  return static_cast<jint>(dotlottie_state_machine_tick(sm));
}

jint nativeStateMachineSetNumericInput(JNIEnv *env, jclass, jlong smPtr,
                                       jstring key, jfloat value) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  auto result = dotlottie_state_machine_set_numeric_input(sm, cKey, value);
  env->ReleaseStringUTFChars(key, cKey);
  return static_cast<jint>(result);
}

jint nativeStateMachineSetStringInput(JNIEnv *env, jclass, jlong smPtr,
                                      jstring key, jstring value) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  const char *cValue = env->GetStringUTFChars(value, nullptr);
  auto result = dotlottie_state_machine_set_string_input(sm, cKey, cValue);
  env->ReleaseStringUTFChars(key, cKey);
  env->ReleaseStringUTFChars(value, cValue);
  return static_cast<jint>(result);
}

jint nativeStateMachineSetBooleanInput(JNIEnv *env, jclass, jlong smPtr,
                                       jstring key, jboolean value) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  auto result =
      dotlottie_state_machine_set_boolean_input(sm, cKey, value == JNI_TRUE);
  env->ReleaseStringUTFChars(key, cKey);
  return static_cast<jint>(result);
}

jfloat nativeStateMachineGetNumericInput(JNIEnv *env, jclass, jlong smPtr,
                                         jstring key) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  float result = 0.0f;
  dotlottie_state_machine_get_numeric_input(sm, cKey, &result);
  env->ReleaseStringUTFChars(key, cKey);
  return result;
}

jstring nativeStateMachineGetStringInput(JNIEnv *env, jclass, jlong smPtr,
                                         jstring key) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);

  uintptr_t size = 0;
  auto res =
      dotlottie_state_machine_get_string_input(sm, cKey, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    env->ReleaseStringUTFChars(key, cKey);
    return env->NewStringUTF("");
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    env->ReleaseStringUTFChars(key, cKey);
    return env->NewStringUTF("");
  }
  dotlottie_state_machine_get_string_input(sm, cKey, buf, nullptr);
  env->ReleaseStringUTFChars(key, cKey);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

jboolean nativeStateMachineGetBooleanInput(JNIEnv *env, jclass, jlong smPtr,
                                           jstring key) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  bool result = false;
  dotlottie_state_machine_get_boolean_input(sm, cKey, &result);
  env->ReleaseStringUTFChars(key, cKey);
  return result ? JNI_TRUE : JNI_FALSE;
}

jstring nativeStateMachineCurrentState(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  uintptr_t size = 0;
  auto res = dotlottie_state_machine_current_state(sm, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    return env->NewStringUTF("");
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    return env->NewStringUTF("");
  }
  dotlottie_state_machine_current_state(sm, buf, nullptr);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

jstring nativeStateMachineStatus(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  uintptr_t size = 0;
  auto res = dotlottie_state_machine_status(sm, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    return env->NewStringUTF("");
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    return env->NewStringUTF("");
  }
  dotlottie_state_machine_status(sm, buf, nullptr);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

jint nativeStateMachineFireEvent(JNIEnv *env, jclass, jlong smPtr,
                                 jstring eventName) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  const char *cName = env->GetStringUTFChars(eventName, nullptr);
  auto result = dotlottie_state_machine_fire_event(sm, cName);
  env->ReleaseStringUTFChars(eventName, cName);
  return static_cast<jint>(result);
}

jint nativeStateMachinePostEvent(JNIEnv *env, jclass, jlong smPtr,
                                 jint eventTag, jfloat x, jfloat y) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  dotlottieDotLottieEvent event;
  event.tag = static_cast<dotlottieDotLottieEvent_Tag>(eventTag);

  switch (event.tag) {
  case dotlottieDotLottieEvent_Tag::PointerDown:
    event.pointer_down.x = x;
    event.pointer_down.y = y;
    break;
  case dotlottieDotLottieEvent_Tag::PointerUp:
    event.pointer_up.x = x;
    event.pointer_up.y = y;
    break;
  case dotlottieDotLottieEvent_Tag::PointerMove:
    event.pointer_move.x = x;
    event.pointer_move.y = y;
    break;
  case dotlottieDotLottieEvent_Tag::PointerEnter:
    event.pointer_enter.x = x;
    event.pointer_enter.y = y;
    break;
  case dotlottieDotLottieEvent_Tag::PointerExit:
    event.pointer_exit.x = x;
    event.pointer_exit.y = y;
    break;
  case dotlottieDotLottieEvent_Tag::Click:
    event.click.x = x;
    event.click.y = y;
    break;
  default:
    break;
  }

  return static_cast<jint>(dotlottie_state_machine_post_event(sm, &event));
}

jint nativeStateMachineFrameworkSetup(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  uint16_t result = 0;
  dotlottie_state_machine_framework_setup(sm, &result);
  return static_cast<jint>(result);
}

// Poll state machine events — pointer-based data union
jobject nativeStateMachinePollEvent(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  dotlottieStateMachineEvent event;

  int32_t result = dotlottie_state_machine_poll_event(sm, &event);
  if (result != 1) {
    return nullptr;
  }

  jclass stringClass = env->FindClass("java/lang/String");
  jobjectArray arr = env->NewObjectArray(8, stringClass, nullptr);

  // Set event type
  char eventTypeStr[16];
  snprintf(eventTypeStr, sizeof(eventTypeStr), "%d",
           static_cast<int>(event.event_type));
  env->SetObjectArrayElement(arr, 0, env->NewStringUTF(eventTypeStr));

  switch (event.event_type) {
  case dotlottieStateMachineEventType::StateMachineTransition:
    if (event.data.transition.previous_state) {
      env->SetObjectArrayElement(
          arr, 1, env->NewStringUTF(event.data.transition.previous_state));
    }
    if (event.data.transition.new_state) {
      env->SetObjectArrayElement(
          arr, 2, env->NewStringUTF(event.data.transition.new_state));
    }
    break;
  case dotlottieStateMachineEventType::StateMachineStateEntered:
  case dotlottieStateMachineEventType::StateMachineStateExit:
    if (event.data.state.state) {
      env->SetObjectArrayElement(arr, 1,
                                 env->NewStringUTF(event.data.state.state));
    }
    break;
  case dotlottieStateMachineEventType::StateMachineCustomEvent:
  case dotlottieStateMachineEventType::StateMachineError:
    if (event.data.message.message) {
      env->SetObjectArrayElement(
          arr, 1, env->NewStringUTF(event.data.message.message));
    }
    break;
  case dotlottieStateMachineEventType::StateMachineStringInputChange:
    if (event.data.string_input.name) {
      env->SetObjectArrayElement(
          arr, 1, env->NewStringUTF(event.data.string_input.name));
    }
    if (event.data.string_input.old_value) {
      env->SetObjectArrayElement(
          arr, 2, env->NewStringUTF(event.data.string_input.old_value));
    }
    if (event.data.string_input.new_value) {
      env->SetObjectArrayElement(
          arr, 3, env->NewStringUTF(event.data.string_input.new_value));
    }
    break;
  case dotlottieStateMachineEventType::StateMachineNumericInputChange: {
    if (event.data.numeric_input.name) {
      env->SetObjectArrayElement(
          arr, 1, env->NewStringUTF(event.data.numeric_input.name));
    }
    char oldVal[32], newVal[32];
    snprintf(oldVal, sizeof(oldVal), "%f", event.data.numeric_input.old_value);
    snprintf(newVal, sizeof(newVal), "%f", event.data.numeric_input.new_value);
    env->SetObjectArrayElement(arr, 4, env->NewStringUTF(oldVal));
    env->SetObjectArrayElement(arr, 5, env->NewStringUTF(newVal));
    break;
  }
  case dotlottieStateMachineEventType::StateMachineBooleanInputChange: {
    if (event.data.boolean_input.name) {
      env->SetObjectArrayElement(
          arr, 1, env->NewStringUTF(event.data.boolean_input.name));
    }
    env->SetObjectArrayElement(
        arr, 6,
        env->NewStringUTF(event.data.boolean_input.old_value ? "true"
                                                             : "false"));
    env->SetObjectArrayElement(
        arr, 7,
        env->NewStringUTF(event.data.boolean_input.new_value ? "true"
                                                             : "false"));
    break;
  }
  case dotlottieStateMachineEventType::StateMachineInputFired:
    if (event.data.input_fired.name) {
      env->SetObjectArrayElement(
          arr, 1, env->NewStringUTF(event.data.input_fired.name));
    }
    break;
  default:
    break;
  }

  return arr;
}

jstring nativeStateMachinePollInternalEvent(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieDotLottieStateMachine *>(smPtr);
  dotlottieStateMachineInternalEvent event;

  int32_t result = dotlottie_state_machine_poll_internal_event(sm, &event);
  if (result != 1) {
    return nullptr;
  }

  if (event.message != nullptr) {
    return env->NewStringUTF(event.message);
  }
  return nullptr;
}

jstring nativeGetStateMachine(JNIEnv *env, jclass, jlong playerPtr,
                              jstring id) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(playerPtr);
  const char *cId = env->GetStringUTFChars(id, nullptr);

  uintptr_t size = 0;
  auto res = dotlottie_get_state_machine(player, cId, nullptr, &size);
  if (res != dotlottieDotLottieResult::Success || size == 0) {
    env->ReleaseStringUTFChars(id, cId);
    return nullptr;
  }
  char *buf = (char *)malloc(size);
  if (!buf) {
    env->ReleaseStringUTFChars(id, cId);
    return nullptr;
  }
  dotlottie_get_state_machine(player, cId, buf, nullptr);
  env->ReleaseStringUTFChars(id, cId);
  jstring result = env->NewStringUTF(buf);
  free(buf);
  return result;
}

// ==================== Bitmap Pixel Access ====================

jlong nativeLockBitmapPixels(JNIEnv *env, jclass, jobject bitmap) {
  void *pixels = nullptr;
  int ret = AndroidBitmap_lockPixels(env, bitmap, &pixels);
  if (ret != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) {
    return 0;
  }
  return reinterpret_cast<jlong>(pixels);
}

void nativeUnlockBitmapPixels(JNIEnv *env, jclass, jobject bitmap) {
  AndroidBitmap_unlockPixels(env, bitmap);
}

void nativeFlushBitmapPixels(JNIEnv *env, jclass, jobject bitmap) {
  // Unlock bumps Bitmap generation ID (notifyPixelsChanged), then re-lock
  // keeps the pixel pointer valid for the next render — single JNI crossing.
  AndroidBitmap_unlockPixels(env, bitmap);
  AndroidBitmap_lockPixels(env, bitmap, nullptr);
}

void nativeCopyBufferToBitmap(JNIEnv *env, jclass, jlong bufferPtr,
                              jobject bitmap, jint sizeBytes) {
  if (bufferPtr == 0) return;
  void *pixels = nullptr;
  int ret = AndroidBitmap_lockPixels(env, bitmap, &pixels);
  if (ret == ANDROID_BITMAP_RESULT_SUCCESS && pixels != nullptr) {
    memcpy(pixels, reinterpret_cast<void *>(bufferPtr), sizeBytes);
    // unlockPixels calls notifyPixelsChanged() internally, which bumps the
    // Bitmap generation ID so the GPU re-uploads the texture.
    AndroidBitmap_unlockPixels(env, bitmap);
  }
}

// ==================== Pointer Helper ====================

jobject nativeGetByteBuffer(JNIEnv *env, jclass, jlong address, jint length) {
  if (address == 0) {
    return nullptr;
  }
  return env->NewDirectByteBuffer(reinterpret_cast<void *>(address), length);
}

// ==================== HardwareBuffer FBO (API 26+) ====================

// Function pointer types for runtime-resolved APIs
typedef AHardwareBuffer *(*PFN_AHardwareBuffer_fromHardwareBuffer)(
    JNIEnv *, jobject);
typedef EGLClientBuffer (*PFN_eglGetNativeClientBufferANDROID)(
    const AHardwareBuffer *);
typedef EGLImageKHR (*PFN_eglCreateImageKHR)(EGLDisplay, EGLContext, EGLenum,
                                              EGLClientBuffer,
                                              const EGLint *);
typedef EGLBoolean (*PFN_eglDestroyImageKHR)(EGLDisplay, EGLImageKHR);
typedef void (*PFN_glEGLImageTargetTexture2DOES)(GLenum, GLeglImageOES);
typedef void (*PFN_AHardwareBuffer_release)(AHardwareBuffer *);

jintArray nativeCreateFboFromHardwareBuffer(JNIEnv *env, jclass,
                                            jobject hwBuffer) {
  // Resolve AHardwareBuffer_fromHardwareBuffer via dlsym
  static auto fromHwBuffer =
      (PFN_AHardwareBuffer_fromHardwareBuffer)dlsym(
          RTLD_DEFAULT, "AHardwareBuffer_fromHardwareBuffer");
  static auto releaseHwBuffer =
      (PFN_AHardwareBuffer_release)dlsym(
          RTLD_DEFAULT, "AHardwareBuffer_release");
  if (!fromHwBuffer) {
    LOGE("dlsym AHardwareBuffer_fromHardwareBuffer failed");
    return nullptr;
  }

  // Resolve EGL/GL extension functions
  static auto getNativeClientBuffer =
      (PFN_eglGetNativeClientBufferANDROID)eglGetProcAddress(
          "eglGetNativeClientBufferANDROID");
  static auto createImageKHR =
      (PFN_eglCreateImageKHR)eglGetProcAddress("eglCreateImageKHR");
  static auto destroyImageKHR =
      (PFN_eglDestroyImageKHR)eglGetProcAddress("eglDestroyImageKHR");
  static auto imageTargetTexture2D =
      (PFN_glEGLImageTargetTexture2DOES)eglGetProcAddress(
          "glEGLImageTargetTexture2DOES");

  if (!getNativeClientBuffer || !createImageKHR || !imageTargetTexture2D) {
    LOGE("Failed to resolve EGL/GL extension functions for HardwareBuffer FBO");
    return nullptr;
  }

  AHardwareBuffer *nativeBuffer = fromHwBuffer(env, hwBuffer);
  if (!nativeBuffer) {
    LOGE("AHardwareBuffer_fromHardwareBuffer returned null");
    return nullptr;
  }

  EGLClientBuffer clientBuffer = getNativeClientBuffer(nativeBuffer);
  if (!clientBuffer) {
    LOGE("eglGetNativeClientBufferANDROID returned null");
    if (releaseHwBuffer) releaseHwBuffer(nativeBuffer);
    return nullptr;
  }

  EGLDisplay display = eglGetCurrentDisplay();
  EGLint imageAttrs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
  EGLImageKHR eglImage =
      createImageKHR(display, EGL_NO_CONTEXT,
                     EGL_NATIVE_BUFFER_ANDROID, clientBuffer, imageAttrs);
  if (eglImage == EGL_NO_IMAGE_KHR) {
    LOGE("eglCreateImageKHR failed");
    if (releaseHwBuffer) releaseHwBuffer(nativeBuffer);
    return nullptr;
  }

  // Create GL texture backed by the EGLImage
  GLuint texId = 0;
  glGenTextures(1, &texId);
  glBindTexture(GL_TEXTURE_2D, texId);
  imageTargetTexture2D(GL_TEXTURE_2D, (GLeglImageOES)eglImage);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  glBindTexture(GL_TEXTURE_2D, 0);

  // Create FBO and attach the texture
  GLuint fboId = 0;
  glGenFramebuffers(1, &fboId);
  glBindFramebuffer(GL_FRAMEBUFFER, fboId);
  glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                         texId, 0);

  GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
  glBindFramebuffer(GL_FRAMEBUFFER, 0);

  if (status != GL_FRAMEBUFFER_COMPLETE) {
    LOGE("Framebuffer not complete: 0x%x", status);
    glDeleteFramebuffers(1, &fboId);
    glDeleteTextures(1, &texId);
    if (destroyImageKHR) {
      destroyImageKHR(display, eglImage);
    }
    if (releaseHwBuffer) releaseHwBuffer(nativeBuffer);
    return nullptr;
  }

  // Return {fboId, textureId, eglImagePtrHigh, eglImagePtrLow} as int[4]
  // Pack the 64-bit eglImage pointer into two 32-bit ints
  jlong imgPtr = reinterpret_cast<jlong>(eglImage);
  jintArray result = env->NewIntArray(4);
  jint values[4] = {
      static_cast<jint>(fboId), static_cast<jint>(texId),
      static_cast<jint>((imgPtr >> 32) & 0xFFFFFFFF),
      static_cast<jint>(imgPtr & 0xFFFFFFFF)};
  env->SetIntArrayRegion(result, 0, 4, values);

  if (releaseHwBuffer) releaseHwBuffer(nativeBuffer);

  return result;
}

void nativeDestroyFboResources(JNIEnv *env, jclass, jint fboId, jint textureId,
                               jlong eglImagePtr) {
  GLuint fbo = static_cast<GLuint>(fboId);
  GLuint tex = static_cast<GLuint>(textureId);
  if (fbo != 0) glDeleteFramebuffers(1, &fbo);
  if (tex != 0) glDeleteTextures(1, &tex);

  if (eglImagePtr != 0) {
    static auto destroyImageKHR =
        (PFN_eglDestroyImageKHR)eglGetProcAddress("eglDestroyImageKHR");
    if (destroyImageKHR) {
      EGLDisplay display = eglGetCurrentDisplay();
      destroyImageKHR(display, reinterpret_cast<EGLImageKHR>(eglImagePtr));
    }
  }
}

void nativeGlFinish(JNIEnv *env, jclass) { glFinish(); }

// ==================== JNI Method Tables ====================

static JNINativeMethod playerMethods[] = {
    // Player lifecycle
    {"nativeNewPlayer", "(I)J", (void *)nativeNewPlayer},
    {"nativeNewPlayerWithConfig",
     "(IIFZIZZIZFFLjava/lang/String;IFFLjava/lang/String;)J",
     (void *)nativeNewPlayerWithConfig},
    {"nativeApplyConfig",
     "(JIFZIZZIZFFLjava/lang/String;IFFLjava/lang/String;)I",
     (void *)nativeApplyConfig},
    {"nativeDestroy", "(J)I", (void *)nativeDestroy},

    // Loading
    {"nativeLoadAnimationData", "(JLjava/lang/String;II)I",
     (void *)nativeLoadAnimationData},
    {"nativeLoadAnimationPath", "(JLjava/lang/String;II)I",
     (void *)nativeLoadAnimationPath},
    {"nativeLoadAnimation", "(JLjava/lang/String;II)I",
     (void *)nativeLoadAnimation},
    {"nativeLoadDotLottieData", "(J[BII)I", (void *)nativeLoadDotLottieData},

    // Playback control
    {"nativePlay", "(J)I", (void *)nativePlay},
    {"nativePause", "(J)I", (void *)nativePause},
    {"nativeStop", "(J)I", (void *)nativeStop},
    {"nativeRender", "(J)I", (void *)nativeRender},
    {"nativeTick", "(J)I", (void *)nativeTick},
    {"nativeRequestFrame", "(J)F", (void *)nativeRequestFrame},
    {"nativeSetFrame", "(JF)I", (void *)nativeSetFrame},
    {"nativeSeek", "(JF)I", (void *)nativeSeek},
    {"nativeResize", "(JII)I", (void *)nativeResize},
    {"nativeClear", "(J)I", (void *)nativeClear},

    // State queries
    {"nativeIsLoaded", "(J)Z", (void *)nativeIsLoaded},
    {"nativeIsPlaying", "(J)Z", (void *)nativeIsPlaying},
    {"nativeIsPaused", "(J)Z", (void *)nativeIsPaused},
    {"nativeIsStopped", "(J)Z", (void *)nativeIsStopped},
    {"nativeIsComplete", "(J)Z", (void *)nativeIsComplete},

    // Getters
    {"nativeCurrentFrame", "(J)F", (void *)nativeCurrentFrame},
    {"nativeTotalFrames", "(J)F", (void *)nativeTotalFrames},
    {"nativeDuration", "(J)F", (void *)nativeDuration},
    {"nativeLoopCount", "(J)I", (void *)nativeLoopCount},
    {"nativeSegmentDuration", "(J)F", (void *)nativeSegmentDuration},
    {"nativeAnimationSize", "(J)[F", (void *)nativeAnimationSize},

    // Buffer management
    {"nativeAllocateBuffer", "(II)J", (void *)nativeAllocateBuffer},
    {"nativeFreeBuffer", "(J)V", (void *)nativeFreeBuffer},
    {"nativeSetSwTarget", "(JJII)I", (void *)nativeSetSwTarget},
    {"nativeSetGlTarget", "(JIII)I", (void *)nativeSetGlTarget},

    // Bitmap pixel access
    {"nativeLockBitmapPixels", "(Landroid/graphics/Bitmap;)J",
     (void *)nativeLockBitmapPixels},
    {"nativeUnlockBitmapPixels", "(Landroid/graphics/Bitmap;)V",
     (void *)nativeUnlockBitmapPixels},
    {"nativeFlushBitmapPixels", "(Landroid/graphics/Bitmap;)V",
     (void *)nativeFlushBitmapPixels},
    {"nativeCopyBufferToBitmap", "(JLandroid/graphics/Bitmap;I)V",
     (void *)nativeCopyBufferToBitmap},

    // Config setters
    {"nativeSetMode", "(JI)I", (void *)nativeSetMode},
    {"nativeSetSpeed", "(JF)I", (void *)nativeSetSpeed},
    {"nativeSetLoop", "(JZ)I", (void *)nativeSetLoop},
    {"nativeSetLoopCount", "(JI)I", (void *)nativeSetLoopCount},
    {"nativeSetAutoplay", "(JZ)I", (void *)nativeSetAutoplay},
    {"nativeSetUseFrameInterpolation", "(JZ)I",
     (void *)nativeSetUseFrameInterpolation},
    {"nativeSetBackgroundColor", "(JI)I", (void *)nativeSetBackgroundColor},
    {"nativeSetSegment", "(JFF)I", (void *)nativeSetSegment},
    {"nativeClearSegment", "(J)I", (void *)nativeClearSegment},
    {"nativeSetMarker", "(JLjava/lang/String;)I", (void *)nativeSetMarker},
    {"nativeSetLayout", "(JIFF)I", (void *)nativeSetLayout},

    // Config getters
    {"nativeGetMode", "(J)I", (void *)nativeGetMode},
    {"nativeGetSpeed", "(J)F", (void *)nativeGetSpeed},
    {"nativeGetLoop", "(J)Z", (void *)nativeGetLoop},
    {"nativeGetLoopCount", "(J)I", (void *)nativeGetLoopCount},
    {"nativeGetAutoplay", "(J)Z", (void *)nativeGetAutoplay},
    {"nativeGetUseFrameInterpolation", "(J)Z",
     (void *)nativeGetUseFrameInterpolation},
    {"nativeGetBackgroundColor", "(J)I", (void *)nativeGetBackgroundColor},
    {"nativeGetSegment", "(J)[F", (void *)nativeGetSegment},
    {"nativeGetActiveMarker", "(J)Ljava/lang/String;",
     (void *)nativeGetActiveMarker},
    {"nativeGetLayout", "(J)[F", (void *)nativeGetLayout},

    // Manifest & markers
    {"nativeManifest", "(J)Ljava/lang/String;", (void *)nativeManifest},
    {"nativeMarkersCount", "(J)I", (void *)nativeMarkersCount},
    {"nativeMarker", "(JI)[Ljava/lang/String;", (void *)nativeMarker},

    // Theme
    {"nativeSetTheme", "(JLjava/lang/String;)I", (void *)nativeSetTheme},
    {"nativeResetTheme", "(J)I", (void *)nativeResetTheme},
    {"nativeSetThemeData", "(JLjava/lang/String;)I",
     (void *)nativeSetThemeData},
    {"nativeActiveThemeId", "(J)Ljava/lang/String;",
     (void *)nativeActiveThemeId},
    {"nativeActiveAnimationId", "(J)Ljava/lang/String;",
     (void *)nativeActiveAnimationId},

    // Slots
    {"nativeSetSlotsStr", "(JLjava/lang/String;)I", (void *)nativeSetSlotsStr},
    {"nativeClearSlots", "(J)I", (void *)nativeClearSlots},
    {"nativeClearSlot", "(JLjava/lang/String;)I", (void *)nativeClearSlot},
    {"nativeSetColorSlot", "(JLjava/lang/String;FFF)I",
     (void *)nativeSetColorSlot},
    {"nativeSetScalarSlot", "(JLjava/lang/String;F)I",
     (void *)nativeSetScalarSlot},
    {"nativeSetTextSlot", "(JLjava/lang/String;Ljava/lang/String;)I",
     (void *)nativeSetTextSlot},
    {"nativeSetVectorSlot", "(JLjava/lang/String;FF)I",
     (void *)nativeSetVectorSlot},
    {"nativeSetPositionSlot", "(JLjava/lang/String;FF)I",
     (void *)nativeSetPositionSlot},
    {"nativeSetImageSlotPath",
     "(JLjava/lang/String;Ljava/lang/String;)I",
     (void *)nativeSetImageSlotPath},
    {"nativeSetImageSlotDataUrl",
     "(JLjava/lang/String;Ljava/lang/String;)I",
     (void *)nativeSetImageSlotDataUrl},

    // Layer bounds
    {"nativeGetLayerBounds", "(JLjava/lang/String;)[F",
     (void *)nativeGetLayerBounds},

    // Viewport
    {"nativeSetViewport", "(JIIII)I", (void *)nativeSetViewport},

    // Poll events
    {"nativePollEvent", "(J)[I", (void *)nativePollEvent},

    // State machine
    {"nativeStateMachineLoad", "(JLjava/lang/String;)J",
     (void *)nativeStateMachineLoad},
    {"nativeStateMachineLoadData", "(JLjava/lang/String;)J",
     (void *)nativeStateMachineLoadData},
    {"nativeStateMachineRelease", "(J)V", (void *)nativeStateMachineRelease},
    {"nativeStateMachineStart", "(JLjava/lang/String;Z)I",
     (void *)nativeStateMachineStart},
    {"nativeStateMachineStop", "(J)I", (void *)nativeStateMachineStop},
    {"nativeStateMachineTick", "(J)I", (void *)nativeStateMachineTick},
    {"nativeStateMachineSetNumericInput", "(JLjava/lang/String;F)I",
     (void *)nativeStateMachineSetNumericInput},
    {"nativeStateMachineSetStringInput",
     "(JLjava/lang/String;Ljava/lang/String;)I",
     (void *)nativeStateMachineSetStringInput},
    {"nativeStateMachineSetBooleanInput", "(JLjava/lang/String;Z)I",
     (void *)nativeStateMachineSetBooleanInput},
    {"nativeStateMachineGetNumericInput", "(JLjava/lang/String;)F",
     (void *)nativeStateMachineGetNumericInput},
    {"nativeStateMachineGetStringInput",
     "(JLjava/lang/String;)Ljava/lang/String;",
     (void *)nativeStateMachineGetStringInput},
    {"nativeStateMachineGetBooleanInput", "(JLjava/lang/String;)Z",
     (void *)nativeStateMachineGetBooleanInput},
    {"nativeStateMachineCurrentState", "(J)Ljava/lang/String;",
     (void *)nativeStateMachineCurrentState},
    {"nativeStateMachineStatus", "(J)Ljava/lang/String;",
     (void *)nativeStateMachineStatus},
    {"nativeStateMachineFireEvent", "(JLjava/lang/String;)I",
     (void *)nativeStateMachineFireEvent},
    {"nativeStateMachinePostEvent", "(JIFF)I",
     (void *)nativeStateMachinePostEvent},
    {"nativeStateMachineFrameworkSetup", "(J)I",
     (void *)nativeStateMachineFrameworkSetup},
    {"nativeStateMachinePollEvent", "(J)[Ljava/lang/String;",
     (void *)nativeStateMachinePollEvent},
    {"nativeStateMachinePollInternalEvent", "(J)Ljava/lang/String;",
     (void *)nativeStateMachinePollInternalEvent},
    {"nativeGetStateMachine", "(JLjava/lang/String;)Ljava/lang/String;",
     (void *)nativeGetStateMachine},

    // HardwareBuffer FBO
    {"nativeCreateFboFromHardwareBuffer",
     "(Landroid/hardware/HardwareBuffer;)[I",
     (void *)nativeCreateFboFromHardwareBuffer},
    {"nativeDestroyFboResources", "(IIJ)V",
     (void *)nativeDestroyFboResources},
    {"nativeGlFinish", "()V", (void *)nativeGlFinish},
};

static JNINativeMethod pointerMethods[] = {
    {"nativeGetByteBuffer", "(JI)Ljava/nio/ByteBuffer;",
     (void *)nativeGetByteBuffer},
};

// JNI_OnLoad - Register all native methods
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    LOGE("Failed to get JNI environment");
    return JNI_ERR;
  }

  // Register DotLottiePlayer methods
  jclass playerClazz =
      env->FindClass("com/lottiefiles/dotlottie/core/jni/DotLottiePlayer");
  if (playerClazz == nullptr) {
    LOGE("Failed to find DotLottiePlayer class");
    return JNI_ERR;
  }

  int methodCount = sizeof(playerMethods) / sizeof(playerMethods[0]);
  if (env->RegisterNatives(playerClazz, playerMethods, methodCount) !=
      JNI_OK) {
    LOGE("Failed to register native methods");
    return JNI_ERR;
  }

  LOGI("JNI_OnLoad: Successfully registered %d DotLottiePlayer methods",
       methodCount);
  env->DeleteLocalRef(playerClazz);

  // Register Pointer methods
  jclass pointerClazz = env->FindClass("com/dotlottie/dlplayer/Pointer");
  if (pointerClazz == nullptr) {
    LOGE("Failed to find Pointer class");
    return JNI_ERR;
  }

  int pointerMethodCount = sizeof(pointerMethods) / sizeof(pointerMethods[0]);
  if (env->RegisterNatives(pointerClazz, pointerMethods, pointerMethodCount) !=
      JNI_OK) {
    LOGE("Failed to register Pointer methods");
    return JNI_ERR;
  }

  LOGI("JNI_OnLoad: Successfully registered %d Pointer methods",
       pointerMethodCount);
  env->DeleteLocalRef(pointerClazz);

  LOGI("JNI_OnLoad: Initialization complete (no cached field IDs needed)");

  return JNI_VERSION_1_6;
}
