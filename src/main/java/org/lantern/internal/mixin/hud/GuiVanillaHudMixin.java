package org.lantern.internal.mixin.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import org.lantern.uix.hud.VanillaHudElement;
import org.lantern.uix.hud.VanillaHudPoses;
import org.lantern.uix.hud.VanillaHudVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 原版 HUD 元素的隐藏与摆放（Fabric 与 Forge 1.21.1 共用原版 Gui，签名逐字一致）。
 * 隐藏由 hide-vanilla 决定（{@link VanillaHudVisibility}）；摆放由 HUD 里的 vanilla 节点决定：
 * HEAD 按元素本帧的原版参考点推入平移，RETURN 弹出。隐藏时在 HEAD 取消且不推入——
 * 取消插入的 return 不会触发 RETURN 注入，位姿栈保持配平。
 */
@Mixin(Gui.class)
public abstract class GuiVanillaHudMixin {

    @Inject(
        method = "renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.HOTBAR)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.HOTBAR, graphics.guiWidth() / 2 - 91, graphics.guiHeight() - 22);
    }

    @Inject(
        method = "renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("RETURN")
    )
    private void lantern$afterHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.HOTBAR);
    }

    // Forge 另有 (GuiGraphics;I)V 重载，原版单参版在 Forge 上只是转调，拦单参版两边都生效
    @Inject(
        method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeSelectedItemName(GuiGraphics graphics, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.SELECTED_ITEM_NAME)) {
            ci.cancel();
            return;
        }
        int y = graphics.guiHeight() - 59;
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null && !gameMode.canHurtPlayer()) {
            y += 14;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.SELECTED_ITEM_NAME, graphics.guiWidth() / 2, y);
    }

    @Inject(
        method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("RETURN")
    )
    private void lantern$afterSelectedItemName(GuiGraphics graphics, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.SELECTED_ITEM_NAME);
    }

    @Inject(
        method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeHealth(
        GuiGraphics graphics, Player player, int x, int y, int height, int offsetHeartIndex,
        float maxHealth, int currentHealth, int displayHealth, int absorptionAmount, boolean renderHighlight,
        CallbackInfo ci
    ) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.HEALTH)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.HEALTH, x, y);
    }

    @Inject(
        method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
        at = @At("RETURN")
    )
    private void lantern$afterHealth(
        GuiGraphics graphics, Player player, int x, int y, int height, int offsetHeartIndex,
        float maxHealth, int currentHealth, int displayHealth, int absorptionAmount, boolean renderHighlight,
        CallbackInfo ci
    ) {
        VanillaHudPoses.end(graphics, VanillaHudElement.HEALTH);
    }

    // 护甲行画在 y - (heartRows - 1) * height - 10，即多行心之上
    @Inject(
        method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void lantern$beforeArmor(
        GuiGraphics graphics, Player player, int y, int heartRows, int height, int x, CallbackInfo ci
    ) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.ARMOR)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.ARMOR, x, y - (heartRows - 1) * height - 10);
    }

    @Inject(
        method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V",
        at = @At("RETURN")
    )
    private static void lantern$afterArmor(
        GuiGraphics graphics, Player player, int y, int heartRows, int height, int x, CallbackInfo ci
    ) {
        VanillaHudPoses.end(graphics, VanillaHudElement.ARMOR);
    }

    // x 是饥饿条右缘，图标从右往左排
    @Inject(
        method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeFood(GuiGraphics graphics, Player player, int y, int x, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.FOOD)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.FOOD, x, y);
    }

    @Inject(
        method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V",
        at = @At("RETURN")
    )
    private void lantern$afterFood(GuiGraphics graphics, Player player, int y, int x, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.FOOD);
    }

    // 气泡在 renderPlayerHealth 内联绘制；该方法里直接调用的 blitSprite 只有 AIR / AIR_BURSTING 两处，
    // 右缘固定为 guiWidth / 2 + 91，逐个气泡推入/弹出
    @Redirect(
        method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"
        )
    )
    private void lantern$drawAir(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.AIR)) return;
        VanillaHudPoses.begin(graphics, VanillaHudElement.AIR, graphics.guiWidth() / 2 + 91, y);
        graphics.blitSprite(sprite, x, y, width, height);
        VanillaHudPoses.end(graphics, VanillaHudElement.AIR);
    }

    @Inject(
        method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeVehicleHealth(GuiGraphics graphics, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.VEHICLE_HEALTH)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.VEHICLE_HEALTH, graphics.guiWidth() / 2 + 91, graphics.guiHeight() - 39);
    }

    @Inject(
        method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("RETURN")
    )
    private void lantern$afterVehicleHealth(GuiGraphics graphics, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.VEHICLE_HEALTH);
    }

    @Inject(
        method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeExperienceBar(GuiGraphics graphics, int x, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_BAR)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.EXPERIENCE_BAR, x, graphics.guiHeight() - 29);
    }

    @Inject(
        method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At("RETURN")
    )
    private void lantern$afterExperienceBar(GuiGraphics graphics, int x, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.EXPERIENCE_BAR);
    }

    @Inject(
        method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeExperienceLevel(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_LEVEL)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.EXPERIENCE_LEVEL, graphics.guiWidth() / 2, graphics.guiHeight() - 35);
    }

    @Inject(
        method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("RETURN")
    )
    private void lantern$afterExperienceLevel(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.EXPERIENCE_LEVEL);
    }

    @Inject(
        method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeJumpBar(PlayerRideableJumping rideable, GuiGraphics graphics, int x, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.JUMP_BAR)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.JUMP_BAR, x, graphics.guiHeight() - 29);
    }

    @Inject(
        method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At("RETURN")
    )
    private void lantern$afterJumpBar(PlayerRideableJumping rideable, GuiGraphics graphics, int x, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.JUMP_BAR);
    }

    @Inject(
        method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.CROSSHAIR)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(
            graphics, VanillaHudElement.CROSSHAIR,
            (graphics.guiWidth() - 15) / 2, (graphics.guiHeight() - 15) / 2
        );
    }

    @Inject(
        method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("RETURN")
    )
    private void lantern$afterCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.CROSSHAIR);
    }

    // 效果图标从屏幕右缘向左排，参考点为右上角
    @Inject(
        method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void lantern$beforeEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EFFECTS)) {
            ci.cancel();
            return;
        }
        VanillaHudPoses.begin(graphics, VanillaHudElement.EFFECTS, graphics.guiWidth(), 0);
    }

    @Inject(
        method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("RETURN")
    )
    private void lantern$afterEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudPoses.end(graphics, VanillaHudElement.EFFECTS);
    }
}
