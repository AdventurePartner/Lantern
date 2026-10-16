package org.lantern

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.core.registries.Registries
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.RegisterEvent
import org.lantern.costume.renderer.CostumeRenderLayer
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.LocalPackLoader
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.handler.SessionCleanup
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.block.LanternBlockEntity
import org.lantern.model.block.LanternBlockRenderer
import org.lantern.model.block.TrackedBarrelRenderer
import org.lantern.platform.ForgeClientListener
import org.lantern.platform.ForgePacketNetwork
import org.slf4j.LoggerFactory
import java.util.function.Consumer

@Mod(Lantern.MOD_ID)
class LanternForge {

    init {
        Lantern.logger = LoggerFactory.getLogger(Lantern.MOD_ID)
        Lantern.logger.info("Lantern Forge 1.21.1 initializing...")
        // 首轮资源重载前预载本地包，启动时 GeckoLib 扫描烘焙即可命中模型文件
        LocalPackLoader.reload()

        val modBus = FMLJavaModLoadingContext.get().modEventBus
        // 原版注册表在 mod 构造前已冻结，直写 BuiltInRegistries 会抛异常，只能在 RegisterEvent 里注册
        modBus.addListener(Consumer<RegisterEvent> { event ->
            event.register(Registries.BLOCK_ENTITY_TYPE, LanternBlockEntity.ID) { LanternBlockEntity.TYPE }
        })
        modBus.addListener(Consumer<FMLClientSetupEvent> { event -> onClientSetup(event) })
        modBus.addListener(Consumer<EntityRenderersEvent.RegisterRenderers> { event -> registerRenderers(event) })
        modBus.addListener(Consumer<EntityRenderersEvent.AddLayers> { event -> registerLayers(event) })
        modBus.addListener(Consumer<RegisterClientReloadListenersEvent> { event -> registerReloadListeners(event) })

        ForgePacketNetwork.registerPackets()
        org.lantern.feature.FeatureHooks.register(ForgePacketNetwork::sendAnimationEvent)
        MinecraftForge.EVENT_BUS.register(ForgeClientListener)
        MinecraftForge.EVENT_BUS.register(org.lantern.platform.WorldImageForgeEvents)

        Lantern.logger.info("Lantern Forge 1.21.1 initialized successfully!")
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            TextureHandler.tick()
        }
    }

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(LanternBlockEntity.TYPE) { LanternBlockRenderer() }
        event.registerBlockEntityRenderer(BlockEntityType.BARREL) { TrackedBarrelRenderer(it) }
    }

    private fun registerLayers(event: EntityRenderersEvent.AddLayers) {
        event.skins.forEach { skin ->
            event.getPlayerSkin<PlayerRenderer>(skin)?.let { renderer ->
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
            SessionCleanup.onDisconnect()
        }
    }
}
