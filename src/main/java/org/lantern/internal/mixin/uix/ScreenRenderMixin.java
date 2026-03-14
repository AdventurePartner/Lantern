package org.lantern.internal.mixin.uix;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lantern.uix.renderer.OverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenRenderMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void lantern$renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayRenderer.INSTANCE.tryRenderOverlay((Screen) (Object) this, graphics, mouseX, mouseY, delta);
    }
}
