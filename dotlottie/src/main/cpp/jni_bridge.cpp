#include "dotlottie_player.h"
#include <android/log.h>
#include <jni.h>
#include <string.h>

#define LOG_TAG "DotLottieJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Cached field IDs for Config class
static struct {
  jclass configClass;
  jfieldID mode;
  jfieldID loopAnimation;
  jfieldID loopCount;
  jfieldID speed;
  jfieldID useFrameInterpolation;
  jfieldID autoplay;
  jfieldID segment;
  jfieldID backgroundColor;
  jfieldID layout;
  jfieldID marker;
  jfieldID themeId;
  jfieldID stateMachineId;
  jfieldID animationId;
} gConfigFields;

// Cached field IDs for Layout class
static struct {
  jclass layoutClass;
  jfieldID fit;
  jfieldID align;
} gLayoutFields;

// Cached field IDs for Mode enum
static struct {
  jclass modeClass;
  jfieldID value;
} gModeFields;

// Cached field IDs for Fit enum
static struct {
  jclass fitClass;
  jfieldID value;
} gFitFields;

// Cached method IDs for List and Float
static struct {
  jclass listClass;
  jmethodID get;
  jmethodID size;
  jclass floatClass;
  jmethodID floatValue;
} gListFields;

// Forward declare all JNI functions
extern "C" {

// Player lifecycle
static jlong nativeNewPlayer(JNIEnv *env, jclass, jobject config);
static jint nativeDestroy(JNIEnv *env, jclass, jlong ptr);

// Config
static jint nativeInitConfig(JNIEnv *env, jclass, jobject config);

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
static jint nativeIsLoaded(JNIEnv *env, jclass, jlong ptr);
static jint nativeIsPlaying(JNIEnv *env, jclass, jlong ptr);
static jint nativeIsPaused(JNIEnv *env, jclass, jlong ptr);
static jint nativeIsStopped(JNIEnv *env, jclass, jlong ptr);
static jint nativeIsComplete(JNIEnv *env, jclass, jlong ptr);

// Getters
static jfloat nativeCurrentFrame(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeTotalFrames(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeDuration(JNIEnv *env, jclass, jlong ptr);
static jint nativeLoopCount(JNIEnv *env, jclass, jlong ptr);
static jlong nativeBufferPtr(JNIEnv *env, jclass, jlong ptr);
static jlong nativeBufferLen(JNIEnv *env, jclass, jlong ptr);
static jfloat nativeSegmentDuration(JNIEnv *env, jclass, jlong ptr);
static jfloatArray nativeAnimationSize(JNIEnv *env, jclass, jlong ptr);

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

// Viewport
static jint nativeSetViewport(JNIEnv *env, jclass, jlong ptr, jint x, jint y,
                              jint w, jint h);

// Poll events
static jobject nativePollEvent(JNIEnv *env, jclass, jlong ptr);

// State machine - returns state machine pointer
static jlong nativeStateMachineLoad(JNIEnv *env, jclass, jlong playerPtr,
                                    jstring stateMachineId);
static jlong nativeStateMachineLoadData(JNIEnv *env, jclass, jlong playerPtr,
                                        jstring data);
static void nativeStateMachineRelease(JNIEnv *env, jclass, jlong smPtr);
static jint nativeStateMachineStart(JNIEnv *env, jclass, jlong smPtr);
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

// Pointer helper
static jobject nativeGetByteBuffer(JNIEnv *env, jclass, jlong address,
                                   jint length);

} // extern "C"

// Helper to convert Kotlin Config to C config
static void convertConfig(JNIEnv *env, jobject config,
                          dotlottieDotLottieConfig *cConfig) {
  dotlottie_init_config(cConfig);

  if (config == nullptr) {
    return;
  }

  // Extract primitive fields
  cConfig->autoplay =
      env->GetBooleanField(config, gConfigFields.autoplay) == JNI_TRUE;
  cConfig->loop_animation =
      env->GetBooleanField(config, gConfigFields.loopAnimation) == JNI_TRUE;
  cConfig->use_frame_interpolation =
      env->GetBooleanField(config, gConfigFields.useFrameInterpolation) ==
      JNI_TRUE;
  cConfig->speed = env->GetFloatField(config, gConfigFields.speed);
  cConfig->loop_count =
      static_cast<uint32_t>(env->GetIntField(config, gConfigFields.loopCount));
  cConfig->background_color = static_cast<uint32_t>(
      env->GetIntField(config, gConfigFields.backgroundColor));

  // Extract Mode enum
  jobject modeObj = env->GetObjectField(config, gConfigFields.mode);
  if (modeObj != nullptr) {
    jint modeValue = env->GetIntField(modeObj, gModeFields.value);
    cConfig->mode = static_cast<dotlottieMode>(modeValue);
    env->DeleteLocalRef(modeObj);
  }

  // Extract Layout
  jobject layoutObj = env->GetObjectField(config, gConfigFields.layout);
  if (layoutObj != nullptr) {
    // Extract Fit enum
    jobject fitObj = env->GetObjectField(layoutObj, gLayoutFields.fit);
    if (fitObj != nullptr) {
      jint fitValue = env->GetIntField(fitObj, gFitFields.value);
      cConfig->layout.fit = static_cast<dotlottieDotLottieFit>(fitValue);
      env->DeleteLocalRef(fitObj);
    }

    // Extract align List<Float>
    jobject alignList = env->GetObjectField(layoutObj, gLayoutFields.align);
    if (alignList != nullptr) {
      jint size = env->CallIntMethod(alignList, gListFields.size);
      if (size >= 2) {
        jobject alignX = env->CallObjectMethod(alignList, gListFields.get, 0);
        jobject alignY = env->CallObjectMethod(alignList, gListFields.get, 1);
        if (alignX != nullptr && alignY != nullptr) {
          cConfig->layout.align_x =
              env->CallFloatMethod(alignX, gListFields.floatValue);
          cConfig->layout.align_y =
              env->CallFloatMethod(alignY, gListFields.floatValue);
          env->DeleteLocalRef(alignX);
          env->DeleteLocalRef(alignY);
        }
      }
      env->DeleteLocalRef(alignList);
    }
    env->DeleteLocalRef(layoutObj);
  }

  // Extract segment List<Float>
  jobject segmentList = env->GetObjectField(config, gConfigFields.segment);
  if (segmentList != nullptr) {
    jint size = env->CallIntMethod(segmentList, gListFields.size);
    if (size >= 2) {
      jobject startObj = env->CallObjectMethod(segmentList, gListFields.get, 0);
      jobject endObj = env->CallObjectMethod(segmentList, gListFields.get, 1);
      if (startObj != nullptr && endObj != nullptr) {
        cConfig->segment_start =
            env->CallFloatMethod(startObj, gListFields.floatValue);
        cConfig->segment_end =
            env->CallFloatMethod(endObj, gListFields.floatValue);
        env->DeleteLocalRef(startObj);
        env->DeleteLocalRef(endObj);
      }
    }
    env->DeleteLocalRef(segmentList);
  }

  // Extract string fields
  jstring markerStr =
      (jstring)env->GetObjectField(config, gConfigFields.marker);
  if (markerStr != nullptr) {
    const char *markerChars = env->GetStringUTFChars(markerStr, nullptr);
    if (markerChars != nullptr) {
      strncpy(cConfig->marker.value, markerChars,
              dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1);
      cConfig->marker.value[dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1] = '\0';
      env->ReleaseStringUTFChars(markerStr, markerChars);
    }
    env->DeleteLocalRef(markerStr);
  }

  jstring themeIdStr =
      (jstring)env->GetObjectField(config, gConfigFields.themeId);
  if (themeIdStr != nullptr) {
    const char *themeIdChars = env->GetStringUTFChars(themeIdStr, nullptr);
    if (themeIdChars != nullptr) {
      strncpy(cConfig->theme_id.value, themeIdChars,
              dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1);
      cConfig->theme_id.value[dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1] = '\0';
      env->ReleaseStringUTFChars(themeIdStr, themeIdChars);
    }
    env->DeleteLocalRef(themeIdStr);
  }

  jstring stateMachineIdStr =
      (jstring)env->GetObjectField(config, gConfigFields.stateMachineId);
  if (stateMachineIdStr != nullptr) {
    const char *stateMachineIdChars =
        env->GetStringUTFChars(stateMachineIdStr, nullptr);
    if (stateMachineIdChars != nullptr) {
      strncpy(cConfig->state_machine_id.value, stateMachineIdChars,
              dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1);
      cConfig->state_machine_id.value[dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1] =
          '\0';
      env->ReleaseStringUTFChars(stateMachineIdStr, stateMachineIdChars);
    }
    env->DeleteLocalRef(stateMachineIdStr);
  }

  jstring animationIdStr =
      (jstring)env->GetObjectField(config, gConfigFields.animationId);
  if (animationIdStr != nullptr) {
    const char *animationIdChars =
        env->GetStringUTFChars(animationIdStr, nullptr);
    if (animationIdChars != nullptr) {
      strncpy(cConfig->animation_id.value, animationIdChars,
              dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1);
      cConfig->animation_id.value[dotlottieDOTLOTTIE_MAX_STR_LENGTH - 1] = '\0';
      env->ReleaseStringUTFChars(animationIdStr, animationIdChars);
    }
    env->DeleteLocalRef(animationIdStr);
  }
}

// JNI method implementations

jlong nativeNewPlayer(JNIEnv *env, jclass, jobject config) {
  dotlottieDotLottieConfig cConfig;
  convertConfig(env, config, &cConfig);

  dotlottieDotLottiePlayer *player = dotlottie_new_player(&cConfig);
  return reinterpret_cast<jlong>(player);
}

jint nativeDestroy(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_destroy(player);
}

jint nativeInitConfig(JNIEnv *env, jclass, jobject config) {
  // Config initialization handled in Kotlin
  return 0;
}

jint nativeLoadAnimationData(JNIEnv *env, jclass, jlong ptr, jstring data,
                             jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cData = env->GetStringUTFChars(data, nullptr);
  int32_t result = dotlottie_load_animation_data(player, cData, width, height);
  env->ReleaseStringUTFChars(data, cData);
  return result;
}

jint nativeLoadAnimationPath(JNIEnv *env, jclass, jlong ptr, jstring path,
                             jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cPath = env->GetStringUTFChars(path, nullptr);
  int32_t result = dotlottie_load_animation_path(player, cPath, width, height);
  env->ReleaseStringUTFChars(path, cPath);
  return result;
}

jint nativeLoadAnimation(JNIEnv *env, jclass, jlong ptr, jstring id, jint width,
                         jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cId = env->GetStringUTFChars(id, nullptr);
  int32_t result = dotlottie_load_animation(player, cId, width, height);
  env->ReleaseStringUTFChars(id, cId);
  return result;
}

jint nativeLoadDotLottieData(JNIEnv *env, jclass, jlong ptr, jbyteArray data,
                             jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  jbyte *bytes = env->GetByteArrayElements(data, nullptr);
  jsize len = env->GetArrayLength(data);
  int32_t result = dotlottie_load_dotlottie_data(
      player, reinterpret_cast<const char *>(bytes), len, width, height);
  env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
  return result;
}

jint nativePlay(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_play(player);
}

jint nativePause(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_pause(player);
}

jint nativeStop(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_stop(player);
}

jint nativeRender(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_render(player);
}

jint nativeTick(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_tick(player);
}

jfloat nativeRequestFrame(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  float frame = 0.0f;
  dotlottie_request_frame(player, &frame);
  return frame;
}

jint nativeSetFrame(JNIEnv *env, jclass, jlong ptr, jfloat frame) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_set_frame(player, frame);
}

jint nativeSeek(JNIEnv *env, jclass, jlong ptr, jfloat time) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_seek(player, time);
}

jint nativeResize(JNIEnv *env, jclass, jlong ptr, jint width, jint height) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_resize(player, width, height);
}

jint nativeClear(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_clear(player);
}

jint nativeIsLoaded(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_is_loaded(player);
}

jint nativeIsPlaying(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_is_playing(player);
}

jint nativeIsPaused(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_is_paused(player);
}

jint nativeIsStopped(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_is_stopped(player);
}

jint nativeIsComplete(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_is_complete(player);
}

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
  dotlottie_loop_count(player, &count);
  return static_cast<jint>(count);
}

jlong nativeBufferPtr(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const uint32_t *bufferPtr = nullptr;
  dotlottie_buffer_ptr(player, &bufferPtr);
  return reinterpret_cast<jlong>(bufferPtr);
}

jlong nativeBufferLen(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  uint64_t len = 0;
  dotlottie_buffer_len(player, &len);
  return static_cast<jlong>(len);
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

jint nativeSetTheme(JNIEnv *env, jclass, jlong ptr, jstring themeId) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cThemeId = env->GetStringUTFChars(themeId, nullptr);
  int32_t result = dotlottie_set_theme(player, cThemeId);
  env->ReleaseStringUTFChars(themeId, cThemeId);
  return result;
}

jint nativeResetTheme(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_reset_theme(player);
}

jint nativeSetThemeData(JNIEnv *env, jclass, jlong ptr, jstring themeData) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cThemeData = env->GetStringUTFChars(themeData, nullptr);
  int32_t result = dotlottie_set_theme_data(player, cThemeData);
  env->ReleaseStringUTFChars(themeData, cThemeData);
  return result;
}

jstring nativeActiveThemeId(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  char result[dotlottieDOTLOTTIE_MAX_STR_LENGTH] = {0};
  dotlottie_active_theme_id(player, result);
  return env->NewStringUTF(result);
}

jstring nativeActiveAnimationId(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  char result[dotlottieDOTLOTTIE_MAX_STR_LENGTH] = {0};
  dotlottie_active_animation_id(player, result);
  return env->NewStringUTF(result);
}

// Slots
jint nativeSetSlotsStr(JNIEnv *env, jclass, jlong ptr, jstring slotsJson) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cJson = env->GetStringUTFChars(slotsJson, nullptr);
  int32_t result = dotlottie_set_slots_str(player, cJson);
  env->ReleaseStringUTFChars(slotsJson, cJson);
  return result;
}

jint nativeClearSlots(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_clear_slots(player);
}

jint nativeClearSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  int32_t result = dotlottie_clear_slot(player, cSlotId);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return result;
}

jint nativeSetColorSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                        jfloat r, jfloat g, jfloat b) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  int32_t result = dotlottie_set_color_slot(player, cSlotId, r, g, b);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return result;
}

jint nativeSetScalarSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                         jfloat value) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  int32_t result = dotlottie_set_scalar_slot(player, cSlotId, value);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  return result;
}

jint nativeSetTextSlot(JNIEnv *env, jclass, jlong ptr, jstring slotId,
                       jstring text) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  const char *cSlotId = env->GetStringUTFChars(slotId, nullptr);
  const char *cText = env->GetStringUTFChars(text, nullptr);
  int32_t result = dotlottie_set_text_slot(player, cSlotId, cText);
  env->ReleaseStringUTFChars(slotId, cSlotId);
  env->ReleaseStringUTFChars(text, cText);
  return result;
}

// Viewport
jint nativeSetViewport(JNIEnv *env, jclass, jlong ptr, jint x, jint y, jint w,
                       jint h) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  return dotlottie_set_viewport(player, x, y, w, h);
}

// Poll events - returns array [eventType, data1, data2] or null
jobject nativePollEvent(JNIEnv *env, jclass, jlong ptr) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(ptr);
  dotlottieDotLottiePlayerEvent event;

  int32_t result = dotlottie_poll_event(player, &event);
  if (result != 1) {
    return nullptr; // No event available
  }

  // Create int array [eventType, data (as int bits)]
  jintArray arr = env->NewIntArray(3);
  jint values[3];
  values[0] = static_cast<jint>(event.event_type);

  // Store float data as int bits for Frame/Render, or loop count for Loop
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

// State machine methods
jlong nativeStateMachineLoad(JNIEnv *env, jclass, jlong playerPtr,
                             jstring stateMachineId) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(playerPtr);
  const char *cId = env->GetStringUTFChars(stateMachineId, nullptr);
  dotlottieStateMachineEngine *sm = dotlottie_state_machine_load(player, cId);
  env->ReleaseStringUTFChars(stateMachineId, cId);
  return reinterpret_cast<jlong>(sm);
}

jlong nativeStateMachineLoadData(JNIEnv *env, jclass, jlong playerPtr,
                                 jstring data) {
  auto *player = reinterpret_cast<dotlottieDotLottiePlayer *>(playerPtr);
  const char *cData = env->GetStringUTFChars(data, nullptr);
  dotlottieStateMachineEngine *sm =
      dotlottie_state_machine_load_data(player, cData);
  env->ReleaseStringUTFChars(data, cData);
  return reinterpret_cast<jlong>(sm);
}

void nativeStateMachineRelease(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  if (sm != nullptr) {
    dotlottie_state_machine_release(sm);
  }
}

jint nativeStateMachineStart(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  dotlottieDotLottieOpenUrlPolicy policy = {};
  policy.require_user_interaction = false;
  return dotlottie_state_machine_start(sm, &policy);
}

jint nativeStateMachineStop(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  return dotlottie_state_machine_stop(sm);
}

jint nativeStateMachineTick(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  return dotlottie_state_machine_tick(sm);
}

jint nativeStateMachineSetNumericInput(JNIEnv *env, jclass, jlong smPtr,
                                       jstring key, jfloat value) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  int32_t result = dotlottie_state_machine_set_numeric_input(sm, cKey, value);
  env->ReleaseStringUTFChars(key, cKey);
  return result;
}

jint nativeStateMachineSetStringInput(JNIEnv *env, jclass, jlong smPtr,
                                      jstring key, jstring value) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  const char *cValue = env->GetStringUTFChars(value, nullptr);
  int32_t result = dotlottie_state_machine_set_string_input(sm, cKey, cValue);
  env->ReleaseStringUTFChars(key, cKey);
  env->ReleaseStringUTFChars(value, cValue);
  return result;
}

jint nativeStateMachineSetBooleanInput(JNIEnv *env, jclass, jlong smPtr,
                                       jstring key, jboolean value) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  int32_t result =
      dotlottie_state_machine_set_boolean_input(sm, cKey, value == JNI_TRUE);
  env->ReleaseStringUTFChars(key, cKey);
  return result;
}

jfloat nativeStateMachineGetNumericInput(JNIEnv *env, jclass, jlong smPtr,
                                         jstring key) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  float result = 0.0f;
  dotlottie_state_machine_get_numeric_input(sm, cKey, &result);
  env->ReleaseStringUTFChars(key, cKey);
  return result;
}

jstring nativeStateMachineGetStringInput(JNIEnv *env, jclass, jlong smPtr,
                                         jstring key) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  char result[dotlottieDOTLOTTIE_MAX_STR_LENGTH] = {0};
  dotlottie_state_machine_get_string_input(sm, cKey, result);
  env->ReleaseStringUTFChars(key, cKey);
  return env->NewStringUTF(result);
}

jboolean nativeStateMachineGetBooleanInput(JNIEnv *env, jclass, jlong smPtr,
                                           jstring key) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  const char *cKey = env->GetStringUTFChars(key, nullptr);
  bool result = false;
  dotlottie_state_machine_get_boolean_input(sm, cKey, &result);
  env->ReleaseStringUTFChars(key, cKey);
  return result ? JNI_TRUE : JNI_FALSE;
}

jstring nativeStateMachineCurrentState(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  char result[dotlottieDOTLOTTIE_MAX_STR_LENGTH] = {0};
  dotlottie_state_machine_current_state(sm, result);
  return env->NewStringUTF(result);
}

jstring nativeStateMachineStatus(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  char result[dotlottieDOTLOTTIE_MAX_STR_LENGTH] = {0};
  dotlottie_state_machine_status(sm, result);
  return env->NewStringUTF(result);
}

jint nativeStateMachineFireEvent(JNIEnv *env, jclass, jlong smPtr,
                                 jstring eventName) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  const char *cName = env->GetStringUTFChars(eventName, nullptr);
  int32_t result = dotlottie_state_machine_fire_event(sm, cName);
  env->ReleaseStringUTFChars(eventName, cName);
  return result;
}

jint nativeStateMachinePostEvent(JNIEnv *env, jclass, jlong smPtr,
                                 jint eventTag, jfloat x, jfloat y) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
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

  return dotlottie_state_machine_post_event(sm, &event);
}

jint nativeStateMachineFrameworkSetup(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  uint16_t result = 0;
  dotlottie_state_machine_framework_setup(sm, &result);
  return static_cast<jint>(result);
}

// Poll state machine events - returns array [eventType, str1, str2, str3,
// numOld, numNew, boolOld, boolNew] or null
jobject nativeStateMachinePollEvent(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  dotlottieStateMachineEvent event;

  int32_t result = dotlottie_state_machine_poll_event(sm, &event);
  if (result != 1) {
    return nullptr; // No event available
  }

  // Create array to hold event data
  // We'll use a string array where index 0 is the event type
  jclass stringClass = env->FindClass("java/lang/String");
  jobjectArray arr = env->NewObjectArray(8, stringClass, nullptr);

  // Set event type
  char eventTypeStr[16];
  snprintf(eventTypeStr, sizeof(eventTypeStr), "%d",
           static_cast<int>(event.event_type));
  env->SetObjectArrayElement(arr, 0, env->NewStringUTF(eventTypeStr));

  switch (event.event_type) {
  case dotlottieStateMachineEventType::StateMachineTransition:
  case dotlottieStateMachineEventType::StateMachineStateEntered:
  case dotlottieStateMachineEventType::StateMachineStateExit:
  case dotlottieStateMachineEventType::StateMachineCustomEvent:
  case dotlottieStateMachineEventType::StateMachineError:
  case dotlottieStateMachineEventType::StateMachineStringInputChange:
    env->SetObjectArrayElement(arr, 1,
                               env->NewStringUTF(event.data.strings.str1));
    env->SetObjectArrayElement(arr, 2,
                               env->NewStringUTF(event.data.strings.str2));
    env->SetObjectArrayElement(arr, 3,
                               env->NewStringUTF(event.data.strings.str3));
    break;
  case dotlottieStateMachineEventType::StateMachineNumericInputChange: {
    env->SetObjectArrayElement(arr, 1,
                               env->NewStringUTF(event.data.numeric.name));
    char oldVal[32], newVal[32];
    snprintf(oldVal, sizeof(oldVal), "%f", event.data.numeric.old_value);
    snprintf(newVal, sizeof(newVal), "%f", event.data.numeric.new_value);
    env->SetObjectArrayElement(arr, 4, env->NewStringUTF(oldVal));
    env->SetObjectArrayElement(arr, 5, env->NewStringUTF(newVal));
    break;
  }
  case dotlottieStateMachineEventType::StateMachineBooleanInputChange: {
    env->SetObjectArrayElement(arr, 1,
                               env->NewStringUTF(event.data.boolean.name));
    env->SetObjectArrayElement(
        arr, 6,
        env->NewStringUTF(event.data.boolean.old_value ? "true" : "false"));
    env->SetObjectArrayElement(
        arr, 7,
        env->NewStringUTF(event.data.boolean.new_value ? "true" : "false"));
    break;
  }
  case dotlottieStateMachineEventType::StateMachineInputFired:
    env->SetObjectArrayElement(arr, 1,
                               env->NewStringUTF(event.data.strings.str1));
    break;
  default:
    break;
  }

  return arr;
}

jstring nativeStateMachinePollInternalEvent(JNIEnv *env, jclass, jlong smPtr) {
  auto *sm = reinterpret_cast<dotlottieStateMachineEngine *>(smPtr);
  dotlottieStateMachineInternalEvent event;

  int32_t result = dotlottie_state_machine_poll_internal_event(sm, &event);
  if (result != 1) {
    return nullptr;
  }

  return env->NewStringUTF(event.message);
}

jobject nativeGetByteBuffer(JNIEnv *env, jclass, jlong address, jint length) {
  if (address == 0) {
    return nullptr;
  }
  return env->NewDirectByteBuffer(reinterpret_cast<void *>(address), length);
}

// JNI method tables
static JNINativeMethod playerMethods[] = {
    // Player lifecycle
    {"nativeNewPlayer", "(Ljava/lang/Object;)J", (void *)nativeNewPlayer},
    {"nativeDestroy", "(J)I", (void *)nativeDestroy},

    // Config
    {"nativeInitConfig", "(Ljava/lang/Object;)I", (void *)nativeInitConfig},

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
    {"nativeIsLoaded", "(J)I", (void *)nativeIsLoaded},
    {"nativeIsPlaying", "(J)I", (void *)nativeIsPlaying},
    {"nativeIsPaused", "(J)I", (void *)nativeIsPaused},
    {"nativeIsStopped", "(J)I", (void *)nativeIsStopped},
    {"nativeIsComplete", "(J)I", (void *)nativeIsComplete},

    // Getters
    {"nativeCurrentFrame", "(J)F", (void *)nativeCurrentFrame},
    {"nativeTotalFrames", "(J)F", (void *)nativeTotalFrames},
    {"nativeDuration", "(J)F", (void *)nativeDuration},
    {"nativeLoopCount", "(J)I", (void *)nativeLoopCount},
    {"nativeBufferPtr", "(J)J", (void *)nativeBufferPtr},
    {"nativeBufferLen", "(J)J", (void *)nativeBufferLen},
    {"nativeSegmentDuration", "(J)F", (void *)nativeSegmentDuration},
    {"nativeAnimationSize", "(J)[F", (void *)nativeAnimationSize},

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
    {"nativeStateMachineStart", "(J)I", (void *)nativeStateMachineStart},
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
  if (env->RegisterNatives(playerClazz, playerMethods, methodCount) != JNI_OK) {
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

  // Cache Config class field IDs
  jclass configClass = env->FindClass("com/dotlottie/dlplayer/Config");
  if (configClass == nullptr) {
    LOGE("Failed to find Config class");
    return JNI_ERR;
  }
  gConfigFields.configClass = (jclass)env->NewGlobalRef(configClass);
  gConfigFields.mode =
      env->GetFieldID(configClass, "mode", "Lcom/dotlottie/dlplayer/Mode;");
  gConfigFields.loopAnimation =
      env->GetFieldID(configClass, "loopAnimation", "Z");
  gConfigFields.loopCount = env->GetFieldID(configClass, "loopCount", "I");
  gConfigFields.speed = env->GetFieldID(configClass, "speed", "F");
  gConfigFields.useFrameInterpolation =
      env->GetFieldID(configClass, "useFrameInterpolation", "Z");
  gConfigFields.autoplay = env->GetFieldID(configClass, "autoplay", "Z");
  gConfigFields.segment =
      env->GetFieldID(configClass, "segment", "Ljava/util/List;");
  gConfigFields.backgroundColor =
      env->GetFieldID(configClass, "backgroundColor", "I");
  gConfigFields.layout =
      env->GetFieldID(configClass, "layout", "Lcom/dotlottie/dlplayer/Layout;");
  gConfigFields.marker =
      env->GetFieldID(configClass, "marker", "Ljava/lang/String;");
  gConfigFields.themeId =
      env->GetFieldID(configClass, "themeId", "Ljava/lang/String;");
  gConfigFields.stateMachineId =
      env->GetFieldID(configClass, "stateMachineId", "Ljava/lang/String;");
  gConfigFields.animationId =
      env->GetFieldID(configClass, "animationId", "Ljava/lang/String;");
  env->DeleteLocalRef(configClass);

  // Cache Layout class field IDs
  jclass layoutClass = env->FindClass("com/dotlottie/dlplayer/Layout");
  if (layoutClass == nullptr) {
    LOGE("Failed to find Layout class");
    return JNI_ERR;
  }
  gLayoutFields.layoutClass = (jclass)env->NewGlobalRef(layoutClass);
  gLayoutFields.fit =
      env->GetFieldID(layoutClass, "fit", "Lcom/dotlottie/dlplayer/Fit;");
  gLayoutFields.align =
      env->GetFieldID(layoutClass, "align", "Ljava/util/List;");
  env->DeleteLocalRef(layoutClass);

  // Cache Mode enum field IDs
  jclass modeClass = env->FindClass("com/dotlottie/dlplayer/Mode");
  if (modeClass == nullptr) {
    LOGE("Failed to find Mode class");
    return JNI_ERR;
  }
  gModeFields.modeClass = (jclass)env->NewGlobalRef(modeClass);
  gModeFields.value = env->GetFieldID(modeClass, "value", "I");
  env->DeleteLocalRef(modeClass);

  // Cache Fit enum field IDs
  jclass fitClass = env->FindClass("com/dotlottie/dlplayer/Fit");
  if (fitClass == nullptr) {
    LOGE("Failed to find Fit class");
    return JNI_ERR;
  }
  gFitFields.fitClass = (jclass)env->NewGlobalRef(fitClass);
  gFitFields.value = env->GetFieldID(fitClass, "value", "I");
  env->DeleteLocalRef(fitClass);

  // Cache List and Float method IDs
  jclass listClass = env->FindClass("java/util/List");
  if (listClass == nullptr) {
    LOGE("Failed to find List class");
    return JNI_ERR;
  }
  gListFields.listClass = (jclass)env->NewGlobalRef(listClass);
  gListFields.get = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");
  gListFields.size = env->GetMethodID(listClass, "size", "()I");
  env->DeleteLocalRef(listClass);

  jclass floatClass = env->FindClass("java/lang/Float");
  if (floatClass == nullptr) {
    LOGE("Failed to find Float class");
    return JNI_ERR;
  }
  gListFields.floatClass = (jclass)env->NewGlobalRef(floatClass);
  gListFields.floatValue = env->GetMethodID(floatClass, "floatValue", "()F");
  env->DeleteLocalRef(floatClass);

  LOGI("JNI_OnLoad: Successfully cached all field IDs");

  return JNI_VERSION_1_6;
}
