package com.dendy.core.emulation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import com.dendy.core.model.RomEntry
import com.dendy.core.model.SaveSlot
import com.dendy.core.model.SaveStateSummary
import com.dendy.core.model.VirtualButton
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StaticCoreProvider : CoreProvider {
    private val supportedAbis = listOf("arm64-v8a", "x86_64")

    override fun defaultCore(): CoreDescriptor = descriptorFor(resolvePreferredAbi())

    override fun listCores(): List<CoreDescriptor> = supportedAbis.map(::descriptorFor)

    private fun descriptorFor(abi: String): CoreDescriptor {
        return CoreDescriptor(
            coreId = "mesen-libretro",
            abi = abi,
            coreBinaryPath = "cores/$abi/mesen_libretro_android.so",
            displayName = "Mesen libretro",
        )
    }

    private fun resolvePreferredAbi(): String {
        resolveRuntimeArch()?.let { runtimeAbi ->
            if (runtimeAbi in supportedAbis) {
                return runtimeAbi
            }
        }
        val available = Build.SUPPORTED_ABIS.toList()
        return supportedAbis.firstOrNull(available::contains) ?: supportedAbis.first()
    }

    protected fun resolveRuntimeArch(): String? {
        val arch = System.getProperty("os.arch").orEmpty().lowercase()
        return when {
            "x86_64" in arch || "amd64" in arch -> "x86_64"
            "aarch64" in arch || "arm64" in arch -> "arm64-v8a"
            else -> null
        }
    }
}

class AndroidRuntimeCoreProvider(
    private val context: Context,
) : CoreProvider {
    private val fallback = StaticCoreProvider()
    private val supportedAbis = listOf("arm64-v8a", "x86_64")

    override fun defaultCore(): CoreDescriptor = descriptorFor(resolveProcessAbi())

    override fun listCores(): List<CoreDescriptor> = supportedAbis.map(::descriptorFor)

    private fun descriptorFor(abi: String): CoreDescriptor {
        return CoreDescriptor(
            coreId = "mesen-libretro",
            abi = abi,
            coreBinaryPath = "cores/$abi/mesen_libretro_android.so",
            displayName = "Mesen libretro",
        )
    }

    private fun resolveProcessAbi(): String {
        val nativeLibDirAbi = File(context.applicationInfo.nativeLibraryDir ?: "").name
        if (nativeLibDirAbi in supportedAbis) {
            return nativeLibDirAbi
        }
        val runtimeArch = System.getProperty("os.arch").orEmpty().lowercase()
        when {
            "x86_64" in runtimeArch || "amd64" in runtimeArch -> return "x86_64"
            "aarch64" in runtimeArch || "arm64" in runtimeArch -> return "arm64-v8a"
        }
        return fallback.defaultCore().abi
    }
}

class LibretroEmulationSessionFactory(
    private val appContext: Context,
) : EmulationSessionFactory {
    override fun create(coreProvider: CoreProvider): EmulationSession {
        return LibretroEmulationSession(
            coreProvider = coreProvider,
            bridge = NativeLibretroBridge(appContext),
        )
    }
}

private class LibretroEmulationSession(
    private val coreProvider: CoreProvider,
    private val bridge: LibretroBridge,
) : EmulationSession {
    override var status: SessionStatus = SessionStatus.CREATED
        private set

    override val rendererHost: RendererHost = bridge.rendererHost
    override val isPlaceholder: Boolean = false

    override fun load(romEntry: RomEntry) {
        bridge.attachCore(coreProvider.defaultCore())
        bridge.loadRom(romEntry)
        status = SessionStatus.LOADED
    }

    override fun start() {
        bridge.start()
        status = SessionStatus.RUNNING
    }

    override fun pause() {
        bridge.pause()
        status = SessionStatus.PAUSED
    }

    override fun resume() {
        bridge.resume()
        status = SessionStatus.RUNNING
    }

    override fun stop() {
        bridge.stop()
        status = SessionStatus.STOPPED
    }

    override fun release() {
        bridge.release()
        status = SessionStatus.RELEASED
    }

    override fun saveState(slot: SaveSlot): SaveStateSummary? = bridge.saveState(slot)

    override fun loadState(summary: SaveStateSummary) {
        bridge.loadState(summary)
    }

    override fun inputSink(): InputSink = bridge.inputSink()
}

private class NativeLibretroBridge(
    context: Context,
) : LibretroBridge, FrameProvider {
    private val appContext = context.applicationContext
    private val coreInstaller = CoreBinaryInstaller(appContext)
    private val stateRoot = File(appContext.filesDir, "states")
    private val systemDir = File(appContext.filesDir, "system")
    private val saveDir = File(appContext.filesDir, "saves")
    private val audioPlayer = LibretroAudioPlayer()
    private val inputMask = AtomicInteger(0)
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val frameLoopLock = Object()
    private val inputSink = NativeInputSink(inputMask) { mask ->
        val handle = nativeHandle
        if (handle != 0L) {
            NativeBindings.nativeSetInputMask(handle, mask)
        }
    }
    private val nativeHandle = run {
        NativeBindings.ensureLoaded()
        NativeBindings.nativeCreateBackend()
    }
    private var installedCore: File? = null
    private var currentRom: RomEntry? = null
    private var frameLoopThread: Thread? = null
    private var released = false

    override val rendererHost: LibretroGlRendererHost = LibretroGlRendererHost(
        frameProvider = this,
        onHardwareInput = { button, pressed -> inputSink.send(button, pressed) },
    )

    override fun attachCore(descriptor: CoreDescriptor) {
        check(!released) { "Bridge already released" }
        val installed = coreInstaller.install(descriptor)
        installedCore = installed
        systemDir.mkdirs()
        saveDir.mkdirs()
        stateRoot.mkdirs()
        val loaded = NativeBindings.nativeLoadCore(
            handle = nativeHandle,
            corePath = installed.absolutePath,
            systemDir = systemDir.absolutePath,
            saveDir = saveDir.absolutePath,
            stateDir = stateRoot.absolutePath,
        )
        check(loaded) { "Failed to load libretro core from ${installed.absolutePath}" }
    }

    override fun loadRom(romEntry: RomEntry) {
        check(installedCore != null) { "Core must be attached before ROM load" }
        val romFile = File(romEntry.location.absolutePath)
        check(romFile.exists()) { "ROM file does not exist: ${romFile.absolutePath}" }
        saveStateDir(romEntry).mkdirs()
        val loaded = NativeBindings.nativeLoadRom(
            handle = nativeHandle,
            romPath = romFile.absolutePath,
        )
        check(loaded) { "Failed to load ROM ${romFile.absolutePath}" }
        currentRom = romEntry
        rendererHost.updateFrameInfo(
            width = NativeBindings.nativeGetFrameWidth(nativeHandle),
            height = NativeBindings.nativeGetFrameHeight(nativeHandle),
        )
        audioPlayer.configure(
            sampleRate = NativeBindings.nativeGetSampleRate(nativeHandle).coerceAtLeast(22_050),
            audioReader = { target ->
                NativeBindings.nativeReadAudio(nativeHandle, target, target.size)
            },
        )
        NativeBindings.nativeSetInputMask(nativeHandle, inputMask.get())
    }

    override fun start() {
        if (running.compareAndSet(false, true)) {
            paused.set(false)
            rendererHost.resume()
            audioPlayer.start()
            frameLoopThread = Thread(
                {
                    while (running.get()) {
                        if (paused.get()) {
                            synchronized(frameLoopLock) {
                                while (running.get() && paused.get()) {
                                    frameLoopLock.wait(25L)
                                }
                            }
                            continue
                        }
                        NativeBindings.nativeRunFrame(nativeHandle)
                        rendererHost.requestFrame()
                    }
                },
                "dendy-libretro-loop",
            ).apply { start() }
        } else {
            resume()
        }
    }

    override fun pause() {
        paused.set(true)
        audioPlayer.pause()
        rendererHost.pause()
    }

    override fun resume() {
        paused.set(false)
        rendererHost.resume()
        audioPlayer.resume()
        synchronized(frameLoopLock) {
            frameLoopLock.notifyAll()
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        synchronized(frameLoopLock) {
            frameLoopLock.notifyAll()
        }
        frameLoopThread?.join(1_000L)
        frameLoopThread = null
        audioPlayer.stop()
        rendererHost.pause()
    }

    override fun saveState(slot: SaveSlot): SaveStateSummary? {
        val rom = currentRom ?: return null
        val payload = NativeBindings.nativeSerialize(nativeHandle) ?: return null
        val stateFile = saveStateFile(rom, slot)
        stateFile.parentFile?.mkdirs()
        stateFile.writeBytes(payload)
        return SaveStateSummary(
            romId = rom.id,
            slot = slot,
            timestampLabel = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
            ).format(Date()),
        )
    }

    override fun loadState(summary: SaveStateSummary) {
        val rom = currentRom ?: return
        if (rom.id != summary.romId) {
            return
        }
        val stateFile = saveStateFile(rom, summary.slot)
        if (!stateFile.exists()) {
            return
        }
        NativeBindings.nativeUnserialize(nativeHandle, stateFile.readBytes())
    }

    override fun inputSink(): InputSink = inputSink

    override fun release() {
        if (released) {
            return
        }
        stop()
        audioPlayer.release()
        rendererHost.release()
        NativeBindings.nativeDestroyBackend(nativeHandle)
        released = true
    }

    override fun frameWidth(): Int = NativeBindings.nativeGetFrameWidth(nativeHandle)

    override fun frameHeight(): Int = NativeBindings.nativeGetFrameHeight(nativeHandle)

    override fun copyFrame(target: java.nio.ByteBuffer): Boolean {
        return NativeBindings.nativeCopyFrame(nativeHandle, target)
    }

    private fun saveStateDir(romEntry: RomEntry): File = File(stateRoot, romEntry.id.value)

    private fun saveStateFile(romEntry: RomEntry, slot: SaveSlot): File {
        return File(saveStateDir(romEntry), "${slot.key}.state")
    }
}

private class NativeInputSink(
    private val mask: AtomicInteger,
    private val pushMask: (Int) -> Unit,
) : InputSink {
    override fun send(button: VirtualButton, pressed: Boolean) {
        val bit = when (button) {
            VirtualButton.B -> 1 shl 0
            VirtualButton.SELECT -> 1 shl 2
            VirtualButton.START -> 1 shl 3
            VirtualButton.UP -> 1 shl 4
            VirtualButton.DOWN -> 1 shl 5
            VirtualButton.LEFT -> 1 shl 6
            VirtualButton.RIGHT -> 1 shl 7
            VirtualButton.A -> 1 shl 8
            VirtualButton.MENU,
            VirtualButton.QUICK_SAVE,
            VirtualButton.QUICK_LOAD,
            -> 0
        }
        if (bit == 0) {
            return
        }
        while (true) {
            val current = mask.get()
            val updated = if (pressed) {
                current or bit
            } else {
                current and bit.inv()
            }
            if (mask.compareAndSet(current, updated)) {
                pushMask(updated)
                return
            }
        }
    }
}

private class CoreBinaryInstaller(
    private val context: Context,
) {
    fun install(descriptor: CoreDescriptor): File {
        val targetDir = File(context.filesDir, "cores/${descriptor.abi}")
        targetDir.mkdirs()
        val targetFile = File(targetDir, File(descriptor.coreBinaryPath).name)
        val assetBytes = context.assets.open(descriptor.coreBinaryPath).use { it.readBytes() }
        if (!targetFile.exists() || targetFile.length() != assetBytes.size.toLong()) {
            targetFile.writeBytes(assetBytes)
        }
        return targetFile
    }
}

private class LibretroAudioPlayer {
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private var readAudio: ((ShortArray) -> Int)? = null

    fun configure(
        sampleRate: Int,
        audioReader: (ShortArray) -> Int,
    ) {
        releaseTrack()
        readAudio = audioReader
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, channelConfig, format)
        val bufferBytes = maxOf(minBytes, sampleRate / 10 * 4)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(format)
                    .setChannelMask(channelConfig)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun start() {
        val track = audioTrack ?: return
        if (running.compareAndSet(false, true)) {
            playing.set(true)
            track.play()
            audioThread = Thread(
                {
                    val chunk = ShortArray(4_096)
                    while (running.get()) {
                        if (!playing.get()) {
                            Thread.sleep(8L)
                            continue
                        }
                        val read = readAudio?.invoke(chunk) ?: 0
                        if (read > 0) {
                            track.write(chunk, 0, read)
                        } else {
                            Thread.sleep(2L)
                        }
                    }
                },
                "dendy-audio-track",
            ).apply { start() }
        } else {
            resume()
        }
    }

    fun pause() {
        playing.set(false)
        audioTrack?.pause()
    }

    fun resume() {
        playing.set(true)
        audioTrack?.play()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        audioThread?.join(1_000L)
        audioThread = null
        audioTrack?.pause()
        audioTrack?.flush()
        playing.set(false)
    }

    fun release() {
        stop()
        releaseTrack()
    }

    private fun releaseTrack() {
        audioTrack?.release()
        audioTrack = null
    }
}
