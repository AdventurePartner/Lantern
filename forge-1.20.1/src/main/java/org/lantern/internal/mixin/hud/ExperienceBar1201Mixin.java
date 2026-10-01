package org.lantern.internal.mixin.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lantern.uix.hud.VanillaHudElement;
import org.lantern.uix.hud.VanillaHudPoses;
import org.lantern.uix.hud.VanillaHudVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 的经验条与等级数字画在同一个 renderExperienceBar 里（ForgeGui 的 EXPERIENCE_BAR
 * overlay 经 invokespecial 调用它），按 overlay 取消或平移会把两者一起处理。
 * 这里按调用拆开：blit 只有经验条底/进度两处，drawString 只有等级数字的描边与正文；
 * 隐藏与摆放都在单次调用前后完成。
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
    private void lantern$drawExperienceBar(
        GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height
    ) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_BAR)) return;
        VanillaHudPoses.begin(graphics, VanillaHudElement.EXPERIENCE_BAR, x, y);
        graphics.blit(texture, x, y, u, v, width, height);
        VanillaHudPoses.end(graphics, VanillaHudElement.EXPERIENCE_BAR);
    }

    // 等级数字水平居中于屏幕中线，顶边 guiHeight - 35（四次 ±1 描边随正文一起平移）
    @Redirect(
        method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"
        )
    )
    private int lantern$drawExperienceLevel(
        GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean dropShadow
    ) {
        if (VanillaHudVisibility.isHidden(VanillaHudElement.EXPERIENCE_LEVEL)) return 0;
        VanillaHudPoses.begin(
            graphics, VanillaHudElement.EXPERIENCE_LEVEL, graphics.guiWidth() / 2, graphics.guiHeight() - 35
        );
        int result = graphics.drawString(font, text, x, y, color, dropShadow);
        VanillaHudPoses.end(graphics, VanillaHudElement.EXPERIENCE_LEVEL);
        return result;
    }
}
