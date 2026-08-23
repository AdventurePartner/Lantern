package org.lantern.internal.mixin.accessor;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.lantern.internal.chat.ChatComponentBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor extends ChatComponentBridge {
    @Override
    @Accessor("allMessages")
    List<GuiMessage> lantern$getAllMessages();

    @Override
    @Invoker("refreshTrimmedMessages")
    void lantern$refreshTrimmedMessages();
}
