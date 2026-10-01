package org.lantern

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.lantern.costume.handler.CostumeHandler
import org.lantern.costume.renderer.CostumeRenderLayer
import org.lantern.internal.chat.ChatChannelHandler
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.internal.parser.UiParser
import org.lantern.model.block.LanternBlockEntity
import org.lantern.model.block.LanternBlockRenderer
import org.lantern.model.block.TrackedBarrelRenderer
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.platform.ForgeClientListener
import org.lantern.platform.ForgePacketNetwork
import org.lantern.uix.renderer.HudRenderer
import org.slf4j.LoggerFactory
import software.bernie.geckolib.GeckoLib
import java.util.function.Consumer

@Mod(Lantern.MOD_ID)
class LanternForge {

    init {
        Lantern.logger = LoggerFactory.getLogger(Lantern.MOD_ID)
        Lantern.logger.info("Lantern Forge 1.20.1 initializing...")

        GeckoLib.shadowInit()

        val modBus = FMLJavaModLoadingContext.get().modEventBus
        LanternBlockEntity.register(modBus)
        modBus.addListener(Consumer<FMLClientSetupEvent> { event -> onClientSetup(event) })
        modBus.addListener(Consumer<EntityRenderersEvent.RegisterRenderers> { event -> registerRenderers(event) })
        modBus.addListener(Consumer<EntityRenderersEvent.AddLayers> { event -> registerLayers(event) })
        modBus.addListener(Consumer<RegisterClientReloadListenersEvent> { event -> registerReloadListeners(event) })
        modBus.addListener(Consumer<RegisterGuiOverlaysEvent> { event -> registerGuiOverlays(event) })

        ForgePacketNetwork.registerPackets()
        MinecraftForge.EVENT_BUS.register(ForgeClientListener)

        Lantern.logger.info("Lantern Forge 1.20.1 initialized successfully!")
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            TextureHandler.tick()
        }
    }

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(LanternBlockEntity.TYPE.get()) { LanternBlockRenderer() }
        event.registerBlockEntityRenderer(BlockEntityType.BARREL) { TrackedBarrelRenderer(it) }
    }

    private fun registerLayers(event: EntityRenderersEvent.AddLayers) {
        event.skins.forEach { skin ->
            event.getSkin<PlayerRenderer>(skin)?.let { renderer ->
                renderer.addLayer(CostumeRenderLayer(renderer))
            }
        }
    }

    private fun registerReloadListeners(event: RegisterClientReloadListenersEvent) {
        event.registerReloadListener(ResourceManagerReloadListener { _: ResourceManager ->
            ResourceHandler.reload()
        })
    }

    // Forge 1.20.1 用 ForgeGui 替换了原版 Gui，其 render 不调用 super，
    // 注入 Gui.render 的 Mixin 永远不会执行——HUD 只能挂在 overlay 表上
    private fun registerGuiOverlays(event: RegisterGuiOverlaysEvent) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "hud_base") { _, graphics, _, _, _ ->
            HudRenderer.renderBase(graphics)
        }
        event.registerAboveAll("hud") { _, graphics, _, _, _ ->
            // 抬到全部原版 overlay 之上，避免被先画的物品等高 z 像素经深度测试遮挡
            graphics.pose().pushPose()
            graphics.pose().translate(0f, 0f, TOP_LAYER_Z)
            HudRenderer.renderTop(graphics)
            graphics.pose().popPose()
        }
    }

    companion object {
        private const val TOP_LAYER_Z = 3000f

        fun initializeContext(client: Minecraft) {
            if (CycleHandler.context != null || client.level == null) {
                return
            }
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

        fun clearClientCaches() {
            ForgePacketNetwork.clear()
            ChatChannelHandler.clear()
            CostumeHandler.reload()
            BlockRendererHandler.clear()
            BlockRendererHandler.clearPositions()
            BlockRendererHandler.resetDiagnosticFlags()
            RendererHandler.reload()
            UiParser.resetAll()
        }
    }
}
