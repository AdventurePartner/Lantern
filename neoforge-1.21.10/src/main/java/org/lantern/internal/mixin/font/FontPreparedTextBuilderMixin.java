package org.lantern.internal.mixin.font;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import org.lantern.internal.handler.ResourceHandler;
import org.lantern.internal.font.CharacterIconSupport.Icon;
import org.lantern.internal.font.CharacterIconSupport.IconSink;
import org.lantern.internal.wrapper.key.CharacterWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.10 用 Font$PreparedTextBuilder 替代了 1.21.1 的 Font$StringRenderOutput。
 * 在 accept 阶段拦截图标字符：跳过原字形，记录图标 quad 供渲染阶段发射，
 * 并调用 markSize 让背景色/下划线等边界计算包含图标范围。
 */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontPreparedTextBuilderMixin implements IconSink {

    @Shadow
    float x;

    @Shadow
    float y;

    @Shadow
    @Final
    private int color;

    @Shadow
    @Final
    private boolean drawShadow;

    @Shadow
    private void markSize(float minX, float minY, float maxX, float maxY) {
        throw new AssertionError();
    }

    @Unique
    private List<Icon> lantern$icons;

    @Override
    public List<Icon> lantern$getIcons() {
        return this.lantern$icons == null ? List.of() : this.lantern$icons;
    }

    @Override
    public void lantern$clearIcons() {
        this.lantern$icons = null;
    }

    @Inject(
        method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void lantern$acceptIcon(int index, Style style, int codePoint, CallbackInfoReturnable<Boolean> cir) {
        CharacterWrapper wrapper = ResourceHandler.INSTANCE.getCharacterWrapper((char) codePoint);
        if (wrapper == null) {
            return;
        }

        int baseColor = lantern$effectiveColor(style);
        int shadowColor = lantern$shadowColor(style, baseColor);
        if (this.lantern$icons == null) {
            this.lantern$icons = new ArrayList<>();
        }
        float iconX = this.x + wrapper.getOffsetX();
        float iconY = this.y + wrapper.getOffsetY();
        this.lantern$icons.add(new Icon(
            iconX, iconY,
            wrapper.getWidth(), wrapper.getHeight(),
            wrapper.getResource(),
            baseColor, shadowColor
        ));
        this.markSize(iconX, iconY, iconX + wrapper.getWidth(), iconY + wrapper.getHeight());
        this.x += wrapper.getWide();
        cir.setReturnValue(true);
    }

    /** 复刻 PreparedTextBuilder.getTextColor：样式色 + 构造时的 alpha */
    @Unique
    private int lantern$effectiveColor(Style style) {
        TextColor styleColor = style.getColor();
        if (styleColor != null) {
            return ARGB.color(ARGB.alpha(this.color), styleColor.getValue());
        }
        return this.color;
    }

    /** 复刻 PreparedTextBuilder.getShadowColor */
    @Unique
    private int lantern$shadowColor(Style style, int baseColor) {
        Integer override = style.getShadowColor();
        if (override != null) {
            float alpha = ARGB.alphaFloat(baseColor);
            float overrideAlpha = ARGB.alphaFloat(override);
            return alpha != 1.0F ? ARGB.color(ARGB.as8BitChannel(alpha * overrideAlpha), override) : override;
        }
        return this.drawShadow ? ARGB.scaleRGB(baseColor, 0.25F) : 0;
    }
}
