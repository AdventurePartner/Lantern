package org.lantern.model.handler

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EntityType
import org.lantern.Lantern
import org.lantern.internal.asset.AssetSync
import org.lantern.internal.asset.LanternAssets
import org.lantern.internal.asset.RenderProbe
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.icon.ItemIconMeshes
import org.lantern.internal.storage.HudLayer
import org.lantern.internal.storage.UiScreenStorage
import org.lantern.item.icon.ItemIconModels
import software.bernie.geckolib.cache.GeckoLibCache
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * 资源就绪（或整包重载）15 秒后打一次的渲染就绪报告。实体模型、物品图标、HUD 图片、字符图标
 * 回退原版的分支都是静默的；某台设备上「数据都到了却全画原版」时，靠这份报告指出卡在哪一步。
 * 只在共用根目录源码的 Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1 上编译
 */
object RenderReadinessReport {

    private const val DELAY_TICKS = 300
    private const val MAX_ENTITIES = 20
    private const val MAX_MODELS = 30

    private var countdown = -1

    /** 主线程调用：重新倒计时，并清掉此前的计数，让报告只反映就绪之后的渲染 */
    fun schedule() {
        countdown = DELAY_TICKS
        RenderProbe.drain()
        ItemIconModels.drainSeen()
    }

    /** 每个 client tick 调用 */
    fun tick() {
        if (countdown < 0 || countdown-- > 0) return
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null || minecraft.player == null) return
        try {
            report(minecraft)
        } catch (e: Exception) {
            Lantern.logger.warn("[Lantern] Render readiness report failed", e)
        }
    }

    private fun report(minecraft: Minecraft) {
        val log = Lantern.logger
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        log.info("[Lantern] ==== RENDER READINESS (15 s after assets ready) ====")
        log.info(
            "[Lantern] assets: isLoading={} {} rendererContext={}",
            AssetSync.isLoading, AssetSync.debugState(), CycleHandler.context != null
        )

        val baked = GeckoLibCache.getBakedModels()
        log.info(
            "[Lantern] geckolib: {} baked models, {} in namespace {}",
            baked.size, baked.keys.count { it.namespace == Lantern.MOD_ID }, Lantern.MOD_ID
        )

        // 同一个模型按原名和去格式码的名字各登记一次，按实例合并
        val models = RendererHandler.getCustomModelWrappers().entries.groupBy({ it.value }, { it.key })
        log.info("[Lantern] entity models: {}", models.size)
        models.entries.take(MAX_MODELS).forEach { (wrapper, names) ->
            log.info(
                "[Lantern]   model {} geoReady={} textureReady={} texture={} url={}",
                names, LanternAssets.isGeoReady(wrapper.modelLocation),
                LanternAssets.isTextureReady(wrapper.textureLocation, wrapper.textureUrl),
                wrapper.textureLocation, wrapper.textureUrl
            )
        }

        val named = level.entitiesForRendering().filter { it.hasCustomName() }
        log.info(
            "[Lantern] named entities in level: {}, matched a model: {}",
            named.size, named.count { RendererHandler.matchesModel(it.customName?.string.orEmpty()) }
        )
        named.sortedBy { it.distanceToSqr(player) }.take(MAX_ENTITIES).forEach { entity ->
            val name = entity.customName?.string.orEmpty()
            log.info(
                "[Lantern]   entity {} {} dist={} name=\"{}\" nonAscii=[{}] normalized=\"{}\" matched={}",
                entity.id, EntityType.getKey(entity.type), "%.1f".format(Math.sqrt(entity.distanceToSqr(player))),
                name, nonAsciiCodepoints(name), RendererHandler.normalizedName(name), RendererHandler.matchesModel(name)
            )
        }

        log.info(
            "[Lantern] item icons: configured={} meshStates={}",
            ResourceHandler.itemIconCount(), ItemIconMeshes.stateCounts()
        )
        val seen = ItemIconModels.drainSeen()
        if (seen.isNotEmpty()) {
            log.info("[Lantern]   CustomModelData seen by item hook: {}", seen.joinToString(" ") { cmd ->
                val icon = ResourceHandler.getItemIconSpec(cmd) != null
                "$cmd:${if (icon) ItemIconMeshes.stateOf(cmd) else "not_icon"}"
            })
        }

        val characters = ResourceHandler.characterTexturePaths()
        log.info(
            "[Lantern] hud roots: base={} top={} | character icons: {} (ready {})",
            UiScreenStorage.hudRoots(HudLayer.BASE).size, UiScreenStorage.hudRoots(HudLayer.TOP).size,
            characters.size, characters.count(LanternAssets::isTexturePathReady)
        )

        val counts = RenderProbe.drain()
        log.info(
            "[Lantern] render probe (15 s): {}",
            if (counts.isEmpty()) "no hits" else counts.entries.joinToString(" ") { "${it.key}=${it.value}" }
        )
        reportBackgroundWorkers()
        log.info("[Lantern] ==== END RENDER READINESS ====")
    }

    /**
     * 物品图标在 Lantern-Assets 线程上用 ImageIO 读贴图；这两样在某些 JRE 上可能卡住或缺失，
     * 放到独立线程里查，避免拖住渲染线程。卡住时对应那一行不会出现
     */
    private fun reportBackgroundWorkers() {
        Thread({
            val startedAt = System.nanoTime()
            val executor = runCatching {
                CompletableFuture.runAsync({}, AssetSync.executor).get(5, TimeUnit.SECONDS)
                "responds in ${(System.nanoTime() - startedAt) / 1_000_000} ms"
            }.getOrElse { "no response within 5 s (${it.javaClass.simpleName})" }
            Lantern.logger.info("[Lantern]   Lantern-Assets executor {}", executor)
            val readers = runCatching {
                ImageIO.getReaderFormatNames().map { it.lowercase() }.distinct().sorted().joinToString(",")
            }.getOrElse { it.toString() }
            Lantern.logger.info("[Lantern]   ImageIO readers: {}", readers)
        }, "Lantern-Diagnostics").apply { isDaemon = true }.start()
    }

    private fun nonAsciiCodepoints(text: String): String =
        text.codePoints().filter { it > 0x7E }.distinct().limit(16).toArray()
            .joinToString(" ") { "U+%04X".format(it) }
}
