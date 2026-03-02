package org.lantern.internal.atlas

import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.client.resources.TextureAtlasHolder
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern

class LanternTextureAtlasHolder(
    textureManager: TextureManager
) : TextureAtlasHolder(
    textureManager,
    ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "atlas/lantern"),
    ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "atlases/lantern.json")
)