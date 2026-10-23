package org.lantern.neoforge;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.lantern.costume.renderstate.CostumeRenderData;
import org.lantern.costume.renderer.CostumeRenderLayer;

public final class CostumeRenderRegistration {
    private CostumeRenderRegistration() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.<Avatar, AvatarRenderState>registerEntityModifier(
            (Class) AvatarRenderer.class,
            (avatar, renderState) ->
                renderState.setRenderData(CostumeRenderData.PLAYER_UUID, avatar.getUUID())
        );
    }

    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType modelType : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(modelType);
            if (renderer != null) {
                renderer.addLayer(new CostumeRenderLayer(renderer));
            }
        }
    }
}
