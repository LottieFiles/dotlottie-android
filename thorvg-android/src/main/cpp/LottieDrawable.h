#ifndef THORVG_ANDROID_LOTTIEDRAWABLE_H
#define THORVG_ANDROID_LOTTIEDRAWABLE_H

#include <cstdint>
#include <iostream>
#include <thorvg.h>

#define LOG_TAG "LottieDrawable"

// 로그 레벨 정의
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace LottieDrawable {

    class Data {
    public:
        Data(const char* content, uint32_t strLength);
        void setBufferSize(uint32_t* buffer, float width, float height);
        void draw(uint32_t frame);
        std::unique_ptr<tvg::Animation> mAnimation;
    private:
        std::unique_ptr<tvg::SwCanvas> mCanvas;
        const char* mContent;
        uint32_t mContentLength;
    };

} // namespace VectorDrawable

#endif //THORVG_ANDROID_LOTTIEDRAWABLE_H