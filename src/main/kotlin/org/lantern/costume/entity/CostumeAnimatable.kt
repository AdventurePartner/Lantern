package org.lantern.costume.entity

import net.minecraft.world.entity.Entity
import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.util.EntityStateUtil
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import software.bernie.geckolib.util.RenderUtil

class CostumeAnimatable : GeoAnimatable {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    var currentEntity: Entity? = null
    var currentAnimationStates: AnimationStateMapping? = null

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "costume_movement", 5) { state ->
            val animStates = currentAnimationStates ?: return@AnimationController PlayState.CONTINUE
            val entity = currentEntity ?: return@AnimationController PlayState.CONTINUE

            val currentState = EntityStateUtil.detectAnimationState(entity)

            when (currentState) {
                EntityAnimationState.DEATH -> {
                    animStates.death?.let { deathAnim ->
                        state.controller.setAnimation(RawAnimation.begin().thenPlay(deathAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                    state.controller.setAnimation(RawAnimation.begin().thenLoop(animStates.idle))
                }
                EntityAnimationState.HURT -> {
                    animStates.hurt?.let { hurtAnim ->
                        state.controller.setAnimation(RawAnimation.begin().thenPlay(hurtAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                    state.controller.setAnimation(RawAnimation.begin().thenLoop(animStates.idle))
                }
                EntityAnimationState.ATTACK -> {
                    animStates.attack?.let { attackAnim ->
                        state.controller.setAnimation(RawAnimation.begin().thenLoop(attackAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                    state.controller.setAnimation(RawAnimation.begin().thenLoop(animStates.idle))
                }
                EntityAnimationState.WALK -> {
                    state.controller.setAnimation(RawAnimation.begin().thenLoop(animStates.walk))
                }
                EntityAnimationState.IDLE -> {
                    state.controller.setAnimation(RawAnimation.begin().thenLoop(animStates.idle))
                }
            }

            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getTick(obj: Any?): Double {
        return RenderUtil.getCurrentTick()
    }
}
