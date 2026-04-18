package org.lantern.internal.mixin;

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

    @Unique private net.minecraft.network.chat.Component lantern$lastNameComponent;
    @Unique private CustomModelWrapper lantern$cachedWrapper;

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void onGetBoundingBoxHead(CallbackInfoReturnable<AABB> cir) {
        CustomModelWrapper wrapper = this.lantern$getCustomWrapper();
        if (wrapper == null) return;

        AABB customBox = lantern$createCustomBoundingBox((Entity) (Object) this, wrapper);
        cir.setReturnValue(customBox);
    }

    @Unique
    private CustomModelWrapper lantern$getCustomWrapper() {
        Entity self = (Entity) (Object) this;

        if (!self.hasCustomName()) return null;

        net.minecraft.network.chat.Component currentName = self.getCustomName();
        if (currentName == lantern$lastNameComponent) return lantern$cachedWrapper;

        lantern$lastNameComponent = currentName;
        lantern$cachedWrapper = RendererHandler.INSTANCE.getCustomModelWrapper(currentName.getString());
        return lantern$cachedWrapper;
    }

    @Unique
    private AABB lantern$createCustomBoundingBox(Entity entity, CustomModelWrapper wrapper) {
        double width = wrapper.getWidth();
        double height = wrapper.getHeight();

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        return new AABB(
                x - width / 2, y,
                z - width / 2,
                x + width / 2, y + height,
                z + width / 2
        );
    }
}
