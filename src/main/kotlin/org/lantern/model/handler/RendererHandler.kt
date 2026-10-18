package org.lantern.model.handler

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import net.minecraft.world.entity.EntityType
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.asset.LanternAssets
import org.lantern.model.renderer.GenericGeoRenderer
import org.lantern.core.anim.statemap.AnimationStateMapping
import org.lantern.model.wrapper.CustomModelWrapper
import org.lantern.animation.AnimationHost
import org.lantern.animation.AnimationRepository
import org.lantern.core.anim.control.AnimationControlStore
import java.util.UUID

object RendererHandler {
    private val customModelWrappers = mutableMapOf<String, CustomModelWrapper>()

    /** 实体类型 -> (实体 UUID -> 渲染器)：每个实体一份克隆骨骼树，同名多实体的姿势互不覆盖 */
    private val renderers = mutableMapOf<EntityType<*>, MutableMap<UUID, GenericGeoRenderer<*>>>()
    private val formatCodeRegex = Regex("\u00A7.")
    private val unmatchedLogged = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    var version: Int = 0
        private set

    fun reload() {
        customModelWrappers.clear()
        renderers.clear()
        AnimationControlStore.reset()
        AnimationControlHandler.resetDiagnostics()
        AnimationRepository.reset()
        AnimationHost.reset()
        version++
    }

    fun addEntityModel(name: String, obj: JsonObject) {
        // 一次提取路径，复用变量
        val geoPath = obj.get("geo").asString
        val texturePath = obj.get("texture").asString
        val isHttpTexture = TextureHandler.isHttpUrl(texturePath)

        val geo = IdentifierBridge.of(Lantern.MOD_ID, geoPath)

        val texture: ResourceLocation
        val textureUrl: String?
        if (isHttpTexture) {
            texture = TextureHandler.getTexture(texturePath)
            textureUrl = texturePath
        } else {
            texture = IdentifierBridge.of(Lantern.MOD_ID, texturePath)
            textureUrl = null
        }

        // 解析动画配置（支持新旧格式）
        val animationLocation: ResourceLocation
        val animationStates: AnimationStateMapping

        if (obj.has("animations") && obj.get("animations").isJsonObject) {
            // 新格式：animations 对象包含 file 和 states
            val animationsObj = obj.getAsJsonObject("animations")
            val animationPath = animationsObj.get("file").asString
            animationLocation = IdentifierBridge.of(Lantern.MOD_ID, animationPath)

            // 解析状态映射（表驱动，string=纯动画名 / object=完整播放语义）
            animationStates = AnimationStateMapping.fromStatesJson(
                animationsObj.getAsJsonObject("states")
            )
        } else if (obj.has("animation")) {
            // 旧格式：单一 animation 字段
            val animationPath = obj.get("animation").asString
            animationLocation = IdentifierBridge.of(Lantern.MOD_ID, animationPath)

            // 使用默认动画状态映射
            animationStates = AnimationStateMapping.default("idle")
        } else {
            Lantern.logger.warn("No animation configuration found for model: $name")
            return
        }

        val wrapper = CustomModelWrapper(
            geo,
            texture,
            animationLocation,
            // 缺字段给默认值：直接 .asFloat 会抛 NPE，一条模型写漏就让整包 packet 2 解析中断
            obj.get("scale")?.asFloat ?: 1.0f,
            obj.get("height")?.asDouble ?: 1.0,
            obj.get("width")?.asDouble ?: 1.0,
            obj.get("hidden")?.asBoolean ?: false,
            obj.get("offset-y")?.asFloat ?: 0.0f,
            animationStates,
            textureUrl
        )
        val normalizedName = normalizeName(name)

        // 清除用旧定义建出来的渲染器，下次 getRenderer() 按新定义重建
        renderers.values.forEach { byUuid ->
            byUuid.values.removeIf { normalizeName(it.rendererKey) == normalizedName }
        }

        customModelWrappers[name] = wrapper
        // 避免冗余存储
        if (name != normalizedName) {
            customModelWrappers[normalizedName] = wrapper
        }
        version++
    }

    /** 资源刷新换了 GeckoLib 缓存：渲染器持有旧模型的骨骼深拷贝，丢掉待懒重建 */
    fun dropRenderers() {
        renderers.clear()
        AnimationRepository.reset()
    }

    fun getRenderer(entityType: EntityType<*>, customName: String, uuid: UUID): GenericGeoRenderer<*>? {
        if (CycleHandler.context == null) return null
        val wrapper = getCustomModelWrapper(customName) ?: run {
            // 名字未匹配时原版渲染器无声接管，是模型不渲染且无日志的主要静默分支，DEBUG 级留痕
            if (unmatchedLogged.add(customName)) {
                Lantern.logger.debug("[Lantern] 实体自定义名 \"{}\" 未匹配任何已注册模型，回退原版渲染器", customName)
            }
            return null
        }
        // 模型或贴图还在后台加载：先画原版模型，就绪后下一帧自动切换
        if (!LanternAssets.isModelReady(wrapper.modelLocation, wrapper.textureLocation, wrapper.textureUrl)) return null
        val byUuid = renderers.computeIfAbsent(entityType) { mutableMapOf() }
        val cached = byUuid[uuid]
        // 实体改名换了模型：旧渲染器的骨骼树属于旧模型，必须重建
        if (cached != null && cached.wrapper === wrapper) return cached
        return GenericGeoRenderer(entityType, normalizeName(customName), wrapper).also { byUuid[uuid] = it }
    }

    /** 实体离开世界时淘汰其渲染器，释放克隆骨骼树；重新进入视距时由 getRenderer 懒重建 */
    fun evict(uuid: UUID) {
        renderers.values.forEach { it.remove(uuid) }
    }

    fun getCustomModelWrapper(customName: String): CustomModelWrapper? {
        return customModelWrappers[customName] ?: customModelWrappers[normalizeName(customName)]
    }

    fun getCustomModelWrappers(): Map<String, CustomModelWrapper> = customModelWrappers

    private fun normalizeName(name: String): String {
        if (name.indexOf('§') < 0) return name
        return formatCodeRegex.replace(name, "")
    }
}
