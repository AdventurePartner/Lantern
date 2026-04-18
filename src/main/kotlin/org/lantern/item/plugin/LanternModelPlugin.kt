package org.lantern.item.plugin

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import org.lantern.internal.handler.ResourceHandler

object LanternModelPlugin : ModelLoadingPlugin {

    override fun onInitializeModelLoader(ctx: ModelLoadingPlugin.Context) {
        // 預載自定義物品圖標模型（fabric_resource 變體）
        ResourceHandler.getItemCustomIcons().forEach { (_, modelLoc) ->
            ctx.addModels(modelLoc.id())
        }
        // 預載自定義方塊模型
        ResourceHandler.getBlockCustomModels().forEach { (_, modelLoc) ->
            ctx.addModels(modelLoc.id())
        }
    }
}
