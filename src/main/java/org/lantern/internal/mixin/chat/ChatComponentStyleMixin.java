package org.lantern.internal.mixin.chat;

import net.minecraft.client.gui.components.ChatComponent;
import org.lantern.internal.chat.ChatStyleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端下发的聊天窗口样式。render 只按方法名匹配：1.21.1 是 (GuiGraphics,IIIZ)V、
 * Forge 1.20.1 是 (GuiGraphics,III)V，两者的 translate / fill / drawString 序号一致，
 * Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1 共用本类；NeoForge 1.21.10 有自己的版本。
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentStyleMixin {
    @Shadow
    public abstract double getScale();

    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void lantern$overrideWidth(CallbackInfoReturnable<Integer> cir) {
        int width = ChatStyleHandler.widthOverride();
        if (width > 0) {
            cir.setReturnValue(width);
        }
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void lantern$overrideHeight(CallbackInfoReturnable<Integer> cir) {
        int height = ChatStyleHandler.heightOverride();
        if (height > 0) {
            cir.setReturnValue(height);
        }
    }

    @Inject(method = "getScale()D", at = @At("HEAD"), cancellable = true)
    private void lantern$overrideScale(CallbackInfoReturnable<Double> cir) {
        double scale = ChatStyleHandler.scaleOverride();
        if (scale > 0) {
            cir.setReturnValue(scale);
        }
    }

    // 原版先 scale 再 translate(4, 0, 0)，之后的行、队列提示、滚动条都画在这层位姿里，偏移要折算到缩放后的坐标
    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0),
        index = 0
    )
    private float lantern$offsetX(float x) {
        double scale = getScale();
        return scale > 0 ? x + (float) (ChatStyleHandler.offsetX() / scale) : x;
    }

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0),
        index = 1
    )
    private float lantern$offsetY(float y) {
        double scale = getScale();
        return scale > 0 ? y + (float) (ChatStyleHandler.offsetY() / scale) : y;
    }

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0),
        index = 4
    )
    private int lantern$lineBackground(int color) {
        return ChatStyleHandler.lineBackground(color);
    }

    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 2),
        index = 4
    )
    private int lantern$queueBackground(int color) {
        return ChatStyleHandler.lineBackground(color);
    }

    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I",
            ordinal = 0
        ),
        index = 4
    )
    private int lantern$textColor(int color) {
        return ChatStyleHandler.textColor(color);
    }

    // 绘制平移了，鼠标命中也要反向平移：悬停、点击链接、消息标签都经这两个方法换算
    @ModifyVariable(method = "screenToChatX(D)D", at = @At("HEAD"), argsOnly = true)
    private double lantern$offsetMouseX(double x) {
        return x - ChatStyleHandler.offsetX();
    }

    @ModifyVariable(method = "screenToChatY(D)D", at = @At("HEAD"), argsOnly = true)
    private double lantern$offsetMouseY(double y) {
        return y - ChatStyleHandler.offsetY();
    }

    // 队列提示的点击判定是内联算的，不经 screenToChat*
    @ModifyVariable(method = "handleChatQueueClicked(DD)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double lantern$offsetQueueMouseX(double x) {
        return x - ChatStyleHandler.offsetX();
    }

    @ModifyVariable(method = "handleChatQueueClicked(DD)Z", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double lantern$offsetQueueMouseY(double y) {
        return y - ChatStyleHandler.offsetY();
    }
}
