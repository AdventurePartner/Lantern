package org.lantern.costume.wrapper

import net.minecraft.resources.ResourceLocation
import org.lantern.model.wrapper.AnimationStateMapping

class CostumeModelWrapper(
    val id: String,
    val displayName: String,
    val modelLocation: ResourceLocation,
    val textureLocation: ResourceLocation,
    val animationLocation: ResourceLocation,
    val scale: Float = 1.0F,
    val offsetX: Float = 0.0F,
    val offsetY: Float = 0.0F,
    val offsetZ: Float = 0.0F,
    val animationStates: AnimationStateMapping = AnimationStateMapping.default(),
    val textureUrl: String? = null
)
