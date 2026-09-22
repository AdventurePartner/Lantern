package org.lantern.costume.wrapper

import net.minecraft.resources.ResourceLocation
import org.lantern.costume.slot.CostumeSlot
import org.lantern.core.anim.statemap.AnimationStateMapping
import org.lantern.core.bone.BoneMapping

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
    val boneMapping: BoneMapping = BoneMapping(),
    /**
     * P1 玩家宿主化：true 时该外观由 AnimationHost 驱动（四层层栈姿态直写骨骼，
     * 状态表/播控/一次性动作全量生效），不再走六部件快照与 GeckoLib 旧控制器。
     * 整替玩家的标准配置；普通装饰外观保持 false 走原快照路径
     */
    val hostDriven: Boolean = false
)
