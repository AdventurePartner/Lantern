package org.lantern.internal.mixin.block;

import net.minecraft.core.BlockPos;
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

/**
 * 木桶载体 Mixin。
 * 标记态：open=true。服务端放置自定义方块时设 open=true，
 * 客户端通过 open 属性判断是否为自定义方块。
 * getRenderShape 返回 ENTITYBLOCK_ANIMATED 以抑制 vanilla/Sodium 模型渲染。
 */
@Mixin(BarrelBlock.class)
public abstract class BarrelBlockMixin extends BaseEntityBlock {

    protected BarrelBlockMixin(Properties properties) {
        super(properties);
    }

    // ==================== 抑制交互 ====================

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void lantern$cancelUse(BlockState state, Level level, BlockPos pos,
                                    Player player, BlockHitResult hitResult,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (BlockRendererHandler.INSTANCE.isTrackedPosition(pos)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }


    // ==================== 挖掘速度 ====================

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
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

    // ==================== 渲染形状 ====================

    /**
     * open=true 时返回 ENTITYBLOCK_ANIMATED，作为辅助抑制手段。
     * 主要抑制由 BarrelModelMixin 完成（替换 BakedModel）。
     */
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void lantern$customRenderShape(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
        if (state.getValue(BlockStateProperties.OPEN)) {
            cir.setReturnValue(RenderShape.ENTITYBLOCK_ANIMATED);
        }
    }

    // ==================== 渲染属性 ====================
    // Sodium 按 BlockState 缓存这些属性，必须用 state 而非 position 判断。
    // open=true 的行为等同于玻璃/台阶等非完整方块。

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return Shapes.empty();
        return super.getOcclusionShape(state, level, pos);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return 1.0f;
        return super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return true;
        return super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(BlockStateProperties.OPEN)) return 0;
        return super.getLightBlock(state, level, pos);
    }
}
