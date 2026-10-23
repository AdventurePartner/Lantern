package org.lantern.internal.mixin.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lantern.model.handler.BlockRendererHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端放置预测：木桶方块携带 CustomModelData 且对应已知自定义方块时，
 * 立即注册位置映射并设 open=true 标记态，不等服务端的 packet 12。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    /** place 返回前会 consume 手里的物品，最后一个用完时 RETURN 处已读不到组件，在 HEAD 先取 */
    @Unique
    private static Integer lantern$placingCmd;

    @Inject(method = "place", at = @At("HEAD"))
    private void lantern$capturePlacingCmd(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        lantern$placingCmd = null;
        if (!context.getLevel().isClientSide()) return;
        CustomModelData cmd = context.getItemInHand().get(DataComponents.CUSTOM_MODEL_DATA);
        Float value = cmd == null ? null : cmd.getFloat(0);
        if (value != null && value > 0 && value == Math.floor(value)) {
            lantern$placingCmd = value.intValue();
        }
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void lantern$onBlockPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Integer cmd = lantern$placingCmd;
        lantern$placingCmd = null;
        if (cmd == null || !cir.getReturnValue().consumesAction()) return;

        Level level = context.getLevel();
        if (!level.isClientSide()) return;

        Integer variation = BlockRendererHandler.INSTANCE.getVariationByCmd(cmd);
        if (variation == null) return;

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!BlockRendererHandler.INSTANCE.isCarrierBlock(state)) return;

        BlockRendererHandler.INSTANCE.addBlockPosition(pos, variation);
        // 标记态先在客户端落下，遮挡与光照覆盖（BarrelBlockMixin）立即生效
        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 0);
        BlockRendererHandler.INSTANCE.markSectionDirtyAt(pos);
    }
}
