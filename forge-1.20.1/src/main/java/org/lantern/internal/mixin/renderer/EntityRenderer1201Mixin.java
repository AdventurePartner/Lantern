package org.lantern.internal.mixin.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.Entity;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.model.handler.RendererHandler;
import org.lantern.model.wrapper.CustomModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRenderer1201Mixin {

    @Redirect(
        method = "renderNameTag",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getNameTagOffsetY()F")
    )
    private float lantern$overrideNameTagOffset(Entity entity) {
        float offset = entity.getNameTagOffsetY();
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

    @Redirect(
        method = "renderNameTag",
        at = @At(value = "INVOKE",
                  target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I",
                  ordinal = 0)
    )
    private int lantern$adjustNameTagWidth(Font font, FormattedText text) {
        return ResourceHandler.INSTANCE.getAdjustedWidth(font, text);
    }
}
