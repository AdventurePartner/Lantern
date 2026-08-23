package org.lantern.internal.mixin.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jetbrains.annotations.Nullable;
import org.lantern.internal.chat.ChatChannelHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponent1201Mixin {
    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;logChatMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/GuiMessageTag;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void lantern$captureChannelMessage(
        Component component,
        @Nullable MessageSignature signature,
        @Nullable GuiMessageTag tag,
        CallbackInfo ci
    ) {
        int addedTime = Minecraft.getInstance().gui.getGuiTicks();
        GuiMessage message = new GuiMessage(addedTime, component, signature, tag);
        if (ChatChannelHandler.INSTANCE.capture(message)) {
            ci.cancel();
        }
    }
}
