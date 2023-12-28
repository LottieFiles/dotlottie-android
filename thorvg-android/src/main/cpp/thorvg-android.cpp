#include <jni.h>
#include <android/bitmap.h>
#include "LottieDrawable.h"

using namespace std;

extern "C"
JNIEXPORT jlong JNICALL
Java_com_lottiefiles_dotlottie_core_LottieNative_nCreateLottie(JNIEnv *env, jclass clazz,
        jstring contentString, jint length, jdoubleArray outValues) {
    if (tvg::Initializer::init(3, tvg::CanvasEngine::Sw) != tvg::Result::Success) {
        return 0;
    }

    const char* inputStr = env->GetStringUTFChars(contentString, nullptr);
    auto* newData = new LottieDrawable::Data(inputStr, length);
    env->ReleaseStringUTFChars(contentString, inputStr);

    jdouble * contentInfo = env->GetDoubleArrayElements(outValues, nullptr);
    if (contentInfo != nullptr) {
        contentInfo[0] = (jint) newData->mAnimation->totalFrame();
        contentInfo[1] = (jdouble) newData->mAnimation->duration();
        env->ReleaseDoubleArrayElements(outValues, contentInfo, 0);
    }

    return reinterpret_cast<jlong>(newData);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_lottiefiles_dotlottie_core_LottieNative_nSetLottieBufferSize(JNIEnv *env, jclass clazz,
        jlong lottiePtr, jobject bitmap, jfloat width, jfloat height) {
    if (lottiePtr == 0) {
        return;
    }

    auto* data = reinterpret_cast<LottieDrawable::Data*>(lottiePtr);
    void *buffer;
    if (AndroidBitmap_lockPixels(env, bitmap, &buffer) >= 0) {
        data->setBufferSize((uint32_t *) buffer, width, height);
        AndroidBitmap_unlockPixels(env, bitmap);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_lottiefiles_dotlottie_core_LottieNative_nDrawLottieFrame(JNIEnv *env, jclass clazz, jlong lottiePtr,
        jobject bitmap, jfloat frame) {
    if (lottiePtr == 0) {
        return;
    }

    auto* data = reinterpret_cast<LottieDrawable::Data*>(lottiePtr);
    void *buffer;
    if (AndroidBitmap_lockPixels(env, bitmap, &buffer) >= 0) {
        data->draw(frame);
        AndroidBitmap_unlockPixels(env, bitmap);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_lottiefiles_dotlottie_core_LottieNative_nDestroyLottie(JNIEnv *env, jclass clazz, jlong lottiePtr) {
    tvg::Initializer::term(tvg::CanvasEngine::Sw);

    if (lottiePtr == 0) {
        return;
    }

    auto* data = reinterpret_cast<LottieDrawable::Data*>(lottiePtr);
    delete data;
}
