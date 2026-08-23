package org.lantern.platform;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lantern.core.protocol.LanternProtocol;
import org.lantern.core.protocol.MainPayloadDecoder;
import org.lantern.core.protocol.MainS2CPacket;
import org.lantern.internal.network.NetworkParser;
import org.lantern.internal.util.JsonUtils;

public final class NeoForgePacketNetwork {
    private static final MainPayloadDecoder DECODER = new MainPayloadDecoder();

    private NeoForgePacketNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(
            RegisterPayloadHandlersEvent.class,
            (Consumer<RegisterPayloadHandlersEvent>) NeoForgePacketNetwork::registerPayloads
        );
        modBus.addListener(
            RegisterClientPayloadHandlersEvent.class,
            (Consumer<RegisterClientPayloadHandlersEvent>) NeoForgePacketNetwork::registerClientHandlers
        );
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .optional()
            .playBidirectional(
                LanternMainPayload.TYPE,
                LanternMainPayload.STREAM_CODEC,
                (payload, context) -> {
                }
            );
    }

    private static void registerClientHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(LanternMainPayload.TYPE, NeoForgePacketNetwork::handleClientPayload);
    }

    private static void handleClientPayload(LanternMainPayload payload, IPayloadContext context) {
        MainS2CPacket packet = DECODER.decode(payload.data());
        if (packet != null) {
            NetworkParser.INSTANCE.parse(
                packet.getPacketId(),
                JsonUtils.INSTANCE.fromString(packet.getJson())
            );
        }
    }

    public static boolean sendKeyboardPacket(String key, boolean press, boolean inGui) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null || !listener.hasChannel(LanternMainPayload.TYPE)) {
            return false;
        }

        byte[] data = LanternProtocol.INSTANCE.encodeKeyboardC2S(key, press, inGui);
        ClientPacketDistributor.sendToServer(new LanternMainPayload(data));
        return true;
    }

    public static void clear() {
        DECODER.clear();
    }
}
