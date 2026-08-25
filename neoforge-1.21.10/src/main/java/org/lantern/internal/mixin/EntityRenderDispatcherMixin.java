package org.lantern.internal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.lantern.costume.renderstate.CostumeRenderData;
import org.lantern.model.handler.RendererHandler;
import org.lantern.model.renderer.GenericGeoRenderer;
import org.lantern.model.renderstate.LanternDataTickets;
import org.lantern.model.renderstate.ReplacedRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.renderer.base.GeoRenderState;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("HEAD")
    )
    private void lantern$captureCameraState(
        EntityRenderState renderState,
        CameraRenderState cameraState,
        double x,
        double y,
        double z,
        PoseStack poseStack,
        SubmitNodeCollector submitNodes,
        CallbackInfo callback
    ) {
        if (renderState instanceof AvatarRenderState) {
            renderState.setRenderData(CostumeRenderData.CAMERA_STATE, cameraState);
        }
    }

    @Inject(
        method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void lantern$getRendererByEntity(
        Entity entity,
        CallbackInfoReturnable<EntityRenderer<?, ?>> callback
    ) {
        if (!entity.hasCustomName()) {
            return;
        }
        GenericGeoRenderer<?> renderer = RendererHandler.INSTANCE.getRenderer(
            entity.getType(),
            entity.getCustomName().getString(),
            entity.getUUID()
        );
        if (renderer != null) {
            callback.setReturnValue(renderer);
        }
    }

    @Inject(
        method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void lantern$getRendererByState(
        EntityRenderState renderState,
        CallbackInfoReturnable<EntityRenderer<?, ?>> callback
    ) {
        if (!(renderState instanceof GeoRenderState geoState) ||
            !geoState.hasGeckolibData(LanternDataTickets.REPLACED_ENTITY)) {
            return;
        }
        ReplacedRenderData data = geoState.getGeckolibData(LanternDataTickets.REPLACED_ENTITY);
        GenericGeoRenderer<?> renderer = RendererHandler.INSTANCE.getRenderer(
            renderState.entityType,
            data.getRendererKey(),
            data.getEntityUuid()
        );
        if (renderer != null) {
            callback.setReturnValue(renderer);
        }
    }
}
