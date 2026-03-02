package org.lantern.internal.mixin.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
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
public abstract class StringRenderOutputMixin implements FormattedCharSink {
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

    @Inject(method = "accept", cancellable = true, at = @At("HEAD"))
    private void lantern$accept(int index, Style style, int codePoint, CallbackInfoReturnable<Boolean> cir) {
        char character = (char) codePoint;
        CharacterWrapper wrapper = ResourceHandler.INSTANCE.getCharacterWrapper(character);

        if (wrapper == null) {
            return;
        }

        internal$renderIcon(wrapper);
        x += wrapper.getWide();
        cir.setReturnValue(true);
    }

    @Unique
    private void internal$renderIcon(CharacterWrapper wrapper) {
        ResourceLocation rs = TextureHandler.INSTANCE.getTexture(wrapper.getResource());
        RenderSystem.setShaderTexture(0, rs);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(r, g, b, a);

        float width = wrapper.getWidth();
        float height = wrapper.getHeight();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(this.pose, x, y + height, 0).setUv(0, 1);
        buffer.addVertex(this.pose, x + width, y + height, 0).setUv(1, 1);
        buffer.addVertex(this.pose, x + width, y, 0).setUv(1, 0);
        buffer.addVertex(this.pose, x, y, 0).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
