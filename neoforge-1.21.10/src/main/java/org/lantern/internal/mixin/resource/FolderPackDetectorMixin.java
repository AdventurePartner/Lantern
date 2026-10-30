package org.lantern.internal.mixin.resource;

import net.minecraft.server.packs.repository.Pack;
import org.lantern.internal.handler.EncryptedZips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/**
 * 加密 zip 不交给原版：原版打不开加密条目，每次扫描资源包（启动、打开/关闭资源包界面）都会报错。
 * 返回 null 后原版只记一行「Found non-pack entry」
 */
@Mixin(targets = "net.minecraft.server.packs.repository.FolderRepositorySource$FolderPackDetector")
public abstract class FolderPackDetectorMixin {

    // 父类 PackDetector<T> 的泛型留下了同名桥接方法，必须写全描述符
    @Inject(
            method = "createZipPack(Ljava/nio/file/Path;)Lnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lantern$skipEncryptedZip(Path path, CallbackInfoReturnable<Pack.ResourcesSupplier> cir) {
        if (EncryptedZips.isEncrypted(path)) {
            cir.setReturnValue(null);
        }
    }
}
