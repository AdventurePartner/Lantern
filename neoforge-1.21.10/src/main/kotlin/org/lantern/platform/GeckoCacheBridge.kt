package org.lantern.platform

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import org.lantern.model.GeckoResourceIds
import software.bernie.geckolib.cache.GeckoLibResources
import java.util.concurrent.CompletableFuture

object GeckoCacheBridge {
    /** Lantern 的 geo 路径（lantern:geo/x.geo.json）要先换成 GeckoLib 5 去掉前后缀的键 */
    @JvmStatic
    fun hasModel(location: ResourceLocation): Boolean =
        GeckoLibResources.getBakedModels().containsKey(GeckoResourceIds.model(location))

    /** 单独跑一遍 GeckoLib 的 reload listener：后台解析，主线程原子替换缓存表，不触发整包重载 */
    @JvmStatic
    fun refresh(manager: ResourceManager): CompletableFuture<Void> = GeckoLibResources.reload(
        PreparableReloadListener.SharedState(manager), Util.backgroundExecutor(), ImmediateBarrier, Minecraft.getInstance()
    )

    private object ImmediateBarrier : PreparableReloadListener.PreparationBarrier {
        override fun <T> wait(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)
    }
}
