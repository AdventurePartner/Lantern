package org.lantern.model.renderstate

import java.util.UUID
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.constant.dataticket.DataTicket

data class ReplacedRenderData(
    val rendererKey: String,
    val animationStates: AnimationStateMapping,
    val entityUuid: UUID,
    val forcedAnimation: AnimationControlStore.ForcedAnimation?,
    /** 渲染阶段从此字段读取骨骼姿势（绕开批量提取阶段的共享骨骼冲突） */
    val pose: Map<String, FloatArray>? = null
)

object LanternDataTickets {
    @JvmField
    val REPLACED_ENTITY: DataTicket<ReplacedRenderData> = DataTicket.create(
        "lantern:replaced_entity",
        ReplacedRenderData::class.java
    )
}
