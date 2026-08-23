package org.lantern.model.renderstate

import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.constant.dataticket.DataTicket

data class ReplacedRenderData(
    val rendererKey: String,
    val animationStates: AnimationStateMapping,
    val actionState: EntityAnimationState?
)

object LanternDataTickets {
    @JvmField
    val REPLACED_ENTITY: DataTicket<ReplacedRenderData> = DataTicket.create(
        "lantern:replaced_entity",
        ReplacedRenderData::class.java
    )
}
