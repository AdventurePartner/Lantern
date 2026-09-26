package org.lantern.internal.mixin.font;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lantern.internal.font.CharacterIconSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界空间文本（名牌等）走 Font.drawInBatch(MultiBufferSource) 路径。
 * prepareText 返回的 builder 内记录了图标（FontPreparedTextBuilderMixin），
 * 通过重定向捕获 builder 与渲染上下文，在 drawInBatch 尾部发射图标 quad。
 * 注意 Font$PreparedTextBuilder 是包私有类，这里只以公共的 PreparedText
 * 接口持有引用，需要时转型为 IconSink duck 接口。
 */
@Mixin(Font.class)
public abstract class FontMixin {

    @Unique
    private CharacterIconSupport.IconSink lantern$sink;

    @Unique
    private MultiBufferSource lantern$buffer;

    @Unique
    private Matrix4f lantern$pose;

    @Unique
    private Font.DisplayMode lantern$mode;

    @Unique
    private int lantern$packedLight;

    @Redirect(
        method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;"
        )
    )
    private Font.PreparedText lantern$captureString(Font font, String text, float x, float y, int color, boolean dropShadow, int backgroundColor) {
        Font.PreparedText prepared = font.prepareText(text, x, y, color, dropShadow, backgroundColor);
        if (prepared instanceof CharacterIconSupport.IconSink sink) {
            this.lantern$sink = sink;
        }
        return prepared;
    }

    @Redirect(
        method = {
            "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;"
        )
    )
    private Font.PreparedText lantern$captureSequence(
        Font font, FormattedCharSequence text, float x, float y, int color, boolean dropShadow, int backgroundColor
    ) {
        Font.PreparedText prepared = font.prepareText(text, x, y, color, dropShadow, backgroundColor);
        if (prepared instanceof CharacterIconSupport.IconSink sink) {
            this.lantern$sink = sink;
        }
        return prepared;
    }

    @Redirect(
        method = {
            "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font$GlyphVisitor;forMultiBufferSource(Lnet/minecraft/client/renderer/MultiBufferSource;Lorg/joml/Matrix4f;Lnet/minecraft/client/gui/Font$DisplayMode;I)Lnet/minecraft/client/gui/Font$GlyphVisitor;"
        )
    )
    private Font.GlyphVisitor lantern$captureContext(MultiBufferSource bufferSource, Matrix4f pose, Font.DisplayMode mode, int packedLight) {
        this.lantern$buffer = bufferSource;
        this.lantern$pose = pose;
        this.lantern$mode = mode;
        this.lantern$packedLight = packedLight;
        return Font.GlyphVisitor.forMultiBufferSource(bufferSource, pose, mode, packedLight);
    }

    @Inject(
        method = {
            "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        },
        at = @At("TAIL")
    )
    private void lantern$emitIcons(CallbackInfo callback) {
        CharacterIconSupport.IconSink sink = this.lantern$sink;
        this.lantern$sink = null;
        if (sink == null || this.lantern$buffer == null) {
            return;
        }
        if (!sink.lantern$getIcons().isEmpty()) {
            CharacterIconSupport.emitWorld(sink.lantern$getIcons(), this.lantern$buffer, this.lantern$pose, this.lantern$mode, this.lantern$packedLight);
        }
        sink.lantern$clearIcons();
        this.lantern$buffer = null;
        this.lantern$pose = null;
    }
}
