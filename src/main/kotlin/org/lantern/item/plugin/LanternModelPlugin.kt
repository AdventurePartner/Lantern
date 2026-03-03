package org.lantern.item.plugin

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import org.lantern.internal.handler.ResourceHandler

object LanternModelPlugin : ModelLoadingPlugin {

    override fun onInitializeModelLoader(ctx: ModelLoadingPlugin.Context) {
        // 在 atlas 縫合前預載入自定義圖標模型（fabric_resource 變體），
        // 確保紋理在烘焙時可由 TextureGetter 正確解析
        ResourceHandler.getItemCustomIcons().forEach { (_, modelLoc) ->
            ctx.addModels(modelLoc.id())
        }
    }
}
