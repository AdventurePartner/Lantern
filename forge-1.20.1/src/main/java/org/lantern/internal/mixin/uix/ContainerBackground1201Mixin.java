package org.lantern.internal.mixin.uix;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lantern.uix.renderer.OverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 容器背景层（1.20.1 变体）：把 index < 0 的 overlay 插到物品/槽位之下渲染。
 * 1.20.1 的 renderBg 调用位于 render() 中段（其后还有 Forge 事件与槽位渲染），
 * 不能用 cancellable 注入——取消会连物品一起跳过，改用 Redirect 条件放行原版背景。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerBackground1201Mixin {

    @Shadow
    protected abstract void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY);

    @Redirect(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
        )
    )
    private void lantern$containerBgLayer(
        AbstractContainerScreen<?> self, GuiGraphics graphics, float partialTick, int mouseX, int mouseY
    ) {
        if (!OverlayRenderer.INSTANCE.shouldCancelVanillaBg(self)) {
            this.renderBg(graphics, partialTick, mouseX, mouseY);
        }
        OverlayRenderer.INSTANCE.renderContainerBackground(self, graphics, mouseX, mouseY, partialTick);
    }
}
