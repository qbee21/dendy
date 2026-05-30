package com.dendy.app.di

import android.content.Context
import com.dendy.core.data.AssetBuiltInCatalogRepository
import com.dendy.core.data.AssetBuiltInRomInstaller
import com.dendy.core.data.AssetRomScanner
import com.dendy.core.data.BuiltInCatalogRepository
import com.dendy.core.data.BuiltInRomInstaller
import com.dendy.core.data.InMemoryRecentGamesRepository
import com.dendy.core.data.InMemoryRomCatalogRepository
import com.dendy.core.data.InMemorySaveStateRepository
import com.dendy.core.data.InMemorySettingsRepository
import com.dendy.core.data.InMemoryUserRomRepository
import com.dendy.core.data.RecentGamesRepository
import com.dendy.core.data.RomCatalogRepository
import com.dendy.core.data.RomScanner
import com.dendy.core.data.SaveStateRepository
import com.dendy.core.data.SettingsRepository
import com.dendy.core.data.UserRomRepository
import com.dendy.core.emulation.AndroidRuntimeCoreProvider
import com.dendy.core.emulation.CoreProvider
import com.dendy.core.emulation.EmulationSessionFactory
import com.dendy.core.emulation.LibretroEmulationSessionFactory
import com.dendy.feature.library.LibraryDependencies
import com.dendy.feature.player.PlayerDependencies
import com.dendy.feature.settings.SettingsDependencies

interface AppGraph {
    val libraryDependencies: LibraryDependencies
    val playerDependencies: PlayerDependencies
    val settingsDependencies: SettingsDependencies
    fun bootstrap()
}

class DefaultAppGraph(
    context: Context,
) : AppGraph, LibraryDependencies, PlayerDependencies, SettingsDependencies {
    private val appContext = context.applicationContext

    private val installer: BuiltInRomInstaller = AssetBuiltInRomInstaller(appContext)
    override val builtInCatalogRepository: BuiltInCatalogRepository = AssetBuiltInCatalogRepository(appContext)
    override val userRomRepository: UserRomRepository = InMemoryUserRomRepository()
    override val romCatalogRepository: RomCatalogRepository =
        InMemoryRomCatalogRepository(builtInCatalogRepository, userRomRepository)
    override val recentGamesRepository: RecentGamesRepository = InMemoryRecentGamesRepository(romCatalogRepository)
    override val saveStateRepository: SaveStateRepository = InMemorySaveStateRepository()
    override val settingsRepository: SettingsRepository = InMemorySettingsRepository()
    private val scanner: RomScanner = AssetRomScanner(builtInCatalogRepository, userRomRepository)
    override val coreProvider: CoreProvider = AndroidRuntimeCoreProvider(appContext)
    override val emulationSessionFactory: EmulationSessionFactory =
        LibretroEmulationSessionFactory(appContext)

    override val libraryDependencies: LibraryDependencies = this
    override val playerDependencies: PlayerDependencies = this
    override val settingsDependencies: SettingsDependencies = this

    override fun bootstrap() {
        installer.installIfNeeded()
        scanner.scan()
    }
}
