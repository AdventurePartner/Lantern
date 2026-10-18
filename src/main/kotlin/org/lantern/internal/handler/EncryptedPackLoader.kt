package org.lantern.internal.handler

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern
import org.lantern.internal.asset.AssetDiagnostics
import org.lantern.internal.asset.AssetSync
import org.lantern.internal.wrapper.resource.ByteArrayResourceWrapper
import org.lantern.internal.wrapper.resource.IResourceWrapper
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * 加密资源包：解密在 [AssetSync.executor] 后台线程，结果只在主线程发布进 [ResourceHandler]。
 * 解密结果按「密码 + 各 zip 路径/大小/修改时间」留快照，指纹不变时重连与 F3+T 都不再读盘
 */
object EncryptedPackLoader {

    private data class ZipStamp(val path: String, val size: Long, val modified: Long)

    private data class Fingerprint(val password: String, val zips: List<ZipStamp>)

    private class Snapshot(val fingerprint: Fingerprint, val resources: Map<ResourceLocation, IResourceWrapper>)

    @Volatile
    private var password: String? = null

    @Volatile
    private var snapshot: Snapshot? = null

    /** 已发布进 ResourceHandler 的快照；F3+T 的 rebuild 会清表，需要按它重新发布 */
    private var published: Snapshot? = null

    /** 断线、换密码时递增，迟到的旧解密结果按代号丢弃 */
    private val generation = AtomicInteger()

    /** 发布内容每变一次递增，AssetSync 据此判断 GeckoLib 缓存是否落后 */
    @Volatile
    var publishedVersion: Int = 0
        private set

    fun clearPassword() {
        password = null
        generation.incrementAndGet()
    }

    /**
     * 后台解密并在主线程发布。返回的 future 在主线程完成，值为本次是否改变了已发布的资源
     */
    fun loadAsync(password: String): CompletableFuture<Boolean> {
        if (password.isBlank()) {
            Lantern.logger.warn("[Lantern] Encrypted pack password is empty, skipping")
            return CompletableFuture.completedFuture(false)
        }
        this.password = password
        val token = generation.incrementAndGet()
        val minecraft = Minecraft.getInstance()
        return CompletableFuture.supplyAsync({
            val fingerprint = fingerprint(password)
            val previous = snapshot
            if (previous != null && previous.fingerprint == fingerprint) {
                Lantern.logger.info("[Lantern] Encrypted packs unchanged, reusing decrypted resources")
                previous
            } else {
                decrypt(fingerprint).also { AssetDiagnostics.reportReloadOnlyAssets(it.resources.keys) }
            }
        }, AssetSync.executor).thenApplyAsync({ next ->
            if (token != generation.get()) return@thenApplyAsync false
            snapshot = next
            publish(next)
        }, minecraft)
    }

    /**
     * F3+T 的 rebuild 清空动态资源表后调用：按内存快照重新发布，不读盘。
     * 玩家在游戏中替换了 zip 时指纹会变，交给后台重新解密
     */
    fun republishSnapshot() {
        val current = snapshot ?: return
        ResourceHandler.replaceEncryptedPackResources(current.resources)
        published = current
        val pwd = password ?: return
        if (current.fingerprint.password == pwd && current.fingerprint.zips != zipStamps()) {
            AssetSync.onResourcePackKey(pwd)
        }
    }

    private fun publish(next: Snapshot): Boolean {
        if (published === next) return false
        ResourceHandler.replaceEncryptedPackResources(next.resources)
        published = next
        publishedVersion++
        AssetDiagnostics.reportUnknownNamespaces(next.resources.keys)
        return true
    }

    private fun zipFiles(): List<File> {
        val dir = ResourcePackPaths.resourcePacksDir().toFile()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    private fun zipStamps(): List<ZipStamp> =
        zipFiles().map { ZipStamp(it.absolutePath, it.length(), it.lastModified()) }

    private fun fingerprint(password: String): Fingerprint = Fingerprint(password, zipStamps())

    private fun decrypt(fingerprint: Fingerprint): Snapshot {
        val resources = LinkedHashMap<ResourceLocation, IResourceWrapper>()
        val zipFiles = zipFiles()
        if (zipFiles.isEmpty()) {
            Lantern.logger.info("[Lantern] No ZIP files found in resourcePacks/")
            return Snapshot(fingerprint, resources)
        }

        Lantern.logger.info("[Lantern] Found {} ZIP file(s) in resourcePacks/", zipFiles.size)
        val startedAt = System.nanoTime()

        for (file in zipFiles) {
            try {
                ZipFile(file, fingerprint.password.toCharArray()).use { zipFile ->
                    if (!zipFile.isEncrypted) {
                        Lantern.logger.info("[Lantern] ZIP '{}' is not encrypted, skipping", file.name)
                        return@use
                    }

                    var loadedCount = 0
                    for (header in zipFile.fileHeaders) {
                        if (header.isDirectory) continue

                        val entryName = header.fileName

                        // 仅处理 assets/ 下的文件
                        if (!entryName.startsWith("assets/")) continue

                        // 解析: assets/<namespace>/<path>
                        val parts = entryName.removePrefix("assets/").split("/", limit = 2)
                        if (parts.size < 2 || parts[1].isEmpty()) continue

                        try {
                            val data = zipFile.getInputStream(header).use { it.readAllBytes() }
                            val rl = IdentifierBridge.of(parts[0], parts[1])
                            resources[rl] = ByteArrayResourceWrapper(rl, data)
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

        Lantern.logger.info(
            "[Lantern] Decrypted {} resource(s) in {} ms",
            resources.size, (System.nanoTime() - startedAt) / 1_000_000
        )
        return Snapshot(fingerprint, resources)
    }
}
