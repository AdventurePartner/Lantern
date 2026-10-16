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
        // 与其他三端一致：offset-y 叠加在原版名牌高度上，而不是替换它
        return offset + wrapper.getNameTagOffsetY();
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
