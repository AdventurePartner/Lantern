package org.lantern.model.handler

import com.google.gson.JsonObject
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import org.lantern.Lantern
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.wrapper.AnimationStateMapping
import org.lantern.model.wrapper.CustomModelWrapper
import org.lantern.model.renderer.GenericGeoRenderer
import org.lantern.platform.IdentifierBridge
import java.util.concurrent.ConcurrentHashMap

object RendererHandler {
    private val wrappers = ConcurrentHashMap<String, CustomModelWrapper>()
    private val renderers = ConcurrentHashMap<EntityType<*>, ConcurrentHashMap<String, GenericGeoRenderer<*>>>()
    private val formatCodeRegex = Regex("\u00A7.")
    @Volatile
    private var context: EntityRendererProvider.Context? = null

    var version: Int = 0
        private set

    fun reload() {
        wrappers.clear()
        renderers.clear()
        version++
    }

    fun updateContext(newContext: EntityRendererProvider.Context) {
        if (context !== newContext) {
            context = newContext
            renderers.clear()
        }
    }

    fun addEntityModel(name: String, obj: JsonObject) {
        val texturePath = obj.get("texture").asString
        val isHttpTexture = TextureHandler.isHttpUrl(texturePath)
        val texture: ResourceLocation
        val textureUrl: String?
        if (isHttpTexture) {
            texture = TextureHandler.getTexture(texturePath)
            textureUrl = texturePath
        } else {
            texture = IdentifierBridge.of(Lantern.MOD_ID, texturePath)
            textureUrl = null
        }

        val animationLocation: ResourceLocation
        val animationStates: AnimationStateMapping
        if (obj.has("animations") && obj.get("animations").isJsonObject) {
            val animations = obj.getAsJsonObject("animations")
            animationLocation = IdentifierBridge.of(Lantern.MOD_ID, animations.get("file").asString)
            val states = animations.getAsJsonObject("states")
            animationStates = AnimationStateMapping(
                idle = states?.get("idle")?.asString ?: "idle",
                walk = states?.get("walk")?.asString ?: "walk",
                attack = states?.get("attack")?.asString,
                hurt = states?.get("hurt")?.asString,
                death = states?.get("death")?.asString
            )
        } else if (obj.has("animation")) {
            animationLocation = IdentifierBridge.of(Lantern.MOD_ID, obj.get("animation").asString)
            animationStates = AnimationStateMapping.default()
        } else {
            Lantern.logger.warn("No animation configuration found for model: {}", name)
            return
        }

        val wrapper = CustomModelWrapper(
            modelLocation = IdentifierBridge.of(Lantern.MOD_ID, obj.get("geo").asString),
            textureLocation = texture,
            animationLocation = animationLocation,
            scale = obj.get("scale")?.asFloat ?: 1.0f,
            height = obj.get("height")?.asDouble ?: 1.0,
            width = obj.get("width")?.asDouble ?: 1.0,
            hiddenName = obj.get("hidden")?.asBoolean ?: false,
            nameTagOffsetY = obj.get("offset-y")?.asFloat ?: 0.0f,
            animationStates = animationStates,
            textureUrl = textureUrl
        )
        val normalizedName = normalizeName(name)
        renderers.values.forEach { byName ->
            byName.entries.removeIf { (cachedName, _) -> normalizeName(cachedName) == normalizedName }
        }
        wrappers[name] = wrapper
        wrappers[normalizedName] = wrapper
        version++
    }

    fun getRenderer(entityType: EntityType<*>, customName: String): GenericGeoRenderer<*>? {
        val rendererContext = context ?: return null
        val normalizedName = normalizeName(customName)
        val wrapper = getCustomModelWrapper(normalizedName) ?: return null
        return renderers.computeIfAbsent(entityType) { ConcurrentHashMap() }
            .computeIfAbsent(normalizedName) {
                GenericGeoRenderer.create(rendererContext, entityType, normalizedName, wrapper)
            }
    }

    fun getCustomModelWrapper(customName: String): CustomModelWrapper? =
        wrappers[customName] ?: wrappers[normalizeName(customName)]

    private fun normalizeName(name: String): String =
        if ('\u00A7' in name) formatCodeRegex.replace(name, "") else name
}
