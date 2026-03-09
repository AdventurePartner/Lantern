package org.lantern.model.wrapper

import net.minecraft.resources.ResourceLocation

class CustomModelWrapper(
    val modelLocation: ResourceLocation,
    val textureLocation: ResourceLocation,
    val animationLocation: ResourceLocation,
    val scale: Float = 1.0F,
    val height: Double = 1.0,
    val width: Double = 1.0,
    val hiddenName: Boolean = false,
    val nameTagOffsetY: Float = 0.0F,
    val animationStates: AnimationStateMapping = AnimationStateMapping.default(),
    val textureUrl: String? = null
)