package org.lantern.internal.mixin.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lantern.model.handler.BlockRendererHandler;
import org.lantern.model.wrapper.BlockModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BarrelBlock.class)
public abstract class BarrelBlock1201Mixin extends BaseEntityBlock {

    protected BarrelBlock1201Mixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void lantern$cancelUse(BlockState state, Level level, BlockPos pos,
                                   Player player, InteractionHand hand, BlockHitResult hitResult,
                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (BlockRendererHandler.INSTANCE.isTrackedPosition(pos)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!BlockRendererHandler.INSTANCE.isTrackedPosition(pos)) {
            return super.getDestroyProgress(state, player, level, pos);
        }

        BlockModelWrapper wrapper = BlockRendererHandler.INSTANCE.getWrapperByPos(pos);
        if (wrapper == null) return super.getDestroyProgress(state, player, level, pos);

        float hardness = wrapper.getHardness();
        if (hardness < 0) return 0.0f;

        String preferredTool = wrapper.getPreferredTool();
        boolean correctTool;
        float speed;

        if (preferredTool == null || preferredTool.isEmpty()) {
            speed = player.getDestroySpeed(state);
            correctTool = true;
        } else {
            BlockState proxyState = lantern$getProxyState(preferredTool);
            speed = player.getDestroySpeed(proxyState);
            correctTool = speed > 1.0f;
        }

        int divisor = correctTool ? 30 : 100;
        return speed / hardness / (float) divisor;
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

    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void lantern$customRenderShape(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
        if (state.getValue(BlockStateProperties.OPEN)) {
            cir.setReturnValue(RenderShape.ENTITYBLOCK_ANIMATED);
        }
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return Shapes.empty();
        return super.getOcclusionShape(state, level, pos);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return 1.0f;
        return super.getShadeBrightness(state, level, pos);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return true;
        return super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return 0;
        return super.getLightBlock(state, level, pos);
    }
}
