package org.lantern.internal.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.lantern.model.handler.RendererHandler;
import org.lantern.model.wrapper.CustomModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private Component lantern$lastName;
    @Unique
    private CustomModelWrapper lantern$cachedWrapper;
    @Unique
    private int lantern$rendererVersion = -1;

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void lantern$replaceBoundingBox(CallbackInfoReturnable<AABB> callback) {
        CustomModelWrapper wrapper = lantern$getWrapper();
        if (wrapper == null) {
            return;
        }
        Entity entity = (Entity) (Object) this;
        double halfWidth = wrapper.getWidth() / 2;
        callback.setReturnValue(new AABB(
            entity.getX() - halfWidth,
            entity.getY(),
            entity.getZ() - halfWidth,
            entity.getX() + halfWidth,
            entity.getY() + wrapper.getHeight(),
            entity.getZ() + halfWidth
        ));
    }

    @Unique
    private CustomModelWrapper lantern$getWrapper() {
        Entity entity = (Entity) (Object) this;
        if (!entity.hasCustomName()) {
            return null;
        }
        Component name = entity.getCustomName();
        int version = RendererHandler.INSTANCE.getVersion();
        if (name == lantern$lastName && version == lantern$rendererVersion) {
            return lantern$cachedWrapper;
        }
        lantern$lastName = name;
        lantern$rendererVersion = version;
        lantern$cachedWrapper = RendererHandler.INSTANCE.getCustomModelWrapper(name.getString());
        return lantern$cachedWrapper;
    }
}
