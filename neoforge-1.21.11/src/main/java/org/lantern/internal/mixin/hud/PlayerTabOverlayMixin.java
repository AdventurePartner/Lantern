package org.lantern.internal.mixin.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.FormattedText;
import org.lantern.internal.handler.ResourceHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    /**
     * TAB 列表的名字列宽度通过 Font.width() 计算，但自定义图片字符的实际渲染宽度
     * (CharacterWrapper.wide) 可能大于标准字形宽度，导致名字溢出槽位。
     * 重定向宽度计算，把图片字符的额外宽度加进去。
     * render 中 width(FormattedText) 的第 0 次调用即名字列（getNameForDisplay）。
     */
    @Redirect(
        method = "render",
        at = @At(value = "INVOKE",
                  target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I",
                  ordinal = 0)
    )
    private int lantern$adjustNameWidth(Font font, FormattedText text) {
        return ResourceHandler.INSTANCE.getAdjustedWidth(font, text);
    }
}
