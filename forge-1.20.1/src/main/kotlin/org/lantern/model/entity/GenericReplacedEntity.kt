package org.lantern.model.entity

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import software.bernie.geckolib.animatable.GeoReplacedEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil

/** 共享 GenericReplacedEntity 的 1.20.1 版（GeckoLib 4.x 旧包名），同样不注册任何控制器 */
class GenericReplacedEntity<T : Entity>(private val entityType: EntityType<T>) : GeoReplacedEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) = Unit

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getReplacingEntityType(): EntityType<*> = entityType
}
