package org.lantern.model.block;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TerrainParticle 的子类，暴露 setSprite 以允许自定义纹理。
 * TerrainParticle.setSprite 是 protected，无法从外部 Mixin 调用。
 */
public class LanternTerrainParticle extends TerrainParticle {

    public LanternTerrainParticle(
        ClientLevel level,
        double x, double y, double z,
        double xSpeed, double ySpeed, double zSpeed,
        BlockState state, BlockPos pos
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, state, pos);
    }

    /** 公开 setSprite，覆盖构造时从 BlockModel 取到的错误纹理 */
    public void overrideSprite(TextureAtlasSprite sprite) {
        setSprite(sprite);
    }
}
