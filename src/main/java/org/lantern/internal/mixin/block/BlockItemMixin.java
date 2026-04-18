package org.lantern.internal.mixin.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lantern.model.handler.BlockRendererHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端放置预测：木桶方块携带 CustomModelData 且对应已知自定义方块时，
 * 立即注册位置映射并标记 section 脏以抑制 vanilla 模型。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void lantern$onBlockPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;

        Level level = context.getLevel();
        if (!level.isClientSide) return;

        ItemStack stack = context.getItemInHand();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || cmd.value() <= 0) return;

        Integer variation = BlockRendererHandler.INSTANCE.getVariationByCmd(cmd.value());
        if (variation == null) return;

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!BlockRendererHandler.INSTANCE.isCarrierBlock(state)) return;

        // 注册位置映射
        BlockRendererHandler.INSTANCE.addBlockPosition(pos, variation);

        // 客户端预测：立即设置 open=true 标记态，使 getRenderShape 返回 ENTITYBLOCK_ANIMATED
        BlockState markerState = state.setValue(BlockStateProperties.OPEN, true);
        level.setBlock(pos, markerState, 0);

        // 标记 section 脏以重编译
        BlockRendererHandler.INSTANCE.markSectionDirtyAt(pos);
    }
}
