package org.lantern.internal.mixin.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lantern.uix.hud.VanillaHudElement;
import org.lantern.uix.hud.VanillaHudVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 的经验条与等级数字画在同一个 renderExperienceBar 里（ForgeGui 的 EXPERIENCE_BAR
 * overlay 经 invokespecial 调用它），取消整个 overlay 会把两者一起隐藏。
 * 这里按调用拆开：blit 只有经验条底/进度两处，drawString 只有等级数字的描边与正文。
 */
@Mixin(Gui.class)
public abstract class ExperienceBar1201Mixin {

    @Redirect(
        method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"
        )
    )
    private void lantern$hideExperienceBar(
        GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height
    ) {
        if (!VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_BAR)) {
            graphics.blit(texture, x, y, u, v, width, height);
        }
    }

    @Redirect(
        method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"
        )
    )
    private int lantern$hideExperienceLevel(
        GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean dropShadow
    ) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_LEVEL)) return 0;
        return graphics.drawString(font, text, x, y, color, dropShadow);
    }
}
