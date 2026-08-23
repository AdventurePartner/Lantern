package org.lantern.costume.entity

import net.minecraft.world.entity.Entity
import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.util.EntityStateUtil
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.core.animatable.GeoAnimatable
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.core.`object`.PlayState
import software.bernie.geckolib.util.GeckoLibUtil
import software.bernie.geckolib.util.RenderUtils
import java.util.concurrent.ConcurrentHashMap

class CostumeAnimatable : GeoAnimatable {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    private val loopCache = ConcurrentHashMap<String, RawAnimation>()
    private val playCache = ConcurrentHashMap<String, RawAnimation>()

    private fun cachedLoop(name: String): RawAnimation =
        loopCache.getOrPut(name) { RawAnimation.begin().thenLoop(name) }

    private fun cachedPlay(name: String): RawAnimation =
        playCache.getOrPut(name) { RawAnimation.begin().thenPlay(name) }

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
                        state.controller.setAnimation(cachedPlay(deathAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
                EntityAnimationState.HURT -> {
                    animStates.hurt?.let { hurtAnim ->
                        state.controller.setAnimation(cachedPlay(hurtAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
                EntityAnimationState.ATTACK -> {
                    animStates.attack?.let { attackAnim ->
                        state.controller.setAnimation(cachedLoop(attackAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
                EntityAnimationState.WALK -> {
                    state.controller.setAnimation(cachedLoop(animStates.walk))
                }
                EntityAnimationState.IDLE -> {
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
            }

            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getTick(obj: Any?): Double {
        return RenderUtils.getCurrentTick()
    }
}
