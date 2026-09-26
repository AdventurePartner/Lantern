package org.lantern.model.renderstate

import java.util.UUID
import org.lantern.core.anim.control.AnimationControlStore
import org.lantern.core.anim.statemap.AnimationStateMapping
import software.bernie.geckolib.constant.dataticket.DataTicket

/**
 * 载体绑定的渲染重定向量。提取阶段（能拿到实体与 partialTick）算好，
 * 渲染阶段（只有 renderState）消费。
 *
 * @param dx/dy/dz 世界坐标增量：载体插值位置 -> 宿主插值位置 + 偏移
 * @param hostBodyYaw 宿主身体朝向（度）；null = rotate=false，载体保持自己的朝向
 */
class BindTransform(
    val dx: Double,
    val dy: Double,
    val dz: Double,
    val hostBodyYaw: Float?
)

data class ReplacedRenderData(
    val rendererKey: String,
    val animationStates: AnimationStateMapping,
    val entityUuid: UUID,
    val forcedAnimation: AnimationControlStore.ForcedAnimation?,
    /** 渲染阶段从此字段读取骨骼姿势（绕开批量提取阶段的共享骨骼冲突） */
    val pose: Map<String, FloatArray>? = null,
    /** 载体绑定命中时的渲染重定向；null = 正常按自身位置渲染 */
    val bindTransform: BindTransform? = null
)

object LanternDataTickets {
    @JvmField
    val REPLACED_ENTITY: DataTicket<ReplacedRenderData> = DataTicket.create(
        "lantern:replaced_entity",
        ReplacedRenderData::class.java
    )
}
