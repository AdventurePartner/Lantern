package org.lantern.platform

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.InactiveProfiler
import net.minecraft.util.profiling.ProfilerFiller
import software.bernie.geckolib.cache.GeckoLibCache
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object GeckoCacheBridge {
    /** GeckoLib 4.7.1.2 (1.20.1) 的 reload 是 private；打包时整个 GeckoLib 被重定位，类字面量随之改写 */
    private val reloadMethod: Method by lazy {
        GeckoLibCache::class.java.getDeclaredMethod(
            "reload",
            PreparableReloadListener.PreparationBarrier::class.java,
            ResourceManager::class.java,
            ProfilerFiller::class.java,
            ProfilerFiller::class.java,
            Executor::class.java,
            Executor::class.java
        ).apply { isAccessible = true }
    }

    @JvmStatic
    fun hasModel(location: ResourceLocation): Boolean = GeckoLibCache.getBakedModels().containsKey(location)

    /** 单独跑一遍 GeckoLib 的 reload listener：后台解析，主线程原子替换缓存表，不触发整包重载 */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun refresh(manager: ResourceManager): CompletableFuture<Void> = reloadMethod.invoke(
        null, ImmediateBarrier, manager, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE,
        Util.backgroundExecutor(), Minecraft.getInstance()
    ) as CompletableFuture<Void>

    private object ImmediateBarrier : PreparableReloadListener.PreparationBarrier {
        override fun <T> wait(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)
    }
}
