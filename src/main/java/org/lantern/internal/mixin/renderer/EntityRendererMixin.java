package org.lantern.internal.mixin.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.model.handler.RendererHandler;
import org.lantern.model.wrapper.CustomModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

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

    /**
     * 名牌居中偏移通过 Font.width() 计算，但自定义图片字符的实际渲染宽度
     * (CharacterWrapper.wide) 大于标准字形宽度，导致名牌整体向右偏移。
     * 重定向宽度计算，把图片字符的额外宽度加进去。
     */
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
