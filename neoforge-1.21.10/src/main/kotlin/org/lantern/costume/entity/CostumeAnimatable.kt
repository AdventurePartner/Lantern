package org.lantern.costume.entity

import org.lantern.costume.renderstate.CostumeRenderData
import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animatable.processing.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.ClientUtil
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.concurrent.ConcurrentHashMap

class CostumeAnimatable(
    private val animations: AnimationStateMapping
) : GeoAnimatable {
    private val cache = GeckoLibUtil.createInstanceCache(this)
    private val loopAnimations = ConcurrentHashMap<String, RawAnimation>()
    private val playAnimations = ConcurrentHashMap<String, RawAnimation>()

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<CostumeAnimatable>("costume", 5) { test ->
                when (requireNotNull(test.getData(CostumeRenderData.ANIMATION_STATE))) {
                    EntityAnimationState.DEATH -> animations.death?.let { test.setAndContinue(play(it)) }
                    EntityAnimationState.HURT -> animations.hurt?.let { test.setAndContinue(play(it)) }
                    EntityAnimationState.ATTACK -> animations.attack?.let { test.setAndContinue(loop(it)) }
                    EntityAnimationState.WALK -> test.setAndContinue(loop(animations.walk))
                    EntityAnimationState.IDLE -> test.setAndContinue(loop(animations.idle))
                } ?: test.setAndContinue(loop(animations.idle))
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getTick(obj: Any?): Double = ClientUtil.getCurrentTick()

    private fun loop(name: String): RawAnimation =
        loopAnimations.computeIfAbsent(name) { RawAnimation.begin().thenLoop(it) }

    private fun play(name: String): RawAnimation =
        playAnimations.computeIfAbsent(name) { RawAnimation.begin().thenPlay(it) }
}
