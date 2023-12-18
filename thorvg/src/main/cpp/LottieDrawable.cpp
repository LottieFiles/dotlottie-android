#include <thread>
#include <android/log.h>
#include "LottieDrawable.h"

LottieDrawable::Data::Data(const char *content, uint32_t length) {
    LOGI("LottieDrawable::Data::Data length=%d", length);
    mContent = content;
    mContentLength = length;

    // Generate an animation
    mAnimation = tvg::Animation::gen();
    // Acquire a picture which associated with the animation.
    auto picture = mAnimation->picture();
    if (picture->load(mContent, mContentLength, "", false) != tvg::Result::Success) {
        LOGE("Error: Lottie is not supported. Did you enable Lottie Loader?");
        return;
    }

    // Create a canvas
    mCanvas = tvg::SwCanvas::gen();
    mCanvas->push(tvg::cast<tvg::Picture>(picture));
}

void LottieDrawable::Data::setBufferSize(uint32_t *buffer, float width, float height) {
    LOGI("LottieDrawable::Data::setBufferSize width=%f, height=%f", width, height);
    mCanvas->sync();
    mCanvas->clear(false);
    mCanvas->target(buffer, (uint32_t) width, (uint32_t) width, (uint32_t) height,
            tvg::SwCanvas::ABGR8888);
    mAnimation->picture()->size(width, height);
}

void LottieDrawable::Data::draw(uint32_t frame) {
    if (!mCanvas) return;
//    LOGI("LottieDrawable::Data::draw mAnimation=%d", mAnimation->curFrame());
    mAnimation->frame(frame);
    mCanvas->update(mAnimation->picture());
    mCanvas->draw();
    mCanvas->sync();
}
