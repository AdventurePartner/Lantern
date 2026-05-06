package org.lantern.internal.wrapper.resource

import net.minecraft.resources.ResourceLocation
import org.lantern.platform.IdentifierBridge
import org.lantern.Lantern
import java.io.ByteArrayInputStream
import java.io.InputStream

class ItemIconResourceWrapperImpl(rawIdentifier: String, private val texturePath: String, private val type: String = "generated") : IResourceWrapper {
    // 清理 identifier，移除可能的 namespace 前缀
    private val identifier: String = if (rawIdentifier.contains(":")) {
        rawIdentifier.substringAfter(":")
    } else {
        rawIdentifier
    }

    // 主资源路径：loadItemModelAndDependencies 查找 models/item/{identifier}.json
    private val primaryLocation: ResourceLocation =
        IdentifierBridge.of(Lantern.MOD_ID, "models/item/$identifier.json")

    // 备用资源路径：某些情况下可能查找 models/{identifier}.json
    private val secondaryLocation: ResourceLocation =
        IdentifierBridge.of(Lantern.MOD_ID, "models/$identifier.json")

    override fun getResourceLocation(): ResourceLocation = primaryLocation

    fun getSecondaryResourceLocation(): ResourceLocation = secondaryLocation

    fun getTexturePath(): String = texturePath

    fun getType(): String = type

    override fun getResource(): InputStream {
        val parent = when (type) {
            "handheld" -> "minecraft:item/handheld"
            else -> "minecraft:item/generated"
        }
        val modelJson = """
            {
              "parent": "$parent",
              "textures": {
                "layer0": "$texturePath"
              }
            }
        """.trimIndent()
        return ByteArrayInputStream(modelJson.toByteArray(Charsets.UTF_8))
    }
}
