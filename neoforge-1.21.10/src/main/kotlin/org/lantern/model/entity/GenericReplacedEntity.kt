package org.lantern.model.entity

import net.minecraft.world.entity.EntityType
import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.renderstate.LanternDataTickets
import software.bernie.geckolib.animatable.GeoReplacedEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animatable.processing.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.concurrent.ConcurrentHashMap

class GenericReplacedEntity(
    private val entityType: EntityType<*>
) : GeoReplacedEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)
    private val loopAnimations = ConcurrentHashMap<String, RawAnimation>()
    private val playAnimations = ConcurrentHashMap<String, RawAnimation>()

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<GenericReplacedEntity>("movement", 5) { test ->
                val data = requireNotNull(test.getData(LanternDataTickets.REPLACED_ENTITY))
                val animation = if (test.isMoving) {
                    data.animationStates.walk
                } else {
                    data.animationStates.idle
                }
                test.setAndContinue(loop(animation))
            }
        )
        controllers.add(
            AnimationController<GenericReplacedEntity>("action", 0) { test ->
                val data = requireNotNull(test.getData(LanternDataTickets.REPLACED_ENTITY))
                when (data.actionState) {
                    EntityAnimationState.DEATH -> data.animationStates.death?.let { test.setAndContinue(play(it)) }
                    EntityAnimationState.HURT -> data.animationStates.hurt?.let { test.setAndContinue(play(it)) }
                    EntityAnimationState.ATTACK -> data.animationStates.attack?.let { test.setAndContinue(loop(it)) }
                    else -> null
                } ?: PlayState.STOP
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getReplacingEntityType(): EntityType<*> = entityType

    private fun loop(name: String): RawAnimation =
        loopAnimations.computeIfAbsent(name) { RawAnimation.begin().thenLoop(it) }

    private fun play(name: String): RawAnimation =
        playAnimations.computeIfAbsent(name) { RawAnimation.begin().thenPlay(it) }
}
