package org.lantern.internal.mixin.chat;

import net.minecraft.client.gui.screens.ChatScreen;
import org.lantern.internal.chat.ChatStyleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 频道按钮与点击在 NeoForge 走 ScreenEvent；只有输入框背景色必须改原版调用参数。 */
@Mixin(ChatScreen.class)
public class ChatScreenStyleMixin {
    // render 里唯一的 fill 就是输入框背景
    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0),
        index = 4
    )
    private int lantern$inputBackground(int color) {
        return ChatStyleHandler.inputBackground(color);
    }
}
