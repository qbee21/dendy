package com.dendy.core.emulation

import java.nio.ByteBuffer

internal object NativeBindings {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(this) {
            if (loaded) {
                return
            }
            System.loadLibrary("dendy_libretro_bridge")
            loaded = true
        }
    }

    external fun nativeCreateBackend(): Long

    external fun nativeDestroyBackend(handle: Long)

    external fun nativeLoadCore(
        handle: Long,
        corePath: String,
        systemDir: String,
        saveDir: String,
        stateDir: String,
    ): Boolean

    external fun nativeLoadRom(
        handle: Long,
        romPath: String,
    ): Boolean

    external fun nativeRunFrame(handle: Long): Boolean

    external fun nativeGetFrameWidth(handle: Long): Int

    external fun nativeGetFrameHeight(handle: Long): Int

    external fun nativeGetFrameRate(handle: Long): Double

    external fun nativeGetSampleRate(handle: Long): Int

    external fun nativeCopyFrame(
        handle: Long,
        buffer: ByteBuffer,
    ): Boolean

    external fun nativeReadAudio(
        handle: Long,
        target: ShortArray,
        maxSamples: Int,
    ): Int

    external fun nativeSetInputMask(
        handle: Long,
        mask: Int,
    )

    external fun nativeSerialize(handle: Long): ByteArray?

    external fun nativeUnserialize(
        handle: Long,
        data: ByteArray,
    ): Boolean
}
