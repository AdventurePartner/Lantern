package org.lantern

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import org.lantern.item.plugin.LanternModelPlugin
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.lantern.costume.handler.CostumeHandler
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.internal.listen.FabricClientListener
import org.lantern.model.block.LanternBlockEntity
import org.lantern.model.block.LanternBlockRenderer
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.internal.network.PacketNetwork
import org.lantern.model.util.EntityStateUtil
import org.lantern.uix.renderer.CanvasRenderer
import org.lantern.uix.renderer.OverlayRenderer
import org.slf4j.LoggerFactory

class LanternFabric : ClientModInitializer {

    override fun onInitializeClient() {
        // 获取日志管道
        Lantern.logger = LoggerFactory.getLogger(Lantern.MOD_ID)
        Lantern.logger.info("Lantern Fabric initializing... [BUILD v20260405-barrel-renderer]")

        // GeckoLib 4.7+ 不再需要手动初始化
        Lantern.logger.info("Initialized GeckoLib successfully")

        // 注册自定义方块 BlockEntityType + 渲染器
        LanternBlockEntity.register()
        BlockEntityRenderers.register(LanternBlockEntity.TYPE) { LanternBlockRenderer() }
        // barrel (open=true) 的自定义方块渲染——通过标准 block entity 渲染阶段，Iris/Sodium 完全支持
        BlockEntityRenderers.register(net.minecraft.world.level.block.entity.BlockEntityType.BARREL) {
            org.lantern.model.block.TrackedBarrelRenderer(it)
        }
        Lantern.logger.info("Registered custom block entity renderers (LanternBlockEntity + BarrelBlockEntity)")

        // 注册客户端事件
        registerClientEvents()
        FabricClientListener.register()

        // 实体卸载时清理位置缓存，防止 EntityStateUtil.lastPositions 内存泄漏
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            EntityStateUtil.cleanupEntityPosition(entity.id)
        }

        // 断开连接时清理所有缓存，防止 CostumeHandler/ResourceHandler 内存泄漏
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            CostumeHandler.reload()
            BlockRendererHandler.clear()
            BlockRendererHandler.clearPositions()
            BlockRendererHandler.resetDiagnosticFlags()
            RendererHandler.reload()
        }

        // 注册 Overlay Screen 鼠标事件
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            ScreenMouseEvents.allowMouseClick(screen).register { scr, mouseX, mouseY, button ->
                !OverlayRenderer.handleClick(scr, mouseX, mouseY, button)
            }
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
            TextureHandler.tick()
        }

        // 全链路诊断：首次 AFTER_ENTITIES 时检查每个 tracked 位置的实际状态
        var diagDone = false
        WorldRenderEvents.AFTER_ENTITIES.register { _ ->
            if (diagDone) return@register
            val level = Minecraft.getInstance().level ?: return@register
            val positions = BlockRendererHandler.getAllTrackedPositions()
            if (positions.isEmpty()) return@register
            diagDone = true
            Lantern.logger.info("[Lantern] ====== RENDER PIPELINE DIAGNOSTIC ({} tracked positions) ======", positions.size)
            var idx = 0
            for ((pos, variation) in positions) {
                if (idx >= 5) {
                    Lantern.logger.info("[Lantern]   ... and {} more", positions.size - 5)
                    break
                }
                val state = level.getBlockState(pos)
                val be = level.getBlockEntity(pos)
                val beType = be?.type?.let { net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(it) }
                val renderShape = state.renderShape
                val isBarrel = state.`is`(net.minecraft.world.level.block.Blocks.BARREL)
                val isOpen = if (isBarrel) state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) else false
                val hasWrapper = BlockRendererHandler.getWrapperByPos(pos) != null
                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block)
                Lantern.logger.info(
                    "[Lantern]   pos={} blockId={} isBarrel={} open={} renderShape={} beType={} hasWrapper={} variation={}",
                    pos, blockId, isBarrel, isOpen, renderShape, beType, hasWrapper, variation
                )
                idx++
            }
            // 检查 TrackedBarrelRenderer 是否注册
            val ctx = Minecraft.getInstance()
            val rendererDispatcher = ctx.blockEntityRenderDispatcher
            val testBE = level.getBlockEntity(positions.first().first)
            if (testBE != null) {
                val renderer = rendererDispatcher.getRenderer(testBE)
                Lantern.logger.info("[Lantern]   BlockEntityRenderer for first pos: {}", renderer?.javaClass?.name ?: "NULL")
            } else {
                Lantern.logger.info("[Lantern]   No BlockEntity at first tracked position!")
            }
            Lantern.logger.info("[Lantern] ====== END DIAGNOSTIC ======")
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
