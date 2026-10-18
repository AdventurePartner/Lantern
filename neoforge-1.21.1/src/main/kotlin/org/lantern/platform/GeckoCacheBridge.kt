package org.lantern.platform

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.InactiveProfiler
import software.bernie.geckolib.cache.GeckoLibCache
import java.util.concurrent.CompletableFuture

object GeckoCacheBridge {
    @JvmStatic
    fun hasModel(location: ResourceLocation): Boolean = GeckoLibCache.getBakedModels().containsKey(location)

    /** 单独跑一遍 GeckoLib 的 reload listener：后台解析，主线程原子替换缓存表，不触发整包重载 */
    @JvmStatic
    fun refresh(manager: ResourceManager): CompletableFuture<Void> = GeckoLibCache.reload(
        ImmediateBarrier, manager, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE,
        Util.backgroundExecutor(), Minecraft.getInstance()
    )

    private object ImmediateBarrier : PreparableReloadListener.PreparationBarrier {
        override fun <T> wait(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)
    }
}
