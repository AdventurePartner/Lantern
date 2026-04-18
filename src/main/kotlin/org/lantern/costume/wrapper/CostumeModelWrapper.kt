package org.lantern.costume.wrapper

import net.minecraft.resources.ResourceLocation
import org.lantern.costume.bone.BoneMapping
import org.lantern.costume.slot.CostumeSlot
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
    val textureUrl: String? = null,
    val slot: CostumeSlot = CostumeSlot.FULL_BODY,
    val boneSyncEnabled: Boolean = true,
    val boneMapping: BoneMapping = BoneMapping()
)
