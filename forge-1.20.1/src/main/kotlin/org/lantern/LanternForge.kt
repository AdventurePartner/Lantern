package org.lantern

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent
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
import org.lantern.model.block.LanternBlockEntity
import org.lantern.model.block.LanternBlockRenderer
import org.lantern.model.block.TrackedBarrelRenderer
import org.lantern.model.handler.BlockRendererHandler
import org.lantern.model.handler.RendererHandler
import org.lantern.platform.ForgeClientListener
import org.lantern.platform.ForgePacketNetwork
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

    companion object {
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
        }
    }
}
