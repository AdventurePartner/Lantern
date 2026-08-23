package org.lantern.internal.mixin.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.network.GeckoLibNetworkingNeoForge;

@Mixin(value = GeckoLibNetworkingNeoForge.class, remap = false)
public abstract class GeckoLibNetworkingNeoForgeMixin {
    @Redirect(
        method = "lambda$init$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent;registrar(Ljava/lang/String;)Lnet/neoforged/neoforge/network/registration/PayloadRegistrar;"
        )
    )
    private static PayloadRegistrar lantern$makePayloadsOptional(
        RegisterPayloadHandlersEvent event,
        String version
    ) {
        // GeckoLib 5.3-alpha-3 otherwise requires a NeoForge server during payload negotiation.
        return event.registrar(version).optional();
    }
}
