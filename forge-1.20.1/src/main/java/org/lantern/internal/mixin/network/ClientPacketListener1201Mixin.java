package org.lantern.internal.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.lantern.platform.ForgePacketNetwork;
import org.lantern.platform.IdentifierBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListener1201Mixin {
    private static final ResourceLocation LANTERN_MAIN_CHANNEL = IdentifierBridge.of("lantern", "main");

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void lantern$handleBukkitPluginMessage(
        ClientboundCustomPayloadPacket packet,
        CallbackInfo ci
    ) {
        if (!LANTERN_MAIN_CHANNEL.equals(packet.getIdentifier())) {
            return;
        }

        FriendlyByteBuf payload = packet.getData();
        byte[] bytes = new byte[payload.readableBytes()];
        payload.readBytes(bytes);

        // Forge 1.20.1 calls NetworkHooks before vanilla's ensureRunningOnSameThread in this method,
        // so schedule the parser on the client executor before cancelling the unknown-channel path.
        Minecraft.getInstance().execute(() -> ForgePacketNetwork.INSTANCE.handleMainPayload(bytes));
        ci.cancel();
    }
}
