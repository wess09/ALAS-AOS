#include "bridge_preview.h"

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <media/NdkImage.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <mutex>
#include <queue>
#include <thread>

static jobject g_previewSurfaceObj = nullptr;
static std::mutex g_previewMutex;
static std::atomic<bool> g_hasPreview{false};
static std::thread g_renderThread;
static std::queue<AImage *> g_renderQueue;
static std::mutex g_renderMutex;
static std::condition_variable g_renderCv;
static std::atomic<bool> g_renderThreadRunning{false};
static ANativeWindow *g_pendingWindow = nullptr;

static void DrainPreviewQueueLocked() {
    while (!g_renderQueue.empty()) {
        AImage_delete(g_renderQueue.front());
        g_renderQueue.pop();
    }
}

static void RenderPreview(AImage *image, ANativeWindow *window) {
    if (!image || !window) {
        return;
    }

    int32_t width = 0;
    int32_t height = 0;
    int32_t rowStride = 0;
    int32_t pixelStride = 0;
    int dataLength = 0;
    uint8_t *data = nullptr;
    if (AImage_getWidth(image, &width) != AMEDIA_OK ||
        AImage_getHeight(image, &height) != AMEDIA_OK ||
        AImage_getPlaneRowStride(image, 0, &rowStride) != AMEDIA_OK ||
        AImage_getPlanePixelStride(image, 0, &pixelStride) != AMEDIA_OK ||
        AImage_getPlaneData(image, 0, &data, &dataLength) != AMEDIA_OK ||
        !data || pixelStride < 3) {
        return;
    }

    ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_Buffer out{};
    if (ANativeWindow_lock(window, &out, nullptr) != 0 || !out.bits) {
        return;
    }

    auto *dstBase = static_cast<uint8_t *>(out.bits);
    const int dstRowStride = out.stride * 4;
    for (int y = 0; y < height; ++y) {
        const uint8_t *src = data + static_cast<size_t>(y) * rowStride;
        uint8_t *dst = dstBase + static_cast<size_t>(y) * dstRowStride;
        if (pixelStride == 4) {
            memcpy(dst, src, static_cast<size_t>(width) * 4);
        } else {
            for (int x = 0; x < width; ++x) {
                const uint8_t *pixel = src + static_cast<size_t>(x) * pixelStride;
                dst[x * 4] = pixel[0];
                dst[x * 4 + 1] = pixel[1];
                dst[x * 4 + 2] = pixel[2];
                dst[x * 4 + 3] = pixelStride > 3 ? pixel[3] : 255;
            }
        }
    }
    ANativeWindow_unlockAndPost(window);
}

static void RenderLoop() {
    ANativeWindow *window = nullptr;
    while (g_renderThreadRunning.load(std::memory_order_acquire)) {
        AImage *image = nullptr;
        {
            std::unique_lock<std::mutex> lock(g_renderMutex);
            g_renderCv.wait(lock, [] {
                return !g_renderThreadRunning.load(std::memory_order_acquire) ||
                       !g_renderQueue.empty() || g_pendingWindow != nullptr;
            });
            if (!g_renderThreadRunning.load(std::memory_order_acquire)) {
                break;
            }
            if (g_pendingWindow) {
                if (window) {
                    ANativeWindow_release(window);
                }
                window = g_pendingWindow;
                g_pendingWindow = nullptr;
            }
            if (!g_renderQueue.empty()) {
                image = g_renderQueue.front();
                g_renderQueue.pop();
            }
        }
        if (image) {
            RenderPreview(image, window);
            AImage_delete(image);
        }
    }
    if (window) {
        ANativeWindow_release(window);
    }
}

void SetPreviewSurface(JNIEnv *env, jobject jSurface) {
    std::lock_guard<std::mutex> lock(g_previewMutex);
    if (g_previewSurfaceObj && env && env->IsSameObject(jSurface, g_previewSurfaceObj)) {
        return;
    }

    if (g_renderThreadRunning.load(std::memory_order_acquire)) {
        g_renderThreadRunning.store(false, std::memory_order_release);
        g_renderCv.notify_all();
        if (g_renderThread.joinable()) {
            g_renderThread.join();
        }
    }
    {
        std::lock_guard<std::mutex> queueLock(g_renderMutex);
        DrainPreviewQueueLocked();
        if (g_pendingWindow) {
            ANativeWindow_release(g_pendingWindow);
            g_pendingWindow = nullptr;
        }
    }
    if (g_previewSurfaceObj && env) {
        env->DeleteGlobalRef(g_previewSurfaceObj);
        g_previewSurfaceObj = nullptr;
    }

    if (jSurface && env) {
        g_previewSurfaceObj = env->NewGlobalRef(jSurface);
        ANativeWindow *window = ANativeWindow_fromSurface(env, jSurface);
        if (window) {
            g_renderThreadRunning.store(true, std::memory_order_release);
            {
                std::lock_guard<std::mutex> queueLock(g_renderMutex);
                g_pendingWindow = window;
            }
            g_renderThread = std::thread(RenderLoop);
        } else {
            env->DeleteGlobalRef(g_previewSurfaceObj);
            g_previewSurfaceObj = nullptr;
        }
    }
    g_hasPreview.store(g_renderThreadRunning.load(std::memory_order_acquire),
                       std::memory_order_release);
}

bool IsPreviewEnabled() {
    return g_hasPreview.load(std::memory_order_acquire);
}

bool DispatchPreview(AImage *image) {
    if (!image || !g_renderThreadRunning.load(std::memory_order_acquire)) {
        return false;
    }

    static auto lastDispatchTime = std::chrono::steady_clock::now();
    const auto now = std::chrono::steady_clock::now();
    if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastDispatchTime).count() <
        33) {
        return false;
    }
    lastDispatchTime = now;

    AImage *imageToDelete = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_renderMutex);
        if (!g_renderThreadRunning.load(std::memory_order_acquire)) {
            return false;
        }
        if (!g_renderQueue.empty()) {
            imageToDelete = g_renderQueue.front();
            g_renderQueue.pop();
        }
        g_renderQueue.push(image);
    }
    if (imageToDelete) {
        AImage_delete(imageToDelete);
    }
    g_renderCv.notify_one();
    return true;
}

void DrainPreviewQueue() {
    std::lock_guard<std::mutex> lock(g_renderMutex);
    DrainPreviewQueueLocked();
}
