package org.lantern.internal.mixin.chat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lantern.internal.chat.ChatChannelTabsRenderer;
import org.lantern.internal.chat.ChatStyleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            shift = At.Shift.AFTER
        )
    )
    private void lantern$renderChatChannelTabs(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatChannelTabsRenderer.INSTANCE.render(graphics, mouseX, mouseY);
    }

    // render 里唯一的 fill 就是输入框背景
    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0),
        index = 4
    )
    private int lantern$inputBackground(int color) {
        return ChatStyleHandler.inputBackground(color);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lantern$handleChatChannelTabClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && ChatChannelTabsRenderer.INSTANCE.handleClick((int) mouseX, (int) mouseY)) {
            cir.setReturnValue(true);
        }
    }
}
