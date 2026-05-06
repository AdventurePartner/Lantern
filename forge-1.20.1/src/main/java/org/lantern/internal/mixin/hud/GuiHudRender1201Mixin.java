package org.lantern.internal.mixin.hud;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.lantern.uix.renderer.HudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiHudRender1201Mixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void lantern$renderHudAtHead(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        HudRenderer.INSTANCE.render(graphics, partialTick);
    }
}
