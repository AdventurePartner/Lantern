package org.lantern.internal.mixin.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.lantern.internal.chat.ChatChannelNotificationRenderer;
import org.lantern.uix.renderer.HudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiHudRenderMixin {

    // LayeredDraw 每层抬高 200 且 GUI 开启深度测试；render 尾部位姿已复位到 0，
    // 必须抬到全部原版层之上，否则会被先画的高 z 像素遮挡
    @Unique
    private static final float LANTERN$TOP_LAYER_Z = 3000.0F;

    // 原版层：renderCameraOverlays 是 Gui 构造函数登记的第一个图层（暗角/南瓜头/传送门），
    // 其尾部即"相机覆盖层之上、准星与快捷栏之下"
    @Inject(
        method = "renderCameraOverlays(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL")
    )
    private void lantern$renderHudBase(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudRenderer.INSTANCE.renderBase(graphics);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lantern$renderHudTop(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, LANTERN$TOP_LAYER_Z);
        HudRenderer.INSTANCE.renderTop(graphics);
        ChatChannelNotificationRenderer.INSTANCE.render(graphics);
        graphics.pose().popPose();
    }
}
