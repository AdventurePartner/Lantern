package org.lantern.internal.mixin.model;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 对 barrel open=true 状态返回 missing model（无可见面），
 * 使 vanilla 和 Sodium 的 section compiler 都不渲染桶模型。
 * 这是唯一绕过 Sodium 模型缓存的可靠方式。
 */
@Mixin(BlockModelShaper.class)
public abstract class BarrelModelMixin {

    @Inject(method = "getBlockModel", at = @At("HEAD"), cancellable = true)
    private void lantern$hideOpenBarrel(BlockState blockState, CallbackInfoReturnable<BakedModel> cir) {
        if (blockState.is(Blocks.BARREL) && blockState.getValue(BlockStateProperties.OPEN)) {
            // 返回 missing model — 一个无面的空模型，什么都不渲染
            cir.setReturnValue(((BlockModelShaper) (Object) this).getModelManager().getMissingModel());
        }
    }
}
