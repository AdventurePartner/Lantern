package org.lantern.model.entity

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.util.EntityStateUtil
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.animatable.GeoReplacedEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.core.`object`.PlayState
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.concurrent.ConcurrentHashMap

class GenericReplacedEntity<T : Entity>(private val entityType: EntityType<T>) : GeoReplacedEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    private val loopCache = ConcurrentHashMap<String, RawAnimation>()
    private val playCache = ConcurrentHashMap<String, RawAnimation>()

    private fun cachedLoop(name: String): RawAnimation =
        loopCache.getOrPut(name) { RawAnimation.begin().thenLoop(name) }

    private fun cachedPlay(name: String): RawAnimation =
        playCache.getOrPut(name) { RawAnimation.begin().thenPlay(name) }

    var currentAnimationStates: AnimationStateMapping? = null

    var currentRenderedEntity: Entity? = null

    private var lastState: EntityAnimationState = EntityAnimationState.IDLE

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "movement", 5) { state ->
            val animStates = currentAnimationStates ?: return@AnimationController PlayState.CONTINUE
            val entity = currentRenderedEntity ?: return@AnimationController PlayState.CONTINUE

            val currentState = EntityStateUtil.detectAnimationState(entity)

            when (currentState) {
                EntityAnimationState.WALK -> {
                    state.controller.setAnimation(cachedLoop(animStates.walk))
                }
                EntityAnimationState.IDLE -> {
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
                else -> {
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
            }

            lastState = currentState
            PlayState.CONTINUE
        })

        controllers.add(AnimationController(this, "action", 0) { state ->
            val animStates = currentAnimationStates ?: return@AnimationController PlayState.STOP
            val entity = currentRenderedEntity ?: return@AnimationController PlayState.STOP

            val currentState = EntityStateUtil.detectAnimationState(entity)

            when (currentState) {
                EntityAnimationState.DEATH -> {
                    animStates.death?.let { deathAnim ->
                        state.controller.setAnimation(cachedPlay(deathAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                }
                EntityAnimationState.HURT -> {
                    animStates.hurt?.let { hurtAnim ->
                        state.controller.setAnimation(cachedPlay(hurtAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                }
                EntityAnimationState.ATTACK -> {
                    animStates.attack?.let { attackAnim ->
                        state.controller.setAnimation(cachedLoop(attackAnim))
                        return@AnimationController PlayState.CONTINUE
                    }
                }
                else -> {}
            }

            PlayState.STOP
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return cache
    }

    override fun getReplacingEntityType(): EntityType<*> {
        return entityType
    }
}
