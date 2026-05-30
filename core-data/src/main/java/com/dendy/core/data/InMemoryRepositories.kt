package com.dendy.core.data

import android.net.Uri
import com.dendy.core.model.DendySettings
import com.dendy.core.model.GameMetadata
import com.dendy.core.model.LayoutOrientation
import com.dendy.core.model.PerformancePreset
import com.dendy.core.model.RomEntry
import com.dendy.core.model.RomId
import com.dendy.core.model.RomLocation
import com.dendy.core.model.RomSource
import com.dendy.core.model.SaveSlot
import com.dendy.core.model.SaveSlotType
import com.dendy.core.model.SaveStateSummary
import com.dendy.core.model.TouchAnchor
import com.dendy.core.model.TouchControlLayout
import com.dendy.core.model.VideoFilter
import com.dendy.core.model.VirtualButton

private object DemoRomFixtures {
    val defaultSettings = DendySettings(
        videoFilter = VideoFilter.PIXEL_PERFECT,
        audioEnabled = true,
        performancePreset = PerformancePreset.BALANCED,
        portraitLayout = defaultLayout(LayoutOrientation.PORTRAIT),
        landscapeLayout = defaultLayout(LayoutOrientation.LANDSCAPE),
        lastCoreOptions = mapOf("region" to "auto", "overscan" to "off"),
    )

    private fun defaultLayout(orientation: LayoutOrientation): TouchControlLayout {
        val y = if (orientation == LayoutOrientation.PORTRAIT) 0.82f else 0.78f
        return TouchControlLayout(
            orientation = orientation,
            opacity = 0.82f,
            anchors = mapOf(
                VirtualButton.LEFT to TouchAnchor(0.14f, y),
                VirtualButton.RIGHT to TouchAnchor(0.26f, y),
                VirtualButton.A to TouchAnchor(0.82f, y),
                VirtualButton.B to TouchAnchor(0.70f, y),
                VirtualButton.START to TouchAnchor(0.55f, y),
                VirtualButton.SELECT to TouchAnchor(0.44f, y),
                VirtualButton.MENU to TouchAnchor(0.50f, 0.12f),
            ),
        )
    }
}

class InMemoryUserRomRepository : UserRomRepository {
    private val importedEntries = mutableListOf<RomEntry>()

    override fun getImportedEntries(): List<RomEntry> = importedEntries.toList()

    override fun importFromSaf(uri: Uri): RomEntry? {
        val next = RomEntry(
            id = RomId("imported-${importedEntries.size + 1}"),
            fileName = uri.lastPathSegment ?: "imported_rom.nes",
            hash = "imported-${importedEntries.size + 1}",
            location = RomLocation(
                absolutePath = uri.toString(),
                displayName = uri.lastPathSegment ?: "Imported ROM",
                source = RomSource.IMPORTED,
            ),
            metadata = GameMetadata(
                title = "Imported ROM ${importedEntries.size + 1}",
                genre = "Imported",
                releaseLabel = "Queued for scanner",
                description = "Stub import created from SAF URI until the real scanner is wired.",
            ),
        )
        importedEntries += next
        return next
    }
}

class InMemoryRecentGamesRepository(
    private val romCatalogRepository: RomCatalogRepository,
) : RecentGamesRepository {
    private val recentIds = ArrayDeque<RomId>().apply {
        romCatalogRepository.getBuiltInEntries().firstOrNull()?.let { add(it.id) }
    }

    override fun getLastPlayed(): RomEntry? = recentIds.firstOrNull()?.let(romCatalogRepository::findById)

    override fun getRecentEntries(): List<RomEntry> = recentIds.mapNotNull(romCatalogRepository::findById)

    override fun markPlayed(entry: RomEntry) {
        recentIds.remove(entry.id)
        recentIds.addFirst(entry.id)
        while (recentIds.size > 10) {
            recentIds.removeLast()
        }
    }
}

class InMemorySaveStateRepository : SaveStateRepository {
    private val stateMap = mutableMapOf(
        RomId("builtin-sliding-blaster") to mutableListOf(
            SaveStateSummary(
                romId = RomId("builtin-sliding-blaster"),
                slot = SaveSlot("quick-1", SaveSlotType.QUICK, "Quick 1"),
                timestampLabel = "2m ago",
            ),
            SaveStateSummary(
                romId = RomId("builtin-sliding-blaster"),
                slot = SaveSlot("resume", SaveSlotType.LAST_SESSION, "Resume"),
                timestampLabel = "Last session",
            ),
        ),
    )

    override fun getSaveStates(romId: RomId): List<SaveStateSummary> = stateMap[romId].orEmpty().toList()

    override fun getLastSession(romId: RomId): SaveStateSummary? {
        return stateMap[romId]
            ?.firstOrNull { it.slot.type == SaveSlotType.LAST_SESSION }
    }

    override fun upsert(summary: SaveStateSummary) {
        val states = stateMap.getOrPut(summary.romId) { mutableListOf() }
        states.removeAll { it.slot.key == summary.slot.key }
        states.add(0, summary)
    }
}

class InMemorySettingsRepository : SettingsRepository {
    private var currentSettings: DendySettings = DemoRomFixtures.defaultSettings

    override fun read(): DendySettings = currentSettings

    override fun write(settings: DendySettings) {
        currentSettings = settings
    }
}

class InMemoryRomCatalogRepository(
    private val builtInCatalogRepository: BuiltInCatalogRepository,
    private val userRomRepository: UserRomRepository,
) : RomCatalogRepository {
    override fun getBuiltInEntries(): List<RomEntry> = builtInCatalogRepository.getBuiltInEntries()

    override fun getImportedEntries(): List<RomEntry> = userRomRepository.getImportedEntries()

    override fun findById(romId: RomId): RomEntry? {
        return (getBuiltInEntries() + getImportedEntries()).firstOrNull { it.id == romId }
    }
}
