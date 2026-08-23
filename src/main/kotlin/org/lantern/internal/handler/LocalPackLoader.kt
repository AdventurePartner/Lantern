package org.lantern.internal.handler

import org.lantern.Lantern
import org.lantern.internal.wrapper.resource.FileResourceWrapper
import org.lantern.platform.IdentifierBridge
import java.nio.file.Files
import java.nio.file.Path

object LocalPackLoader {

    private const val PACK_NAME = "LanternPackLocal"

    fun reload() {
        ResourceHandler.clearLocalPackResources()
        loadLocalPack()
    }

    private fun loadLocalPack() {
        val assetsPath = ResourcePackPaths.resourcePacksDir()
            .resolve(PACK_NAME)
            .resolve("assets")

        if (!Files.exists(assetsPath)) {
            Lantern.logger.info("[Lantern] {} directory not found, skipping", PACK_NAME)
            return
        }

        if (!Files.isDirectory(assetsPath)) {
            Lantern.logger.warn("[Lantern] {} assets path is not a directory: {}", PACK_NAME, assetsPath)
            return
        }

        var loadedCount = 0

        try {
            Files.walk(assetsPath).use { paths ->
                paths.filter { Files.isRegularFile(it) }
                    .forEach { file ->
                        if (loadFile(assetsPath, file)) {
                            loadedCount++
                        }
                    }
            }
        } catch (e: Exception) {
            Lantern.logger.warn("[Lantern] Failed to load {}: {}", PACK_NAME, e.message)
            return
        }

        Lantern.logger.info("[Lantern] Loaded {} resource(s) from {}", loadedCount, PACK_NAME)
        if (loadedCount == 0) {
            Lantern.logger.warn("[Lantern] {} exists but no valid resources were loaded", PACK_NAME)
        }
    }

    private fun loadFile(assetsPath: Path, file: Path): Boolean {
        val relativePath = assetsPath.relativize(file)
        if (relativePath.nameCount < 2) {
            return false
        }

        val namespace = relativePath.getName(0).toString()
        val resourcePath = (1 until relativePath.nameCount)
            .joinToString("/") { relativePath.getName(it).toString() }

        return try {
            val rl = IdentifierBridge.of(namespace, resourcePath)
            ResourceHandler.addLocalPackResource(rl, FileResourceWrapper(rl, file))
            true
        } catch (e: Exception) {
            Lantern.logger.warn(
                "[Lantern] Skipping invalid {} resource file '{}': {}",
                PACK_NAME,
                relativePath,
                e.message
            )
            false
        }
    }
}
