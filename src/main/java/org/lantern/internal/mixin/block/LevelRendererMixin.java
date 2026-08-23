package org.lantern.internal.mixin.block;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.lantern.platform.IdentifierBridge;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lantern.model.handler.BlockRendererHandler;
import org.lantern.model.wrapper.BlockModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截方块破坏事件 (levelEvent 2001)，
 * 对自定义方块替换音效并使用载体方块的默认纹理生成粒子。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void lantern$levelEvent(int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type != 2001) return;

        BlockState state = Block.stateById(data);
        if (!BlockRendererHandler.INSTANCE.isTrackedPosition(pos)) return;

        BlockModelWrapper wrapper = BlockRendererHandler.INSTANCE.getWrapperByPos(pos);
        if (wrapper == null) return;

        // 播放自定义破坏音效（在方块位置）
        String breakSound = wrapper.getBreakSound();
        if (breakSound == null || breakSound.isEmpty()) return;
        SoundEvent sound = SoundEvent.createVariableRangeEvent(IdentifierBridge.parse(breakSound));
        level.playLocalSound(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, SoundSource.BLOCKS, 1.0f, 1.0f, false
        );

        // 粒子由 ParticleEngineMixin 拦截处理（使用正确的自定义纹理）
        if (level != null) {
            level.addDestroyBlockEffect(pos, state);
        }

        ci.cancel();
    }
}
