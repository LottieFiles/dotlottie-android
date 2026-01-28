cmake_minimum_required(VERSION 3.22.1)
project(dlplayer)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to prebuilt libraries
set(LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs)

# Include directory for C API header
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Add library search path
link_directories(${LIBS_DIR}/${ANDROID_ABI})

# Create JNI bridge library
add_library(
    dlplayer
    SHARED
    jni_bridge.cpp
)

# Linker flags for Android compatibility
target_link_options(
    dlplayer
    PRIVATE
    -Wl,--hash-style=sysv
)

# Link dependencies
target_link_libraries(
    dlplayer
    -ldotlottie_player
    android
    log
)
