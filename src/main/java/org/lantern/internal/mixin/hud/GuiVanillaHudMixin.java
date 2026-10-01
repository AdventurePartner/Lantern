package org.lantern.internal.mixin.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import org.lantern.uix.hud.VanillaHudElement;
import org.lantern.uix.hud.VanillaHudVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 原版 HUD 元素隐藏（Fabric 与 Forge 1.21.1 共用原版 Gui，签名逐字一致）。
 * 元素集合由服务端 HUD 配置 hide-vanilla 决定，见 {@link VanillaHudVisibility}。
 */
@Mixin(Gui.class)
public abstract class GuiVanillaHudMixin {

    @Inject(
        method = "renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.HOTBAR)) ci.cancel();
    }

    // Forge 另有 (GuiGraphics;I)V 重载，原版单参版在 Forge 上只是转调，拦单参版两边都生效
    @Inject(
        method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideSelectedItemName(GuiGraphics graphics, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.SELECTED_ITEM_NAME)) ci.cancel();
    }

    @Inject(
        method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideHealth(
        GuiGraphics graphics, Player player, int x, int y, int height, int offsetHeartIndex,
        float maxHealth, int currentHealth, int displayHealth, int absorptionAmount, boolean renderHighlight,
        CallbackInfo ci
    ) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.HEALTH)) ci.cancel();
    }

    @Inject(
        method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void lantern$hideArmor(
        GuiGraphics graphics, Player player, int y, int heartRows, int height, int x, CallbackInfo ci
    ) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.ARMOR)) ci.cancel();
    }

    @Inject(
        method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideFood(GuiGraphics graphics, Player player, int y, int x, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.FOOD)) ci.cancel();
    }

    // 气泡在 renderPlayerHealth 内联绘制；该方法里直接调用的 blitSprite 只有 AIR / AIR_BURSTING 两处
    @Redirect(
        method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"
        )
    )
    private void lantern$hideAir(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (!VanillaHudVisibility.isHidden(VanillaHudElement.AIR)) {
            graphics.blitSprite(sprite, x, y, width, height);
        }
    }

    @Inject(
        method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideVehicleHealth(GuiGraphics graphics, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.VEHICLE_HEALTH)) ci.cancel();
    }

    @Inject(
        method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideExperienceBar(GuiGraphics graphics, int x, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_BAR)) ci.cancel();
    }

    @Inject(
        method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideExperienceLevel(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_LEVEL)) ci.cancel();
    }

    @Inject(
        method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideJumpBar(PlayerRideableJumping rideable, GuiGraphics graphics, int x, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.JUMP_BAR)) ci.cancel();
    }

    @Inject(
        method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.CROSSHAIR)) ci.cancel();
    }

    @Inject(
        method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$hideEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EFFECTS)) ci.cancel();
    }
}
