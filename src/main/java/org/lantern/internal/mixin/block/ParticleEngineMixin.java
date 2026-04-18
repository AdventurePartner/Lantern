package org.lantern.internal.mixin.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import org.lantern.model.block.LanternTerrainParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lantern.model.handler.BlockRendererHandler;
import org.lantern.model.wrapper.BlockModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * 拦截 ParticleEngine 的 destroy/crack 方法，
 * 为自定义方块使用正确纹理的粒子取代木桶粒子。
 *
 * destroy: 方块被破坏时的爆裂粒子
 * crack:   挖掘过程中的裂纹粒子
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow
    private ClientLevel level;

    // ==================== 破坏粒子 ====================

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void lantern$destroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!BlockRendererHandler.INSTANCE.isTrackedPosition(pos)) return;

        TextureAtlasSprite sprite = lantern$resolveSprite(pos);
        if (sprite == null) return;

        // 仿照 ParticleEngine.destroy 的原始逻辑，但使用自定义纹理 sprite
        VoxelShape shape = state.getShape(level, pos);
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double dx = Math.min(1.0, x1 - x0);
            double dy = Math.min(1.0, y1 - y0);
            double dz = Math.min(1.0, z1 - z0);
            // 每个轴至少 2 个粒子
            int nx = Math.max(2, (int) Math.ceil(dx / 0.25));
            int ny = Math.max(2, (int) Math.ceil(dy / 0.25));
            int nz = Math.max(2, (int) Math.ceil(dz / 0.25));

            for (int ix = 0; ix < nx; ix++) {
                for (int iy = 0; iy < ny; iy++) {
                    for (int iz = 0; iz < nz; iz++) {
                        double px = x0 + (ix + 0.5) * dx / nx;
                        double py = y0 + (iy + 0.5) * dy / ny;
                        double pz = z0 + (iz + 0.5) * dz / nz;

                        LanternTerrainParticle particle = new LanternTerrainParticle(
                            level,
                            pos.getX() + px, pos.getY() + py, pos.getZ() + pz,
                            px - 0.5, py - 0.5, pz - 0.5,
                            state, pos
                        );
                        particle.overrideSprite(sprite);
                        Minecraft.getInstance().particleEngine.add(particle);
                    }
                }
            }
        });
        ci.cancel();
    }

    // ==================== 挖掘粒子 ====================

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void lantern$crack(BlockPos pos, Direction direction, CallbackInfo ci) {
        BlockState state = level.getBlockState(pos);
        if (!BlockRendererHandler.INSTANCE.isTrackedPosition(pos)) return;

        TextureAtlasSprite sprite = lantern$resolveSprite(pos);
        if (sprite == null) return;

        // 仿照 ParticleEngine.crack 的原始逻辑
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return;

        var aabb = shape.bounds();
        double x = pos.getX() + level.random.nextDouble() * (aabb.maxX - aabb.minX - 0.2) + 0.1 + aabb.minX;
        double y = pos.getY() + level.random.nextDouble() * (aabb.maxY - aabb.minY - 0.2) + 0.1 + aabb.minY;
        double z = pos.getZ() + level.random.nextDouble() * (aabb.maxZ - aabb.minZ - 0.2) + 0.1 + aabb.minZ;

        switch (direction) {
            case DOWN  -> y = pos.getY() + aabb.minY - 0.1;
            case UP    -> y = pos.getY() + aabb.maxY + 0.1;
            case NORTH -> z = pos.getZ() + aabb.minZ - 0.1;
            case SOUTH -> z = pos.getZ() + aabb.maxZ + 0.1;
            case WEST  -> x = pos.getX() + aabb.minX - 0.1;
            case EAST  -> x = pos.getX() + aabb.maxX + 0.1;
        }

        LanternTerrainParticle particle = new LanternTerrainParticle(
            level, x, y, z, 0.0, 0.0, 0.0, state, pos
        );
        particle.overrideSprite(sprite);
        particle.setPower(0.2f).scale(0.6f);
        Minecraft.getInstance().particleEngine.add(particle);
        ci.cancel();
    }

    // ==================== 纹理解析 ====================

    /**
     * 按位置查 wrapper，将纹理路径转为 block atlas sprite。
     * 纹理路径 "textures/block/foo.png" → sprite ID "lantern:block/foo"。
     * 如果 sprite 不在 atlas（HTTP 纹理或非标准路径），回退到载体方块默认模型的粒子图标。
     */
    @Unique
    private TextureAtlasSprite lantern$resolveSprite(BlockPos pos) {
        BlockModelWrapper wrapper = BlockRendererHandler.INSTANCE.getWrapperByPos(pos);
        if (wrapper == null) return null;

        Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);

        // 尝试从 block atlas 获取自定义纹理 sprite
        ResourceLocation textureLoc = wrapper.getTextureLocation();
        ResourceLocation spriteId = lantern$textureToSpriteId(textureLoc);
        if (spriteId != null) {
            TextureAtlasSprite sprite = atlas.apply(spriteId);
            if (sprite != null && !lantern$isMissingSprite(sprite)) {
                return sprite;
            }
        }

        // 回退: 使用载体方块（木桶）默认模型的粒子纹理
        return Minecraft.getInstance().getBlockRenderer()
            .getBlockModel(
                net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState()
            ).getParticleIcon();
    }

    /**
     * 将 ResourceLocation 纹理路径转为 atlas sprite ID。
     * 例: lantern:textures/block/ruby_ore.png → lantern:block/ruby_ore
     */
    @Unique
    private static ResourceLocation lantern$textureToSpriteId(ResourceLocation textureLoc) {
        String path = textureLoc.getPath();
        if (!path.startsWith("textures/")) return null;
        String stripped = path.substring("textures/".length());
        if (stripped.endsWith(".png")) {
            stripped = stripped.substring(0, stripped.length() - ".png".length());
        }
        return ResourceLocation.fromNamespaceAndPath(textureLoc.getNamespace(), stripped);
    }

    @Unique
    private static boolean lantern$isMissingSprite(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(
            net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()
        );
    }
}
