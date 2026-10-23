package org.lantern.costume.geo

import net.minecraft.resources.Identifier
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.GeckoResourceIds
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.base.GeoRenderState

/**
 * 外观模型。GeckoLib 5.4 起骨骼不可变、姿势只在本次绘制里经快照挂上，多玩家共用缓存里的
 * 同一棵骨骼树也不会串台，不再需要 5.3 时按渲染器深拷贝骨骼树。姿势写入与原版部件跟随
 * 都在 CostumeRenderer 的 adjustModelBonesForRender 里
 */
class CostumeGeoModel(
    private val wrapper: CostumeModelWrapper,
    /** P1 玩家宿主化：per-UUID 渲染器的归属玩家，hostDriven 时贴图取其真实皮肤 */
    @Volatile private var ownerPlayerId: java.util.UUID? = null
) : GeoModel<CostumeAnimatable>() {

    fun bindPlayer(playerId: java.util.UUID) {
        ownerPlayerId = playerId
    }

    /**
     * hostDriven：返回玩家真实皮肤（几何 UV 即标准 64x64 皮肤布局，
     * 方块人=玩家皮肤人偶）；皮肤未加载时回落 wrapper 静态贴图。
     * 皮肤链：Connection.getPlayerInfo(uuid).getSkin().body().texturePath()
     */
    override fun getTextureResource(renderState: GeoRenderState): Identifier {
        if (wrapper.hostDriven) {
            val playerId = ownerPlayerId ?: return superTexture()
            val skin = net.minecraft.client.Minecraft.getInstance().connection
                ?.getPlayerInfo(playerId)?.skin
            skin?.let { return it.body.texturePath() }
        }
        return superTexture()
    }

    private fun superTexture(): Identifier =
        wrapper.textureUrl?.let(TextureHandler::getTexture) ?: wrapper.textureLocation

    override fun getModelResource(renderState: GeoRenderState): Identifier =
        GeckoResourceIds.model(wrapper.modelLocation)

    override fun getAnimationResource(animatable: CostumeAnimatable): Identifier =
        GeckoResourceIds.animation(wrapper.animationLocation)
}
