package org.lantern.internal.mixin.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.lantern.model.handler.BlockRendererHandler;
import org.lantern.model.wrapper.BlockModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BarrelBlock.class)
public abstract class BarrelBlockMixin extends BaseEntityBlock {
    protected BarrelBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void lantern$cancelUse(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> callback
    ) {
        if (level.isClientSide() && BlockRendererHandler.INSTANCE.isTrackedPosition(pos)) {
            callback.setReturnValue(InteractionResult.PASS);
        }
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!(level instanceof Level clientLevel) || !clientLevel.isClientSide()) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        BlockModelWrapper wrapper = BlockRendererHandler.INSTANCE.getWrapperByPos(pos);
        if (wrapper == null) {
            return super.getDestroyProgress(state, player, level, pos);
        }

        float hardness = wrapper.getHardness();
        if (hardness < 0) {
            return 0.0f;
        }

        String preferredTool = wrapper.getPreferredTool();
        BlockState probeState = preferredTool == null || preferredTool.isEmpty()
            ? state
            : lantern$getProxyState(preferredTool);
        float speed = player.getDestroySpeed(probeState, pos);
        boolean correctTool = preferredTool == null || preferredTool.isEmpty() || speed > 1.0f;
        return speed / hardness / (correctTool ? 30.0f : 100.0f);
    }

    @Unique
    private static BlockState lantern$getProxyState(String toolType) {
        return switch (toolType) {
            case "pickaxe" -> Blocks.STONE.defaultBlockState();
            case "axe" -> Blocks.OAK_LOG.defaultBlockState();
            case "shovel" -> Blocks.DIRT.defaultBlockState();
            default -> Blocks.BARREL.defaultBlockState();
        };
    }
}
