package org.lantern.internal.mixin.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.lantern.model.handler.BlockRendererHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 的区块编译调用的是带 ModelData / RenderType 的 9 参 renderBatched，
 * 共享 BlockRenderDispatcherMixin 挂的原版 7 参重载在 Forge 上从不执行。
 * 这里按位置跳过已跟踪的载体方块，避免放置时 open 标记到达前原版木桶闪一帧。
 * 该重载是 Forge 补丁加的方法，运行时不走 SRG 混淆，必须 remap = false。
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class ForgeBlockRenderDispatcherMixin {

    @Inject(
        method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void lantern$skipTrackedBlock(
            BlockState blockState,
            BlockPos blockPos,
            BlockAndTintGetter blockAndTintGetter,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            boolean checkSides,
            RandomSource randomSource,
            ModelData modelData,
            RenderType renderType,
            CallbackInfo ci
    ) {
        if (BlockRendererHandler.INSTANCE.isCarrierBlock(blockState)
                && BlockRendererHandler.INSTANCE.getVariation(blockPos) != null) {
            ci.cancel();
        }
    }
}
