package org.lantern.internal.mixin.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.lantern.internal.chat.ChatChannelNotificationRenderer;
import org.lantern.uix.renderer.HudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiHudRenderMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void lantern$renderHudAtHead(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudRenderer.INSTANCE.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lantern$renderChatChannelNotificationAtTail(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ChatChannelNotificationRenderer.INSTANCE.render(graphics);
    }
}
