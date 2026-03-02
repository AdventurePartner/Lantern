package org.lantern.internal.mixin.renderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lantern.model.handler.RendererHandler;
import org.lantern.model.wrapper.CustomModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float lantern$overrideNameTagOffset(
        float offset,
        Entity entity,
        Component component,
        PoseStack poseStack,
        MultiBufferSource multiBufferSource,
        int packedLight
    ) {
        if (!entity.hasCustomName()) {
            return offset;
        }
        CustomModelWrapper wrapper = RendererHandler.INSTANCE.getCustomModelWrapper(entity.getCustomName().getString());
        if (wrapper == null) {
            return offset;
        }
        float configuredOffset = wrapper.getNameTagOffsetY();
        return configuredOffset == 0.0F ? offset : configuredOffset;
    }
}
