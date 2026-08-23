package org.lantern.internal.handler

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException

import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern
import org.lantern.internal.wrapper.resource.ByteArrayResourceWrapper

object EncryptedPackLoader {

    private var lastPassword: String? = null

    fun reloadEncryptedPacks() {
        val pwd = lastPassword
        if (pwd != null && pwd.isNotBlank()) {
            loadEncryptedPacks(pwd)
        }
    }

    fun clearPassword() {
        lastPassword = null
    }

    fun loadEncryptedPacks(password: String) {
        lastPassword = password
        if (password.isBlank()) {
            Lantern.logger.warn("[Lantern] Encrypted pack password is empty, skipping")
            return
        }

        // 清除之前加载的加密包资源
        ResourceHandler.clearEncryptedPackResources()

        val resourcePacksDir = ResourcePackPaths.resourcePacksDir().toFile()

        if (!resourcePacksDir.exists() || !resourcePacksDir.isDirectory) {
            Lantern.logger.warn("[Lantern] resourcePacks directory not found")
            return
        }

        val zipFiles = resourcePacksDir.listFiles { file ->
            file.isFile && file.name.endsWith(".zip", ignoreCase = true)
        }

        if (zipFiles.isNullOrEmpty()) {
            Lantern.logger.info("[Lantern] No ZIP files found in resourcePacks/")
            return
        }

        Lantern.logger.info("[Lantern] Found {} ZIP file(s) in resourcePacks/", zipFiles.size)

        for (file in zipFiles) {
            try {
                ZipFile(file, password.toCharArray()).use { zipFile ->
                    if (!zipFile.isEncrypted) {
                        Lantern.logger.info("[Lantern] ZIP '{}' is not encrypted, skipping", file.name)
                        return@use
                    }

                    var loadedCount = 0
                    val headers = zipFile.fileHeaders

                    for (header in headers) {
                        if (header.isDirectory) continue

                        val entryName = header.fileName

                        // 仅处理 assets/ 下的文件
                        if (!entryName.startsWith("assets/")) continue

                        // 解析: assets/<namespace>/<path>
                        val parts = entryName.removePrefix("assets/").split("/", limit = 2)
                        if (parts.size < 2 || parts[1].isEmpty()) continue

                        val namespace = parts[0]
                        val path = parts[1]

                        try {
                            val data = zipFile.getInputStream(header).use { inputStream ->
                                inputStream.readAllBytes()
                            }

                            val rl = IdentifierBridge.of(namespace, path)
                            val wrapper = ByteArrayResourceWrapper(rl, data)
                            ResourceHandler.addEncryptedPackResource(rl, wrapper)
                            loadedCount++
                        } catch (e: Exception) {
                            Lantern.logger.warn(
                                "[Lantern] Failed to read entry '{}' from '{}': {}",
                                entryName, file.name, e.message
                            )
                        }
                    }

                    Lantern.logger.info(
                        "[Lantern] Loaded {} resources from encrypted ZIP '{}'",
                        loadedCount, file.name
                    )
                }
            } catch (e: ZipException) {
                Lantern.logger.warn(
                    "[Lantern] Failed to open encrypted ZIP '{}': {} (wrong password?)",
                    file.name, e.message
                )
            } catch (e: Exception) {
                Lantern.logger.warn(
                    "[Lantern] Unexpected error processing ZIP '{}': {}",
                    file.name, e.message
                )
            }
        }
    }
}
