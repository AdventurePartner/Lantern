package org.lantern.internal.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.lantern.model.handler.RendererHandler;
import org.lantern.model.renderer.GenericGeoRenderer;
import org.lantern.model.wrapper.CustomModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "getRenderer", at=@At("HEAD"), cancellable = true)
    public <T extends Entity> void lantern$getRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T>> cir) {
        if (!entity.hasCustomName()) {
            return;
        }
        String customName = entity.getCustomName().getString();
        CustomModelWrapper wrapper = RendererHandler.INSTANCE.getCustomModelWrapper(customName);
        if (wrapper == null) {
            return;
        }
        GenericGeoRenderer<?> renderer = RendererHandler.INSTANCE.getRenderer(entity.getType(), customName);
        if (renderer == null) {
            return;
        }
        cir.setReturnValue(renderer);
    }
}
