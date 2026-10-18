package org.lantern.item.icon

import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import org.lantern.internal.icon.IconMesh

/**
 * 运行时图标的占位模型：本身不带面，绘制在 ItemRenderer.render 开头由 [ItemIconModels.render] 接管。
 * 从 getModel 返回它是为了让 GUI 光照（平面物品）、掉落物缩放等读模型属性的地方与原版 generated 一致
 */
class LanternIconBakedModel(
    val mesh: IconMesh,
    private val particle: TextureAtlasSprite
) : BakedModel {
    private val itemTransforms = if (mesh.handheld) IconTransforms.HANDHELD else IconTransforms.GENERATED

    override fun getQuads(state: BlockState?, direction: Direction?, random: RandomSource): List<BakedQuad> = emptyList()

    override fun useAmbientOcclusion(): Boolean = false

    override fun isGui3d(): Boolean = false

    override fun usesBlockLight(): Boolean = false

    override fun isCustomRenderer(): Boolean = false

    override fun getParticleIcon(): TextureAtlasSprite = particle

    override fun getTransforms(): ItemTransforms = itemTransforms

    override fun getOverrides(): ItemOverrides = ItemOverrides.EMPTY
}
