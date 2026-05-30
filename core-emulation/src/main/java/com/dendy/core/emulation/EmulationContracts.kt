package com.dendy.core.emulation

import android.content.Context
import android.view.View
import com.dendy.core.model.RomEntry
import com.dendy.core.model.SaveSlot
import com.dendy.core.model.SaveStateSummary
import com.dendy.core.model.VirtualButton

data class CoreDescriptor(
    val coreId: String,
    val abi: String,
    val coreBinaryPath: String,
    val displayName: String,
)

interface CoreProvider {
    fun defaultCore(): CoreDescriptor
    fun listCores(): List<CoreDescriptor>
}

interface LibretroBridge {
    val rendererHost: RendererHost
    fun attachCore(descriptor: CoreDescriptor)
    fun loadRom(romEntry: RomEntry)
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun saveState(slot: SaveSlot): SaveStateSummary?
    fun loadState(summary: SaveStateSummary)
    fun inputSink(): InputSink
    fun release()
}

interface InputSink {
    fun send(button: VirtualButton, pressed: Boolean)
}

interface InputMapper {
    fun mapTouch(button: VirtualButton, pressed: Boolean)
}

interface RendererHost {
    fun createView(context: Context): View
    fun resume()
    fun pause()
    fun release()
}

enum class SessionStatus {
    CREATED,
    LOADED,
    RUNNING,
    PAUSED,
    STOPPED,
    RELEASED,
}

interface EmulationSession {
    val status: SessionStatus
    val rendererHost: RendererHost
    val isPlaceholder: Boolean
    fun load(romEntry: RomEntry)
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun release()
    fun saveState(slot: SaveSlot): SaveStateSummary?
    fun loadState(summary: SaveStateSummary)
    fun inputSink(): InputSink
}

interface EmulationSessionFactory {
    fun create(coreProvider: CoreProvider = StaticCoreProvider()): EmulationSession
}
