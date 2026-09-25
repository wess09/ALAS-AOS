#include "bridge_capture.h"
#include "bridge_frame_buffer.h"
#include "bridge_input.h"
#include "bridge_internal.h"
#include "bridge_preview.h"

static jstring ping(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return env->NewStringUTF("LibBridge");
}

static void nativeSetContactSupport(JNIEnv *env, jclass clazz, jboolean supported) {
    (void) env; (void) clazz;
    SetInputContactSupport(supported == JNI_TRUE);
}

static jobject nativeGetFrameBufferBitmap(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return CreateFrameBufferBitmap(env);
}

static jbyteArray nativeGetFrameBufferBytes(JNIEnv *env, jclass clazz) {
    (void) clazz;
    FrameInfo frame = GetLockedPixels();
    if (!frame.data || frame.length == 0) {
        UnlockPixels(frame);
        return nullptr;
    }
    // 与 CreateFrameBufferBitmap 同一意图：持锁时间压进一次拷贝，拷完立即解锁
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(frame.length));
    if (bytes) {
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(frame.length),
                                reinterpret_cast<const jbyte *>(frame.data));
    }
    UnlockPixels(frame);
    return bytes;
}

static void nativeSetPreviewSurface(JNIEnv *env, jclass clazz, jobject jSurface) {
    (void) clazz;
    SetPreviewSurface(env, jSurface);
}

static jobject nativeSetupNativeCapturer(JNIEnv *env, jclass clazz, jint width, jint height) {
    (void) clazz;
    return SetupNativeCapturer(env, width, height);
}

static void nativeReleaseNativeCapturer(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    ReleaseNativeCapturer();
}

static jlong nativeGetFrameCount(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return static_cast<jlong>(GetFrameCount());
}

static jstring nativeGetCaptureDiagnostics(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return env->NewStringUTF(GetCaptureDiagnostics().c_str());
}

static JNINativeMethod gMethods[] = {
        {"ping",                  "()Ljava/lang/String;",        reinterpret_cast<void *>(ping)},
        {"setContactSupport",     "(Z)V",                         reinterpret_cast<void *>(nativeSetContactSupport)},
        {"setupNativeCapturer",   "(II)Landroid/view/Surface;",  reinterpret_cast<void *>(nativeSetupNativeCapturer)},
        {"releaseNativeCapturer", "()V",                         reinterpret_cast<void *>(nativeReleaseNativeCapturer)},
        {"setPreviewSurface",     "(Ljava/lang/Object;)V",       reinterpret_cast<void *>(nativeSetPreviewSurface)},
        {"getFrameBufferBitmap",  "()Landroid/graphics/Bitmap;", reinterpret_cast<void *>(nativeGetFrameBufferBitmap)},
        {"getFrameBufferBytes",   "()[B",                      reinterpret_cast<void *>(nativeGetFrameBufferBytes)},
        {"getFrameCount",         "()J",                         reinterpret_cast<void *>(nativeGetFrameCount)},
        {"getCaptureDiagnostics", "()Ljava/lang/String;",        reinterpret_cast<void *>(nativeGetCaptureDiagnostics)},
};

static constexpr char kNativeBridgeClass[] = "com/azurpilot/ghio/bridge/NativeBridgeLib";
static constexpr char kDriverClass[] = "com/azurpilot/ghio/bridge/DriverClass";

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;

    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK || !env) {
        return JNI_ERR;
    }

    jclass nativeLibClass = env->FindClass(kNativeBridgeClass);
    if (!nativeLibClass) {
        CheckJNIException(env, "FindClass(NativeBridgeLib)");
        return JNI_ERR;
    }

    if (env->RegisterNatives(
            nativeLibClass, gMethods,
            static_cast<jint>(sizeof(gMethods) / sizeof(gMethods[0]))) < 0) {
        CheckJNIException(env, "RegisterNatives(NativeBridgeLib)");
        env->DeleteLocalRef(nativeLibClass);
        return JNI_ERR;
    }
    env->DeleteLocalRef(nativeLibClass);

    if (!InitInputBridge(vm, env, kDriverClass)) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    (void) reserved;

    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK && env) {
        SetPreviewSurface(env, nullptr);
        ReleaseInputBridge(env);
    }
}
