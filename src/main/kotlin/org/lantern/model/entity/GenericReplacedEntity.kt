package org.lantern.model.entity

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import software.bernie.geckolib.animatable.GeoReplacedEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * GeckoLib 替换实体的壳实现（Fabric 1.21.1 / NeoForge 1.21.1；1.20.1 与 1.21.10 各有副本）。
 *
 * 刻意不注册任何控制器：动画决策、时间轴与骨骼写入全部由 Lantern 的 AnimationHost 接管，
 * GeckoLib 只负责资产解析与渲染
 */
class GenericReplacedEntity<T : Entity>(private val entityType: EntityType<T>) : GeoReplacedEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) = Unit

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getReplacingEntityType(): EntityType<*> = entityType
}
