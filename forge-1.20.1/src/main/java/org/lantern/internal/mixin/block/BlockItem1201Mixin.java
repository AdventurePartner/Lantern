package org.lantern.internal.mixin.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lantern.model.handler.BlockRendererHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItem1201Mixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void lantern$onBlockPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;

        Level level = context.getLevel();
        if (!level.isClientSide) return;

        Integer customModelData = lantern$getCustomModelData(context.getItemInHand());
        if (customModelData == null || customModelData <= 0) return;

        Integer variation = BlockRendererHandler.INSTANCE.getVariationByCmd(customModelData);
        if (variation == null) return;

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!BlockRendererHandler.INSTANCE.isCarrierBlock(state)) return;

        BlockRendererHandler.INSTANCE.addBlockPosition(pos, variation);
        BlockState markerState = state.setValue(BlockStateProperties.OPEN, true);
        level.setBlock(pos, markerState, 0);
        BlockRendererHandler.INSTANCE.markSectionDirtyAt(pos);
    }

    private Integer lantern$getCustomModelData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("CustomModelData", Tag.TAG_INT)) return null;
        return tag.getInt("CustomModelData");
    }
}
