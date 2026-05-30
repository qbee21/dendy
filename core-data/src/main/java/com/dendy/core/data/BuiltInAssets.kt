package com.dendy.core.data

import android.content.Context
import com.dendy.core.model.BuiltInInstallState
import com.dendy.core.model.GameMetadata
import com.dendy.core.model.RomEntry
import com.dendy.core.model.RomId
import com.dendy.core.model.RomLocation
import com.dendy.core.model.RomSource
import org.json.JSONObject
import java.io.File

private const val BUILT_IN_ROOT = "builtins"
private const val BUILT_IN_ROMS_DIR = "roms"
private const val BUILT_IN_COVERS_DIR = "covers"
private const val BUILT_IN_LICENSES_DIR = "licenses"
private const val BUILT_IN_MANIFEST_ASSET = "built_in_catalog.json"

class AssetBuiltInRomInstaller(
    private val context: Context,
) : BuiltInRomInstaller {
    override fun installIfNeeded(): BuiltInInstallState {
        val rootDir = File(context.filesDir, BUILT_IN_ROOT).apply { mkdirs() }
        copyAssetFileIfMissing(BUILT_IN_MANIFEST_ASSET, File(rootDir, BUILT_IN_MANIFEST_ASSET))
        copyAssetDirectoryIfPresent(BUILT_IN_ROMS_DIR, File(rootDir, BUILT_IN_ROMS_DIR))
        copyAssetDirectoryIfPresent(BUILT_IN_COVERS_DIR, File(rootDir, BUILT_IN_COVERS_DIR))
        copyAssetDirectoryIfPresent(BUILT_IN_LICENSES_DIR, File(rootDir, BUILT_IN_LICENSES_DIR))

        return BuiltInInstallState.Installed(
            romCount = rootDir.resolve(BUILT_IN_ROMS_DIR).listFiles().orEmpty()
                .count { it.isFile && it.extension.lowercase() in setOf("nes", "zip") },
            coverCount = rootDir.resolve(BUILT_IN_COVERS_DIR).listFiles().orEmpty()
                .count { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") },
            rootPath = rootDir.absolutePath,
        )
    }

    private fun copyAssetDirectoryIfPresent(assetDir: String, targetDir: File) {
        val children = context.assets.list(assetDir).orEmpty()
        if (children.isEmpty()) return
        targetDir.mkdirs()
        children.forEach { child ->
            val assetPath = "$assetDir/$child"
            val nestedChildren = context.assets.list(assetPath).orEmpty()
            if (nestedChildren.isEmpty()) {
                copyAssetFileIfMissing(assetPath, File(targetDir, child))
            } else {
                copyAssetDirectoryIfPresent(assetPath, File(targetDir, child))
            }
        }
    }

    private fun copyAssetFileIfMissing(assetPath: String, destination: File) {
        if (destination.exists()) return
        destination.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

class AssetBuiltInCatalogRepository(
    private val context: Context,
) : BuiltInCatalogRepository {
    override fun getBuiltInEntries(): List<RomEntry> {
        val manifest = context.assets.open(BUILT_IN_MANIFEST_ASSET).bufferedReader().use { it.readText() }
        val root = JSONObject(manifest)
        val entries = root.getJSONArray("entries")
        val builtInRoot = File(context.filesDir, BUILT_IN_ROOT).resolve(BUILT_IN_ROMS_DIR)

        return buildList(entries.length()) {
            repeat(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                val fileName = item.getString("fileName")
                add(
                    RomEntry(
                        id = RomId(item.getString("id")),
                        fileName = fileName,
                        hash = item.getString("sha256"),
                        location = RomLocation(
                            absolutePath = File(builtInRoot, fileName).absolutePath,
                            displayName = item.getString("title"),
                            source = RomSource.BUILT_IN,
                        ),
                        metadata = GameMetadata(
                            title = item.getString("title"),
                            genre = item.getString("genre"),
                            releaseLabel = item.getString("releaseLabel"),
                            coverPath = item.optString("coverAsset", null)?.takeIf { it.isNotBlank() },
                            description = item.getString("description"),
                        ),
                    ),
                )
            }
        }
    }
}

class AssetRomScanner(
    private val builtInCatalogRepository: BuiltInCatalogRepository,
    private val userRomRepository: UserRomRepository,
) : RomScanner {
    override fun scan(): RomScanReport {
        return RomScanReport(
            indexedBuiltIn = builtInCatalogRepository.getBuiltInEntries().size,
            indexedImported = userRomRepository.getImportedEntries().size,
            duplicateHashes = 0,
        )
    }
}
