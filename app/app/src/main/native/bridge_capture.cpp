#include "bridge_capture.h"

#include "bridge_frame_buffer.h"
#include "bridge_preview.h"

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
#include <atomic>
#include <sstream>

struct NativeCapturer {
    AImageReader *reader = nullptr;
    ANativeWindow *window = nullptr;
    AImageReader_ImageListener listener{};
    int width = 0;
    int height = 0;
};

static NativeCapturer *g_capturer = nullptr;
static std::atomic<bool> g_reader_ready{false};
static std::atomic<int64_t> g_callbacks{0}, g_acquired{0}, g_written{0};
static std::atomic<int> g_acquire_status{0}, g_setup_status{0};

std::string GetCaptureDiagnostics() {
    std::ostringstream state;
    state << "reader=" << g_reader_ready.load() << " setupStatus=" << g_setup_status.load()
          << " callbacks=" << g_callbacks.load() << " acquired=" << g_acquired.load()
          << " written=" << g_written.load() << " acquireStatus=" << g_acquire_status.load()
          << " frames=" << GetFrameCount() << " plane={" << GetFrameReadDiagnostics() << "}";
    return state.str();
}

static void onImageAvailable(void *context, AImageReader *reader) {
    (void) context;
    g_callbacks.fetch_add(1);

    AImage *image = nullptr;
    const auto status = AImageReader_acquireLatestImage(reader, &image);
    g_acquire_status.store(status);
    if (status != AMEDIA_OK || !image) {
        return;
    }

    g_acquired.fetch_add(1);
    if (WriteImageToFrame(image)) g_written.fetch_add(1);

    bool handedOver = false;
    if (IsPreviewEnabled()) {
        handedOver = DispatchPreview(image);
    }

    if (!handedOver) {
        AImage_delete(image);
    }
}

jobject SetupNativeCapturer(JNIEnv *env, int width, int height) {
    ReleaseNativeCapturer();
    g_callbacks.store(0);
    g_acquired.store(0);
    g_written.store(0);
    g_acquire_status.store(0);
    g_setup_status.store(0);
    InitFrameBuffers(width, height);

    g_capturer = new NativeCapturer();
    g_capturer->width = width;
    g_capturer->height = height;

    media_status_t status = AImageReader_newWithUsage(
            width, height, AIMAGE_FORMAT_RGBA_8888,
            AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, 5,
            &g_capturer->reader);
    g_setup_status.store(status);
    if (status != AMEDIA_OK) {
        LOGE("AImageReader_newWithUsage failed: %d", status);
        delete g_capturer;
        g_capturer = nullptr;
        ReleaseFrameBuffers();
        return nullptr;
    }

    g_capturer->listener.context = g_capturer;
    g_capturer->listener.onImageAvailable = onImageAvailable;
    status = AImageReader_setImageListener(g_capturer->reader, &g_capturer->listener);
    g_setup_status.store(status);
    if (status != AMEDIA_OK) {
        LOGE("SetupNativeCapturer: AImageReader_setImageListener failed: %d", status);
        AImageReader_delete(g_capturer->reader);
        delete g_capturer;
        g_capturer = nullptr;
        ReleaseFrameBuffers();
        return nullptr;
    }

    status = AImageReader_getWindow(g_capturer->reader, &g_capturer->window);
    g_setup_status.store(status);
    if (status != AMEDIA_OK || !g_capturer->window) {
        LOGE("SetupNativeCapturer: AImageReader_getWindow failed: status=%d, window=%p",
             status, g_capturer->window);
        AImageReader_setImageListener(g_capturer->reader, nullptr);
        AImageReader_delete(g_capturer->reader);
        delete g_capturer;
        g_capturer = nullptr;
        ReleaseFrameBuffers();
        return nullptr;
    }

    jobject surface = ANativeWindow_toSurface(env, g_capturer->window);
    g_reader_ready.store(surface != nullptr);
    return surface;
}

void ReleaseNativeCapturer() {
    g_reader_ready.store(false);
    DrainPreviewQueue();

    if (g_capturer) {
        if (g_capturer->reader) {
            AImageReader_setImageListener(g_capturer->reader, nullptr);
        }
        if (g_capturer->reader) {
            AImageReader_delete(g_capturer->reader);
        }

        delete g_capturer;
        g_capturer = nullptr;
        LOGI("NativeCapturer released");
    }

    ReleaseFrameBuffers();
}
