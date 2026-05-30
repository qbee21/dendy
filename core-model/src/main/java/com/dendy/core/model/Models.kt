package com.dendy.core.model

@JvmInline
value class RomId(val value: String)

enum class RomSource {
    BUILT_IN,
    IMPORTED,
}

data class RomLocation(
    val absolutePath: String,
    val displayName: String,
    val source: RomSource,
)

data class GameMetadata(
    val title: String,
    val genre: String,
    val releaseLabel: String,
    val coverPath: String? = null,
    val description: String = "",
)

data class RomEntry(
    val id: RomId,
    val fileName: String,
    val hash: String,
    val location: RomLocation,
    val metadata: GameMetadata,
)

enum class SaveSlotType {
    QUICK,
    NAMED,
    LAST_SESSION,
}

data class SaveSlot(
    val key: String,
    val type: SaveSlotType,
    val label: String,
)

data class SaveStateSummary(
    val romId: RomId,
    val slot: SaveSlot,
    val previewPath: String? = null,
    val timestampLabel: String,
)

enum class VirtualButton {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    A,
    B,
    START,
    SELECT,
    MENU,
    QUICK_SAVE,
    QUICK_LOAD,
}

enum class LayoutOrientation {
    PORTRAIT,
    LANDSCAPE,
}

data class TouchAnchor(
    val xFraction: Float,
    val yFraction: Float,
)

data class TouchControlLayout(
    val orientation: LayoutOrientation,
    val opacity: Float,
    val anchors: Map<VirtualButton, TouchAnchor>,
)

enum class PerformancePreset {
    BATTERY,
    BALANCED,
    PERFORMANCE,
}

enum class VideoFilter {
    PIXEL_PERFECT,
    SOFT_CRT,
    SHARP_LCD,
}

data class DendySettings(
    val videoFilter: VideoFilter,
    val audioEnabled: Boolean,
    val performancePreset: PerformancePreset,
    val portraitLayout: TouchControlLayout,
    val landscapeLayout: TouchControlLayout,
    val lastCoreOptions: Map<String, String>,
)

sealed interface BuiltInInstallState {
    data object Pending : BuiltInInstallState
    data class Installed(val romCount: Int, val coverCount: Int, val rootPath: String) : BuiltInInstallState
    data class Failed(val reason: String) : BuiltInInstallState
}

