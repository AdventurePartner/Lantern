package org.lantern.model.entity

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import org.lantern.model.enums.EntityAnimationState
import org.lantern.model.util.EntityStateUtil
import org.lantern.model.wrapper.AnimationStateMapping
import software.bernie.geckolib.animatable.GeoReplacedEntity
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import java.util.concurrent.ConcurrentHashMap
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.util.GeckoLibUtil

class GenericReplacedEntity<T : Entity>(private val entityType: EntityType<T>) : GeoReplacedEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    private val loopCache = ConcurrentHashMap<String, RawAnimation>()
    private val playCache = ConcurrentHashMap<String, RawAnimation>()

    private fun cachedLoop(name: String): RawAnimation =
        loopCache.getOrPut(name) { RawAnimation.begin().thenLoop(name) }

    private fun cachedPlay(name: String): RawAnimation =
        playCache.getOrPut(name) { RawAnimation.begin().thenPlay(name) }

    // 当前使用的动画状态映射（由渲染器设置）
    var currentAnimationStates: AnimationStateMapping? = null

    // 当前渲染的实体引用
    var currentRenderedEntity: Entity? = null

    // 用于跟踪上一帧的动画状态，避免频繁切换
    private var lastState: EntityAnimationState = EntityAnimationState.IDLE

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        // 主移动画控制器（idle/walk） - 低优先级
        controllers.add(AnimationController(this, "movement", 5) { state ->
            val animStates = currentAnimationStates ?: return@AnimationController PlayState.CONTINUE
            val entity = currentRenderedEntity ?: return@AnimationController PlayState.CONTINUE

            val currentState = EntityStateUtil.detectAnimationState(entity)

            // 根据状态设置动画
            when (currentState) {
                EntityAnimationState.WALK -> {
                    state.controller.setAnimation(cachedLoop(animStates.walk))
                }
                EntityAnimationState.IDLE -> {
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
                else -> {
                    // 对于其他状态（DEATH, HURT, ATTACK），使用 idle 作为基础
                    state.controller.setAnimation(cachedLoop(animStates.idle))
                }
            }

            lastState = currentState
            PlayState.CONTINUE
        })

        // 动作动画控制器（hurt/death/attack）- 高优先级，触发式
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
                else -> { /* 其他状态由 movement 控制器处理 */ }
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
