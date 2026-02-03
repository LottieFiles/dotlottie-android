# C API Migration Progress

## Overview
Migrating dotlottie-android from UniFFI (JNA) bindings to C API (JNI) with SDL-style poll_event system.

## Status: Migration Complete

---

## Progress

- [x] Phase 1: Binary Download Script
  - [x] Create `scripts/download-binaries.sh`
  - [x] Test with user

- [x] Phase 2: JNI Bridge
  - [x] Create `dotlottie/src/main/cpp/CMakeLists.txt`
  - [x] Create `dotlottie/src/main/cpp/jni_bridge.cpp`
  - [x] Update `dotlottie/build.gradle.kts` (NDK/CMake config)

- [x] Phase 3: Kotlin Layer
  - [x] Create JNI interface (`core/jni/DotLottiePlayer.kt`)
  - [x] Create Types (`dlplayer/Types.kt`)
  - [x] Create Observers (`dlplayer/Observers.kt`)
  - [x] Create Pointer (`dlplayer/Pointer.kt`)
  - [x] Create PollEvents (`dlplayer/PollEvents.kt`)
  - [x] Create DotLottiePlayer wrapper (`dlplayer/DotLottiePlayer.kt`)
  - [x] Delete old UniFFI generated file (`dlplayer/dotlottie_player.kt`)

- [x] Phase 4: Compose Player
  - [x] Update imports (replace JNA Pointer)
  - [x] Basic playback working

- [x] Phase 5: Widget Player
  - [x] Update imports (replace JNA Pointer)
  - [x] Basic playback working

- [x] Phase 6: Remaining API
  - [x] Core API implemented (playback, state machine, themes, slots)
  - [ ] TODO: `manifest()` - returns empty (needs C API implementation)
  - [ ] TODO: `markers()` - returns empty (needs C API implementation)

- [x] Phase 7: Cleanup
  - [x] Remove UniFFI generated file
  - [x] Remove old native libraries (libjnidispatch.so, libuniffi_dotlottie_player.so)
  - [x] Remove JNA dependency from build.gradle.kts
  - [x] Clean up proguard rules
  - [x] Basic playback verified

---

## Notes

### Binary Download Script
Created `scripts/download-binaries.sh` that:
- Uses `gh` CLI (no token needed, handles auth automatically)
- Downloads from GitHub Actions artifacts (LottieFiles/dotlottie-rs)
- Installs to `dotlottie/src/main/jniLibs/` and `dotlottie/src/main/cpp/`
- Supports all 4 ABIs: arm64-v8a, armeabi-v7a, x86, x86_64

Usage:
```bash
./scripts/download-binaries.sh           # Download latest
./scripts/download-binaries.sh -l        # List available runs
./scripts/download-binaries.sh -r <ID>   # Download specific run
```

---

## Reference Files
- Reference JNI bridge: `~/repos/dotlottie-android/dotlottie/src/main/cpp/jni_bridge.cpp`
- Poll event system: `~/repos/dotlottie-rs/dotlottie-rs/src/poll_events.rs`
- C API: `~/repos/dotlottie-rs/dotlottie-rs/src/c_api/mod.rs`
