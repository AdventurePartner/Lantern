package org.lantern.internal.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lantern.uix.renderer.ContainerPanelBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor extends ContainerPanelBridge {
    @Override
    @Accessor("leftPos")
    int lantern$getLeftPos();

    @Override
    @Accessor("topPos")
    int lantern$getTopPos();

    @Override
    @Accessor("imageWidth")
    int lantern$getImageWidth();

    @Override
    @Accessor("imageHeight")
    int lantern$getImageHeight();
}
