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

    // ==================== 渲染属性 ====================
    // 服务端放置自定义方块时设 open=true 作为载体标记态。几何隐藏按位置做（TrackedBarrelModel），
    // 但遮挡、光照这几项在 1.21.2+ 只收 BlockState、还按状态缓存，只能按标记态判断——
    // 否则载体仍是完整不透光方块：邻面被剔除露出空洞、周围被压暗。与共享端 BarrelBlockMixin 对齐

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        if (state.getValue(BlockStateProperties.OPEN)) return Shapes.empty();
        return super.getOcclusionShape(state);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return 1.0f;
        return super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        if (state.getValue(BlockStateProperties.OPEN)) return true;
        return super.propagatesSkylightDown(state);
    }

    @Override
    protected int getLightBlock(BlockState state) {
        if (state.getValue(BlockStateProperties.OPEN)) return 0;
        return super.getLightBlock(state);
    }
}
