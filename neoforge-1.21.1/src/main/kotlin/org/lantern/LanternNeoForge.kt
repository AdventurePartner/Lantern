package org.lantern

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.LayeredDraw
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.core.registries.Registries
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.registries.RegisterEvent
import org.lantern.costume.renderer.CostumeRenderLayer
import org.lantern.feature.FeatureHooks
import org.lantern.internal.chat.ChatChannelNotificationRenderer
import org.lantern.internal.handler.CycleHandler
import org.lantern.internal.handler.LocalPackLoader
import org.lantern.internal.handler.ResourceHandler
import org.lantern.internal.handler.TextureHandler
import org.lantern.model.block.LanternBlockEntity
import org.lantern.model.block.LanternBlockRenderer
import org.lantern.model.block.TrackedBarrelRenderer
import org.lantern.platform.IdentifierBridge
import org.lantern.platform.NeoForgeClientListener
import org.lantern.platform.NeoForgePacketNetwork
import org.lantern.uix.renderer.HudRenderer
import org.slf4j.LoggerFactory
import java.util.function.Consumer

@Mod(value = Lantern.MOD_ID, dist = [Dist.CLIENT])
class LanternNeoForge(modBus: IEventBus) {

    init {
        Lantern.logger = LoggerFactory.getLogger(Lantern.MOD_ID)
        Lantern.logger.info("Lantern NeoForge 1.21.1 initializing...")
        // 首轮资源重载前预载本地包，启动时 GeckoLib 扫描烘焙即可命中模型文件
        LocalPackLoader.reload()

        // 原版注册表在 mod 构造前已冻结，只能在 RegisterEvent 里注册
        modBus.addListener(RegisterEvent::class.java, Consumer<RegisterEvent> { event ->
            event.register(Registries.BLOCK_ENTITY_TYPE, LanternBlockEntity.ID) { LanternBlockEntity.TYPE }
        })
        modBus.addListener(FMLClientSetupEvent::class.java, Consumer<FMLClientSetupEvent> { event ->
            event.enqueueWork { TextureHandler.tick() }
        })
        modBus.addListener(
            EntityRenderersEvent.RegisterRenderers::class.java,
            Consumer<EntityRenderersEvent.RegisterRenderers>(::registerRenderers)
        )
        modBus.addListener(
            EntityRenderersEvent.AddLayers::class.java,
            Consumer<EntityRenderersEvent.AddLayers>(::registerLayers)
        )
        modBus.addListener(
            RegisterClientReloadListenersEvent::class.java,
            Consumer<RegisterClientReloadListenersEvent> { event ->
                event.registerReloadListener(ResourceManagerReloadListener { _: ResourceManager ->
                    ResourceHandler.reload()
                })
            }
        )
        modBus.addListener(RegisterGuiLayersEvent::class.java, Consumer<RegisterGuiLayersEvent>(::registerGuiLayers))

        NeoForgePacketNetwork.register(modBus)
        FeatureHooks.register(NeoForgePacketNetwork::sendAnimationEvent)
        NeoForgeClientListener.register()

        Lantern.logger.info("Lantern NeoForge 1.21.1 initialized successfully!")
    }

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(LanternBlockEntity.TYPE) { LanternBlockRenderer() }
        event.registerBlockEntityRenderer(BlockEntityType.BARREL) { TrackedBarrelRenderer(it) }
    }

    private fun registerLayers(event: EntityRenderersEvent.AddLayers) {
        event.skins.forEach { skin ->
            event.getSkin<PlayerRenderer>(skin)?.let { renderer ->
                renderer.addLayer(CostumeRenderLayer(renderer))
            }
        }
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        // 原版层（index < 0 的 HUD）：相机覆盖层之上、准星/快捷栏之下，
        // 可见性跟随原版 HUD（界面打开时仍绘制），用于替代原版元素
        event.registerAbove(
            VanillaGuiLayers.CAMERA_OVERLAYS,
            IdentifierBridge.of(Lantern.MOD_ID, "hud_base"),
            LayeredDraw.Layer { graphics, _ -> HudRenderer.renderBase(graphics) }
        )
        // registerAboveAll：暗角在 CAMERA_OVERLAYS 图层按环境光照绘制，顶层 HUD 必须画在所有原版图层之上
        event.registerAboveAll(
            IdentifierBridge.of(Lantern.MOD_ID, "hud"),
            LayeredDraw.Layer { graphics, _ -> HudRenderer.renderTop(graphics) }
        )
        event.registerAboveAll(
            IdentifierBridge.of(Lantern.MOD_ID, "chat_notification"),
            LayeredDraw.Layer { graphics, _ -> ChatChannelNotificationRenderer.render(graphics) }
        )
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
    }
}
