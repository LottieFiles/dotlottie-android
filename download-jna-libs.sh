#!/bin/bash

# Simple JNA Native Library Downloader
# Downloads JAR files from: https://github.com/java-native-access/jna/raw/refs/tags/$version/lib/native/

set -e

# Configuration
JNA_VERSION="5.17.0"
PROJECT_DIR=$(pwd)
JNI_LIBS_DIR="$PROJECT_DIR/dotlottie/src/main/jniLibs"
GITHUB_BASE_URL="https://github.com/java-native-access/jna/raw/refs/tags"
TEMP_DIR="/tmp/jna_extraction_$$"

# Architecture arrays (parallel arrays)
JNA_ARCHS=("android-aarch64" "android-armv7" "android-x86" "android-x86-64")
ANDROID_ARCHS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

echo "JNA Native Library Downloader"
echo "============================="
echo "Version: $JNA_VERSION"
echo "Path: https://github.com/java-native-access/jna/tree/$JNA_VERSION/lib/native"
echo ""

# Create temp directory
mkdir -p "$TEMP_DIR"

# Cleanup function
cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

echo "Downloading and extracting native libraries..."

# Download and extract JAR files
total_extracted=0
for i in "${!JNA_ARCHS[@]}"; do
    jna_arch="${JNA_ARCHS[$i]}"
    android_arch="${ANDROID_ARCHS[$i]}"
    jar_url="$GITHUB_BASE_URL/$JNA_VERSION/lib/native/$jna_arch.jar"
    temp_jar="$TEMP_DIR/$jna_arch.jar"
    target_file="$JNI_LIBS_DIR/$android_arch/libjnidispatch.so"

    echo "Downloading $jna_arch -> $android_arch..."
    echo "URL: $jar_url"

    # Download JAR file
    if curl -f -L -o "$temp_jar" "$jar_url" 2>/dev/null; then
        echo "✓ Downloaded JAR: $jna_arch.jar"

        # Extract .so file from JAR
        if unzip -j -q "$temp_jar" "*.so" -d "$TEMP_DIR" 2>/dev/null; then
            # Find the extracted .so file and move it
            so_file=$(find "$TEMP_DIR" -name "*.so" -type f | head -1)
            if [ -n "$so_file" ] && [ -f "$so_file" ]; then
                cp "$so_file" "$target_file"
                file_size=$(du -h "$target_file" | cut -f1)
                echo "✓ Extracted: $android_arch/libjnidispatch.so ($file_size)"
                total_extracted=$((total_extracted + 1))
                rm -f "$so_file"  # Clean up temp file
            else
                echo "✗ No .so file found in JAR: $jna_arch"
            fi
        else
            echo "✗ Failed to extract from JAR: $jna_arch"
        fi
    else
        echo "✗ Failed to download JAR: $jna_arch"
        echo "  Check if file exists: $jar_url"
    fi
    echo ""
done

echo "Verifying extractions..."

# Verify extractions
verified_libs=0
for android_arch in "${ANDROID_ARCHS[@]}"; do
    so_file="$JNI_LIBS_DIR/$android_arch/libjnidispatch.so"
    if [ -f "$so_file" ]; then
        file_size=$(du -h "$so_file" | cut -f1)
        echo "✓ $android_arch/libjnidispatch.so ($file_size)"
        verified_libs=$((verified_libs + 1))
    else
        echo "✗ Missing: $android_arch/libjnidispatch.so"
    fi
done

echo ""
if [ "$verified_libs" -gt 0 ]; then
    echo "✅ Successfully extracted $verified_libs native libraries"
    echo ""
    echo "Source: https://github.com/java-native-access/jna/tree/$JNA_VERSION/lib/native"
    echo ""
    echo "Add this to your app/build.gradle if not already present:"
    echo ""
    echo "android {"
    echo "    sourceSets {"
    echo "        main {"
    echo "            jniLibs.srcDirs = ['src/main/jniLibs']"
    echo "        }"
    echo "    }"
    echo "    packagingOptions {"
    echo "        pickFirst '**/libjnidispatch.so'"
    echo "    }"
    echo "}"
    echo ""
    echo "Now build your project: ./gradlew assembleRelease"
else
    echo "❌ No libraries were extracted"
    echo ""
    echo "Debugging steps:"
    echo "1. Check if directories exist:"
    echo "   ls -la $JNI_LIBS_DIR"
    echo ""
    echo "2. Create directories if missing:"
    echo "   mkdir -p $JNI_LIBS_DIR/{arm64-v8a,armeabi-v7a,x86,x86_64}"
    echo ""
    echo "3. Test individual JAR URL (replace [arch]):"
    echo "   curl -I $GITHUB_BASE_URL/$JNA_VERSION/lib/native/[arch].jar"
    echo ""
    echo "4. Check available versions/tags:"
    echo "   https://github.com/java-native-access/jna/tags"
    exit 1
fi