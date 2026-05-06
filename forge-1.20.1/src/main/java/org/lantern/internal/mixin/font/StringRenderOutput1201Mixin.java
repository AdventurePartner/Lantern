package org.lantern.internal.mixin.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSink;
import org.joml.Matrix4f;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.internal.handler.TextureHandler;
import org.lantern.internal.wrapper.key.CharacterWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class StringRenderOutput1201Mixin implements FormattedCharSink {
    @Shadow
    float x;
    @Shadow
    float y;
    @Final
    @Shadow
    private float r, g, b, a;
    @Final
    @Shadow
    private Matrix4f pose;
    @Final
    @Shadow
    private MultiBufferSource bufferSource;
    @Final
    @Shadow
    private Font.DisplayMode mode;
    @Final
    @Shadow
    private int packedLightCoords;

    @Inject(method = "accept", cancellable = true, at = @At("HEAD"))
    private void lantern$accept(int index, Style style, int codePoint, CallbackInfoReturnable<Boolean> cir) {
        char character = (char) codePoint;
        CharacterWrapper wrapper = ResourceHandler.INSTANCE.getCharacterWrapper(character);

        if (wrapper == null) {
            return;
        }

        lantern$renderIcon(wrapper);
        x += wrapper.getWide();
        cir.setReturnValue(true);
    }

    @Unique
    private void lantern$renderIcon(CharacterWrapper wrapper) {
        ResourceLocation rs = TextureHandler.INSTANCE.getTexture(wrapper.getResource());
        RenderType renderType = lantern$selectRenderType(rs);
        VertexConsumer consumer = this.bufferSource.getBuffer(renderType);

        float width = wrapper.getWidth();
        float height = wrapper.getHeight();

        consumer.vertex(this.pose, x, y + height, 0).color(r, g, b, a).uv(0, 1).uv2(packedLightCoords).endVertex();
        consumer.vertex(this.pose, x + width, y + height, 0).color(r, g, b, a).uv(1, 1).uv2(packedLightCoords).endVertex();
        consumer.vertex(this.pose, x + width, y, 0).color(r, g, b, a).uv(1, 0).uv2(packedLightCoords).endVertex();
        consumer.vertex(this.pose, x, y, 0).color(r, g, b, a).uv(0, 0).uv2(packedLightCoords).endVertex();
    }

    @Unique
    private RenderType lantern$selectRenderType(ResourceLocation texture) {
        return switch (this.mode) {
            case SEE_THROUGH -> RenderType.textSeeThrough(texture);
            case POLYGON_OFFSET -> RenderType.textPolygonOffset(texture);
            default -> RenderType.text(texture);
        };
    }
}
