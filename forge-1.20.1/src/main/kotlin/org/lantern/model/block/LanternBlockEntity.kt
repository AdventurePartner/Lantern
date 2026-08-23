package org.lantern.model.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.lantern.Lantern
import org.lantern.model.wrapper.BlockModelWrapper
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.core.`object`.PlayState
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.concurrent.ConcurrentHashMap

class LanternBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(TYPE.get(), pos, state), GeoBlockEntity {

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    private val loopCache = ConcurrentHashMap<String, RawAnimation>()

    private fun cachedLoop(name: String): RawAnimation =
        loopCache.getOrPut(name) { RawAnimation.begin().thenLoop(name) }

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
        private val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Lantern.MOD_ID)

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val TYPE: RegistryObject<BlockEntityType<LanternBlockEntity>> =
            BLOCK_ENTITY_TYPES.register("custom_block") {
                BlockEntityType.Builder.of(
                    ::LanternBlockEntity,
                    Blocks.BARREL
                ).build(null)
            }

        fun register(eventBus: IEventBus) {
            BLOCK_ENTITY_TYPES.register(eventBus)
        }
    }
}
