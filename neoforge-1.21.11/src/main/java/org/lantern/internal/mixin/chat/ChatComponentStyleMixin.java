package org.lantern.internal.mixin.chat;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.ChatComponent;
import org.joml.Matrix3x2f;
import org.lantern.internal.chat.ChatStyleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端下发的聊天窗口样式（1.21.11 版）。
 * 1.21.11 的聊天绘制与点击捕获共用私有 render(ChatGraphicsAccess, ...)：位姿经 updatePose 设置一次，
 * 绘制实现与点击捕获实现都按这个位姿换算鼠标坐标，所以偏移只需挂在 updatePose 上，命中判定自动跟随。
 * 逐行背景画在 lambda$render$1 里，升级 NeoForge 时要用 javap 复核这个名字与其中的 fill；
 * 逐行文字色经 ChatComponent$1（ChatComponentLineMixin）与 RenderingTextCollector（ChatTextCollectorMixin）。
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

    // 原版位姿是先 scale 再 translate(4, 0)，之后的行、队列提示、滚动条都画在这层位姿里，偏移要折算到缩放后的坐标
    @ModifyArg(
        method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;updatePose(Ljava/util/function/Consumer;)V"
        ),
        index = 0
    )
    private Consumer<Matrix3x2f> lantern$offsetPose(Consumer<Matrix3x2f> vanilla) {
        int offsetX = ChatStyleHandler.offsetX();
        int offsetY = ChatStyleHandler.offsetY();
        double scale = getScale();
        if ((offsetX == 0 && offsetY == 0) || scale <= 0) {
            return vanilla;
        }
        float x = (float) (offsetX / scale);
        float y = (float) (offsetY / scale);
        return vanilla.andThen(pose -> pose.translate(x, y));
    }

    @ModifyArg(
        method = "lambda$render$1",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V",
            ordinal = 0
        ),
        index = 4
    )
    private static int lantern$lineBackground(int color) {
        return ChatStyleHandler.lineBackground(color);
    }

    @ModifyArg(
        method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V",
            ordinal = 0
        ),
        index = 4
    )
    private int lantern$queueBackground(int color) {
        return ChatStyleHandler.lineBackground(color);
    }
}
