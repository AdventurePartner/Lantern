package org.lantern.model.block

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import org.lantern.platform.IdentifierBridge
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import org.lantern.Lantern
import org.lantern.model.wrapper.BlockModelWrapper
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import java.util.concurrent.ConcurrentHashMap
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * GeckoLib 方块实体。
 * 不存储持久化 NBT — 这是纯客户端的虚拟 BlockEntity，
 * 由 BlockRendererHandler 缓存管理，不放置到世界中。
 * 模型信息通过位置映射从 BlockRendererHandler 查找。
 */
class LanternBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(TYPE, pos, state), GeoBlockEntity {

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    private val loopCache = ConcurrentHashMap<String, RawAnimation>()

    private fun cachedLoop(name: String): RawAnimation =
        loopCache.getOrPut(name) { RawAnimation.begin().thenLoop(name) }

    /** 当前关联的模型配置，由创建时设置 */
    var wrapper: BlockModelWrapper? = null

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "block_idle", 0) { state ->
            val w = wrapper ?: return@AnimationController PlayState.STOP
            if (w.animationLocation == null) return@AnimationController PlayState.STOP
            state.setAndContinue(cachedLoop(w.idleAnimation))
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    companion object {
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val TYPE: BlockEntityType<LanternBlockEntity> = BlockEntityType.Builder.of(
            ::LanternBlockEntity,
            Blocks.BARREL
        ).build(null)

        fun register() {
            net.minecraft.core.Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                IdentifierBridge.of(Lantern.MOD_ID, "custom_block"),
                TYPE
            )
        }
    }
}
