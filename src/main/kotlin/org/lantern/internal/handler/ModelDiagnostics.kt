package org.lantern.internal.handler

import net.minecraft.client.Minecraft
import net.minecraft.server.packs.resources.ResourceManager
import org.lantern.Lantern
import org.lantern.model.wrapper.CustomModelWrapper

/**
 * 实体模型资源的加载诊断。
 *
 * GeckoLib 只扫描客户端资源中 assets/<ns>/geo/ 与 assets/<ns>/animations/ 两个固定目录，
 * entityModels.yml 的路径前缀不合规或文件不在任何客户端资源包里时，渲染器会静默跳过渲染。
 * 这里在资源重载重建完成后集中校验并 WARN，把这类配置错误暴露到日志。
 *
 * 不查 GeckoLib 烘焙缓存：reload listener 与 GeckoLib 缓存 listener 的执行顺序无法保证，
 * 缓存缺失由渲染守卫（GenericGeoRenderer）负责提示。
 */
object ModelDiagnostics {

    private const val GEO_PREFIX = "geo/"
    private const val ANIMATION_PREFIX = "animations/"

    fun validateEntityModels(models: Map<String, CustomModelWrapper>) {
        if (models.isEmpty()) return
        val manager = Minecraft.getInstance().resourceManager
        // 同一 wrapper 会以原始名/去色名注册两次，按 wrapper 去重避免重复告警
        val validated = HashSet<CustomModelWrapper>()
        for ((name, wrapper) in models) {
            if (!validated.add(wrapper)) continue
            validateGeo(name, wrapper, manager)
            validateAnimation(name, wrapper, manager)
        }
    }

    private fun validateGeo(name: String, wrapper: CustomModelWrapper, manager: ResourceManager) {
        val location = wrapper.modelLocation
        if (!location.path.startsWith(GEO_PREFIX)) {
            Lantern.logger.warn(
                "[Lantern] 实体模型 {} 的 geo 路径 {} 不以 geo/ 开头：GeckoLib 只扫描 assets/lantern/geo/，" +
                    "请检查 entityModels.yml 路径与客户端资源包内的实际位置",
                name, location
            )
        }
        if (manager.getResource(location).isEmpty) {
            Lantern.logger.warn(
                "[Lantern] 实体模型 {} 的 geo 文件 {} 不在客户端资源中，模型将不会渲染；" +
                    "请确认文件已放入资源包 assets/lantern/ 下且与配置路径一致",
                name, location
            )
        }
    }

    private fun validateAnimation(name: String, wrapper: CustomModelWrapper, manager: ResourceManager) {
        val location = wrapper.animationLocation
        if (!location.path.startsWith(ANIMATION_PREFIX)) {
            Lantern.logger.warn(
                "[Lantern] 实体模型 {} 的动画路径 {} 不以 animations/ 开头：GeckoLib 只扫描 assets/lantern/animations/",
                name, location
            )
        }
        if (manager.getResource(location).isEmpty) {
            Lantern.logger.warn(
                "[Lantern] 实体模型 {} 的动画文件 {} 不在客户端资源中；" +
                    "请确认文件已放入资源包 assets/lantern/ 下且与配置路径一致",
                name, location
            )
        }
    }
}
