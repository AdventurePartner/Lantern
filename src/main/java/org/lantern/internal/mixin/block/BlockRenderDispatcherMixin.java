package org.lantern.internal.mixin.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.lantern.model.handler.BlockRendererHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阻止 SectionCompiler 在已注册自定义方块的位置渲染原版蘑菇方块模型。
 *
 * 放置自定义方块时，客户端先收到默认蘑菇状态（all faces=true），
 * getRenderShape 返回 MODEL，导致蘑菇纹理闪一帧。
 * 此 Mixin 通过位置匹配提前拦截，避免闪烁。
 *
 * 性能：先做 isCarrierBlock 快速过滤（3 次 block identity 比较），
 * 非蘑菇方块直接放行；仅对蘑菇方块才查 blockPositions（ConcurrentHashMap.get）。
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {

    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true)
    private void lantern$skipTrackedBlock(
            BlockState blockState,
            BlockPos blockPos,
            BlockAndTintGetter blockAndTintGetter,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            boolean bl,
            RandomSource randomSource,
            CallbackInfo ci
    ) {
        if (BlockRendererHandler.INSTANCE.isCarrierBlock(blockState)
                && BlockRendererHandler.INSTANCE.getVariation(blockPos) != null) {
            ci.cancel();
        }
    }
}
