package org.lantern.internal.mixin.uix;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lantern.uix.renderer.OverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器背景层：把 index < 0 的 overlay 插到物品/槽位之下渲染。
 * 1.21.10 的 renderBg 调用位于 renderBackground 末尾（其后仅 return），
 * 因此 cancel-vanilla-bg 命中时可在调用点前安全取消。
 * 本模块不编译共享 src/main/java，故此文件为共享同名 Mixin 的本地副本。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerBackgroundRenderMixin {

    @Inject(
        method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"
        ),
        cancellable = true
    )
    private void lantern$replaceVanillaBg(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (OverlayRenderer.INSTANCE.shouldCancelVanillaBg(self)) {
            OverlayRenderer.INSTANCE.renderContainerBackground(self, graphics, mouseX, mouseY, delta);
            ci.cancel();
        }
    }

    @Inject(
        method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            shift = At.Shift.AFTER
        )
    )
    private void lantern$renderOverVanillaBg(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayRenderer.INSTANCE.renderContainerBackground(
            (AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY, delta
        );
    }
}
