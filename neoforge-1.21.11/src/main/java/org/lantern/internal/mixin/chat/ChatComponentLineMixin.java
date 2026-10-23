package org.lantern.internal.mixin.chat;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lantern.internal.chat.ChatLineTextScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 聊天行文字色（1.21.11）：逐行绘制在 render 里的匿名 LineConsumer（ChatComponent$1）中，
 * 只包住这里的 handleMessage——队列提示也走 handleMessage 但不在此类里，保持原版颜色。
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class ChatComponentLineMixin {
    @Redirect(
        method = "accept(Lnet/minecraft/client/GuiMessage$Line;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"
        )
    )
    private boolean lantern$styledLine(ChatComponent.ChatGraphicsAccess access, int y, float opacity, FormattedCharSequence text) {
        ChatLineTextScope.enter();
        try {
            return access.handleMessage(y, opacity, text);
        } finally {
            ChatLineTextScope.exit();
        }
    }
}
