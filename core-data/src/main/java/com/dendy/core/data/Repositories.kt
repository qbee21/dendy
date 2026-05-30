package com.dendy.core.data

import android.net.Uri
import com.dendy.core.model.BuiltInInstallState
import com.dendy.core.model.DendySettings
import com.dendy.core.model.RomEntry
import com.dendy.core.model.RomId
import com.dendy.core.model.SaveStateSummary

interface BuiltInRomInstaller {
    fun installIfNeeded(): BuiltInInstallState
}

data class RomScanReport(
    val indexedBuiltIn: Int,
    val indexedImported: Int,
    val duplicateHashes: Int,
)

interface RomScanner {
    fun scan(): RomScanReport
}

interface BuiltInCatalogRepository {
    fun getBuiltInEntries(): List<RomEntry>
}

interface UserRomRepository {
    fun getImportedEntries(): List<RomEntry>
    fun importFromSaf(uri: Uri): RomEntry?
}

interface RecentGamesRepository {
    fun getLastPlayed(): RomEntry?
    fun getRecentEntries(): List<RomEntry>
    fun markPlayed(entry: RomEntry)
}

interface SaveStateRepository {
    fun getSaveStates(romId: RomId): List<SaveStateSummary>
    fun getLastSession(romId: RomId): SaveStateSummary?
    fun upsert(summary: SaveStateSummary)
}

interface SettingsRepository {
    fun read(): DendySettings
    fun write(settings: DendySettings)
}

interface RomCatalogRepository {
    fun getBuiltInEntries(): List<RomEntry>
    fun getImportedEntries(): List<RomEntry>
    fun findById(romId: RomId): RomEntry?
}

