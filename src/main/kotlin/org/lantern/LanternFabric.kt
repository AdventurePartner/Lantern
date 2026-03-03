package org.lantern

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import org.lantern.item.plugin.LanternModelPlugin
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.listen.FabricClientListener
import org.lantern.internal.network.PacketNetwork
import org.lantern.uix.renderer.CanvasRenderer
import org.slf4j.LoggerFactory

class LanternFabric : ClientModInitializer {

    override fun onInitializeClient() {
        // 获取日志管道
        Lantern.logger = LoggerFactory.getLogger(Lantern.MOD_ID)
        Lantern.logger.info("Lantern Fabric initializing...")

        // GeckoLib 4.7+ 不再需要手动初始化
        Lantern.logger.info("Initialized GeckoLib successfully")

        // 注册客户端事件
        registerClientEvents()
        FabricClientListener.register()

        // 注册 HUD 渲染回调
        HudRenderCallback.EVENT.register { graphics, tickDeltaManager ->
            CanvasRenderer.hudCanvas.render(graphics, 0, 0, tickDeltaManager.getGameTimeDeltaPartialTick(false))
        }

        // 注册网络包
        PacketNetwork.registerPackets()
        Lantern.logger.info("Registered communication data packets")

        // 注册自定义物品模型插件（纯代码烘焙，不需要 JSON 文件）
        ModelLoadingPlugin.register(LanternModelPlugin)

        // 注册资源重载监听器
        registerResourceReloadListener()

        Lantern.logger.info("Lantern Fabric initialized successfully!")
    }

    private fun registerClientEvents() {
        // 注册客户端 tick 事件，用于初始化 EntityRendererProvider.Context
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (CycleHandler.context == null && client.level != null) {
                CycleHandler.context = EntityRendererProvider.Context(
                    client.entityRenderDispatcher,
                    client.itemRenderer,
                    client.blockRenderer,
                    client.entityRenderDispatcher.itemInHandRenderer,
                    client.resourceManager,
                    client.entityModels,
                    client.font
                )
            }
        }
    }

    private fun registerResourceReloadListener() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            object : SimpleSynchronousResourceReloadListener {
                override fun getFabricId(): ResourceLocation {
                    return ResourceLocation.fromNamespaceAndPath(Lantern.MOD_ID, "reload_listener")
                }

                override fun onResourceManagerReload(manager: ResourceManager) {
                    ResourceHandler.reload()
                }
            }
        )
    }
}
