package org.lantern.internal.mixin.font;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import org.lantern.internal.font.CharacterIconSupport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * 1.21.10 的 GUI 文本（聊天/HUD/界面）不再走 Font.drawInBatch，
 * 而是 GuiTextRenderState 延迟到 GuiRenderer.prepareText 里 visit。
 * 包装 forEachText 的消费者：原逻辑提交字形后，把同一文本记录的
 * 图标（FontPreparedTextBuilderMixin）作为 BlitRenderState 发射到当前层。
 * 此时 forEachText 已把 GuiRenderState.current 切到文本所属层节点，
 * 与字形同层，并沿用原文本的 scissor。
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow
    @Final
    private GuiRenderState renderState;

    @Redirect(
        method = "prepareText",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;forEachText(Ljava/util/function/Consumer;)V"
        )
    )
    private void lantern$forEachText(GuiRenderState state, Consumer<GuiTextRenderState> original) {
        state.forEachText(textState -> {
            original.accept(textState);
            CharacterIconSupport.emitGui(textState, this.renderState);
        });
    }
}
