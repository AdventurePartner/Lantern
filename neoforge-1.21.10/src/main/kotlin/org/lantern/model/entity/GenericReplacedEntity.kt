package org.lantern.model.entity

import net.minecraft.world.entity.EntityType
import org.lantern.animation.AnimationHost
import software.bernie.geckolib.animatable.GeoReplacedEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * GeckoLib 替换实体的壳实现。
 *
 * 注意：这里刻意不注册任何谓词控制器——动画的决策、时间轴与骨骼写入全部由
 * Lantern AnimationHost（org.lantern.animation）接管，GeckoLib 仅负责资产解析与渲染。
 * GeckoLib 的控制器状态机（PlayState/STOP 残留/多控制器快照交错）不可托管，
 * 相关结论见 docs/animation-control-plan.md 第 0 章。
 */
class GenericReplacedEntity(
    private val entityType: EntityType<*>
) : GeoReplacedEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        // 无控制器：骨骼由 AnimationHost 直写（见 GenericGeoRenderer.addRenderData）
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getReplacingEntityType(): EntityType<*> = entityType

    companion object {
        @JvmStatic
        fun resetHost() = AnimationHost.reset()
    }
}
