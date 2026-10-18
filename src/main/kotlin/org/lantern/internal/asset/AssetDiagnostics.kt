package org.lantern.internal.asset

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern

/**
 * 后台加载只覆盖 Lantern 自己按需读取的资源；原版管线里只在完整重载时读取的文件
 * （声音表、语言、字体、方块状态、图集贴图……）放进加密包后不会生效，这里把它们点出来
 */
object AssetDiagnostics {

    /** 任何命名空间下都只在完整重载时读取 */
    private val reloadOnlyPrefixes = listOf(
        "lang/", "font/", "shaders/", "atlases/", "particles/", "post_effect/", "texts/", "equipment/"
    )

    /** 原版图集与模型烘焙读取；lantern 命名空间下的同类文件由 Lantern 自己按需读取，不受影响 */
    private val vanillaPipelinePrefixes = listOf(
        "blockstates/", "models/", "items/",
        "textures/block/", "textures/item/", "textures/gui/sprites/", "textures/particle/",
        "textures/mob_effect/", "textures/painting/", "textures/trims/", "textures/map/"
    )

    fun reportReloadOnlyAssets(keys: Collection<ResourceLocation>) {
        val hits = keys.filter(::needsFullReload)
        if (hits.isEmpty()) return
        Lantern.logger.warn(
            "[Lantern] 加密资源包里有 {} 个文件只在完整资源重载时生效，后台加载不会应用（例如 {}）。" +
                "需要时按 F3+T，或在服务端 config.yml 打开 client-full-reload",
            hits.size, hits.take(6).joinToString()
        )
    }

    /** 资源管理器只为已有的命名空间查找动态资源，加密包带来的新命名空间要等完整重载 */
    fun reportUnknownNamespaces(keys: Collection<ResourceLocation>) {
        val known = Minecraft.getInstance().resourceManager.namespaces
        val unknown = keys.mapTo(sortedSetOf()) { it.namespace }.filterNot(known::contains)
        if (unknown.isEmpty()) return
        Lantern.logger.warn(
            "[Lantern] 加密资源包使用了尚未加载过的命名空间 {}，其中的资源要在完整资源重载（F3+T）后才能读取",
            unknown
        )
    }

    private fun needsFullReload(location: ResourceLocation): Boolean {
        val path = location.path
        if (path == "sounds.json" || reloadOnlyPrefixes.any(path::startsWith)) return true
        if (location.namespace == Lantern.MOD_ID) return false
        return vanillaPipelinePrefixes.any(path::startsWith)
    }
}
