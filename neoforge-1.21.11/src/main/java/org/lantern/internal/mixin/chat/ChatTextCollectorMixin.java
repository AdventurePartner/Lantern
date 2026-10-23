package org.lantern.internal.mixin.chat;

import net.minecraft.util.ARGB;
import org.lantern.internal.chat.ChatLineTextScope;
import org.lantern.internal.chat.ChatStyleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 聊天行文字底色（1.21.11）：原版在这里用 ARGB.white(opacity) 生成文字底色，
 * 只在 ChatLineTextScope 打开时（正在画聊天行）换成配置色，透明度照旧承载淡出。
 */
@Mixin(targets = "net.minecraft.client.gui.GuiGraphics$RenderingTextCollector")
public abstract class ChatTextCollectorMixin {
    @Redirect(
        method = "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I")
    )
    private int lantern$chatLineColor(float opacity) {
        int color = ARGB.white(opacity);
        return ChatLineTextScope.active() ? ChatStyleHandler.textColor(color) : color;
    }
}
