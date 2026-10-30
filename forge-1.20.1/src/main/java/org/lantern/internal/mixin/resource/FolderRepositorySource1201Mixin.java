package org.lantern.internal.mixin.resource;

import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import org.lantern.internal.handler.EncryptedZips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/**
 * 1.20.1 没有 FolderPackDetector，扫描入口是静态的 detectPackResources；
 * 返回 null 时 discoverPacks 直接跳过，不打日志
 */
@Mixin(FolderRepositorySource.class)
public abstract class FolderRepositorySource1201Mixin {

    @Inject(method = "detectPackResources", at = @At("HEAD"), cancellable = true)
    private static void lantern$skipEncryptedZip(Path path, boolean builtin, CallbackInfoReturnable<Pack.ResourcesSupplier> cir) {
        if (EncryptedZips.isEncrypted(path)) {
            cir.setReturnValue(null);
        }
    }
}
