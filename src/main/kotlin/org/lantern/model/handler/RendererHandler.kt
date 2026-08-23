package org.lantern.model.handler

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation

import org.lantern.platform.IdentifierBridge
import net.minecraft.world.entity.EntityType
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import org.lantern.internal.handler.CycleHandler
import org.lantern.model.renderer.GenericGeoRenderer
import org.lantern.model.wrapper.AnimationStateMapping
import org.lantern.model.wrapper.CustomModelWrapper

object RendererHandler {
    private val customModelWrappers = mutableMapOf<String, CustomModelWrapper>()
    private val renderers = mutableMapOf<EntityType<*>, MutableMap<String, GenericGeoRenderer<*>>>()
    private val formatCodeRegex = Regex("\u00A7.")
    var version: Int = 0
        private set

    fun reload() {
        customModelWrappers.clear()
        renderers.clear()
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

            // 解析状态映射
            val statesObj = if (animationsObj.has("states") && animationsObj.get("states").isJsonObject) {
                animationsObj.getAsJsonObject("states")
            } else {
                null
            }

            animationStates = AnimationStateMapping(
                idle = statesObj?.get("idle")?.asString ?: "idle",
                walk = statesObj?.get("walk")?.asString ?: "walk",
                attack = statesObj?.get("attack")?.asString,
                hurt = statesObj?.get("hurt")?.asString,
                death = statesObj?.get("death")?.asString
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
            obj.get("scale").asFloat,
            obj.get("height").asDouble,
            obj.get("width").asDouble,
            obj.get("hidden").asBoolean,
            obj.get("offset-y").asFloat,
            animationStates,
            textureUrl
        )
        val normalizedName = normalizeName(name)

        // 清除对应的渲染器缓存，确保下次 getRenderer() 时创建新的渲染器实例
        // 需要清除所有标准化名称匹配的缓存，因为缓存键可能是带颜色代码的名称（如 "§aTEST"）
        renderers.values.forEach { nameMap ->
            nameMap.entries.removeIf { normalizeName(it.key) == normalizedName }
        }

        customModelWrappers[name] = wrapper
        // 避免冗余存储
        if (name != normalizedName) {
            customModelWrappers[normalizedName] = wrapper
        }
        version++
    }

    fun getRenderer(entityType: EntityType<*>, customName: String): GenericGeoRenderer<*>? {
        if (CycleHandler.context == null) return null
        val wrapper = getCustomModelWrapper(customName) ?: return null
        val renderers = renderers.computeIfAbsent(entityType) { mutableMapOf() }
        return renderers.computeIfAbsent(customName) { GenericGeoRenderer(entityType, wrapper) }
    }

    fun getCustomModelWrapper(customName: String): CustomModelWrapper? {
        return customModelWrappers[customName] ?: customModelWrappers[normalizeName(customName)]
    }

    private fun normalizeName(name: String): String {
        if (name.indexOf('§') < 0) return name
        return formatCodeRegex.replace(name, "")
    }
}
