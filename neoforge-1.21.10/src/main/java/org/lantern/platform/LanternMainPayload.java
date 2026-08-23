package org.lantern.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LanternMainPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<LanternMainPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("lantern", "main")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LanternMainPayload> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data()),
            buffer -> {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);
                return new LanternMainPayload(data);
            }
        );

    @Override
    public Type<LanternMainPayload> type() {
        return TYPE;
    }
}
