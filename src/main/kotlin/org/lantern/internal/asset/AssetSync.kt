package org.lantern.internal.asset

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.LoadingOverlay
import org.lantern.Lantern
import org.lantern.internal.handler.EncryptedPackLoader
import org.lantern.internal.handler.ResourceHandler
import org.lantern.platform.GeckoCacheBridge
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 进服资源加载协调：解密加密包、重建 GeckoLib 缓存都放到后台，完成后在主线程原子替换，
 * 不再整包 reloadResourcePacks（遮罩 + 全量图集 + 全部区块重编译）。
 *
 * 资源没就绪期间各渲染入口经 [LanternAssets] 判定后回退原版或不画。
 * 状态只在主线程读写；packet 处理在 NeoForge 上可能落在网络线程，入口统一切回主线程
 */
object AssetSync {

    val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Lantern-Assets").apply { isDaemon = true }
    }

    @Volatile
    private var pendingDecrypt: CompletableFuture<*> = CompletableFuture.completedFuture(null)

    @Volatile
    private var refreshing = false
    private var refreshQueued = false

    @Volatile
    private var sessionSynced = false

    /** GeckoLib 缓存对应的加密包发布版本 */
    private var geckoSyncedVersion = 0

    /** 上一次刷新完成时的加密包发布版本，用来判断这次刷新是否带来了新资源 */
    private var refreshedVersion = 0

    /** 本会话的资源还没刷新完：缺资源按「还在加载」处理，不当成配置错误 */
    @JvmStatic
    val isLoading: Boolean
        get() = !sessionSynced || refreshing || !pendingDecrypt.isDone

    /** packet 7：后台解密。初始同步时由随后的 packet 99 触发刷新，同步完成后的解密自己触发 */
    fun onResourcePackKey(key: String): Unit = onMain {
        val decrypt = EncryptedPackLoader.loadAsync(key)
        pendingDecrypt = decrypt
        decrypt.thenAccept { changed -> if (changed && sessionSynced) requestRefresh() }
    }

    /** packet 99：初始同步结束。服务端要求 full 时退回整包重载 */
    fun onSyncComplete(full: Boolean): Unit = onMain {
        if (full) {
            val minecraft = Minecraft.getInstance()
            minecraft.reloadResourcePacks().thenRunAsync({ sessionSynced = true }, minecraft)
        } else {
            requestRefresh()
        }
    }

    fun requestRefresh(): Unit = onMain {
        if (refreshing) {
            refreshQueued = true
            return@onMain
        }
        refreshing = true
        val minecraft = Minecraft.getInstance()
        val startedAt = System.nanoTime()
        pendingDecrypt.handle { _, _ -> null }
            .thenComposeAsync({ refreshGeckoLib(minecraft) }, minecraft)
            .whenCompleteAsync({ _, error ->
                if (error != null) {
                    Lantern.logger.error("[Lantern] Background asset refresh failed", error)
                }
                val version = EncryptedPackLoader.publishedVersion
                val resourcesChanged = version != refreshedVersion
                refreshedVersion = version
                try {
                    ResourceHandler.onAssetsRefreshed(resourcesChanged)
                } catch (e: RuntimeException) {
                    Lantern.logger.error("[Lantern] Failed to apply refreshed assets", e)
                }
                sessionSynced = true
                refreshing = false
                Lantern.logger.info(
                    "[Lantern] Assets ready in {} ms (resources changed: {})",
                    (System.nanoTime() - startedAt) / 1_000_000, resourcesChanged
                )
                if (refreshQueued) {
                    refreshQueued = false
                    requestRefresh()
                }
            }, minecraft)
    }

    /** 真正的资源重载（F3+T、整包重载）结束：GeckoLib 已按当前动态资源重建 */
    fun onResourceReloaded() {
        geckoSyncedVersion = EncryptedPackLoader.publishedVersion
        refreshedVersion = geckoSyncedVersion
        LanternAssets.invalidate()
    }

    fun onDisconnect() {
        sessionSynced = false
        refreshQueued = false
        LanternAssets.invalidate()
    }

    private fun refreshGeckoLib(minecraft: Minecraft): CompletableFuture<Unit> {
        val target = EncryptedPackLoader.publishedVersion
        // 正在进行的真重载会带上当前资源，重载结束时由 onResourceReloaded 记账
        if (target == geckoSyncedVersion || minecraft.overlay is LoadingOverlay) {
            return CompletableFuture.completedFuture(Unit)
        }
        val startedAt = System.nanoTime()
        return GeckoCacheBridge.refresh(minecraft.resourceManager).thenApply {
            geckoSyncedVersion = target
            Lantern.logger.info(
                "[Lantern] GeckoLib cache rebuilt in background in {} ms",
                (System.nanoTime() - startedAt) / 1_000_000
            )
        }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.isSameThread) block() else minecraft.execute { block() }
    }
}
