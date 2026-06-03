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
import java.security.MessageDigest

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

        val manifestEntries = buildList(entries.length()) {
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
                            coverPath = item.optString("coverAsset").takeIf { it.isNotBlank() },
                            description = item.getString("description"),
                        ),
                    ),
                )
            }
        }

        val managedFiles = manifestEntries.map { it.fileName.lowercase() }.toHashSet()
        val discoveredEntries = builtInRoot.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.lowercase() in setOf("nes", "zip") }
            .filterNot { it.name.lowercase() in managedFiles }
            .sortedBy { it.name.lowercase() }
            .map { file ->
                val displayName = normalizeRomTitle(file.nameWithoutExtension)
                RomEntry(
                    id = RomId("builtin-auto-${slugify(file.nameWithoutExtension)}"),
                    fileName = file.name,
                    hash = sha256(file),
                    location = RomLocation(
                        absolutePath = file.absolutePath,
                        displayName = displayName,
                        source = RomSource.BUILT_IN,
                    ),
                    metadata = GameMetadata(
                        title = displayName,
                        genre = "Не указан",
                        releaseLabel = "Auto-discovered from built-in ROM folder",
                        description = "ROM автоматически найден в папке встроенных игр.",
                    ),
                )
            }
            .toList()

        return manifestEntries + discoveredEntries
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

private fun slugify(value: String): String {
    return value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "rom" }
}

private fun normalizeRomTitle(rawTitle: String): String {
    val cleaned = rawTitle
        .replace(Regex("^\\d+[_\\-. ]+"), "")
        .replace("onlain-igrok.rf_", "", ignoreCase = true)
        .replace("onlain igrok rf", "", ignoreCase = true)
        .replace(Regex("\\[[^\\]]*]"), "")
        .replace(Regex("\\([^)]*(?:rus|u|e|j|hack|pirate|proto|beta|!|p)[^)]*\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bby\\b.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(?:rus|t-rus|onlain|igrok|rf|hack|shedevr|multisoft|team|p1trus)\\b", RegexOption.IGNORE_CASE), "")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace('.', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    val titleCased = cleaned
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            when (token.lowercase()) {
                "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x" -> token.uppercase()
                "n" -> "n"
                else -> token.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
        }

    return titleCased.ifBlank { rawTitle }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
}
