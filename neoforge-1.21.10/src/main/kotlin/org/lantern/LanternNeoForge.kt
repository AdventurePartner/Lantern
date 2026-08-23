package org.lantern

import net.minecraft.world.level.block.Blocks
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import net.neoforged.neoforge.client.gui.GuiLayer
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent
import net.neoforged.neoforge.event.AddPackFindersEvent
import org.lantern.internal.chat.ChatChannelNotificationRenderer
import org.lantern.internal.handler.LanternReloadListener
import org.lantern.internal.pack.LanternDynamicPackSource
import org.lantern.model.handler.RendererHandler
import org.lantern.neoforge.block.LanternBlockClientExtensions
import org.lantern.neoforge.block.TrackedBarrelRenderer
import org.lantern.neoforge.item.LanternItemModels
import org.lantern.neoforge.CostumeRenderRegistration
import org.lantern.platform.IdentifierBridge
import org.lantern.platform.NeoForgeClientEvents
import org.lantern.platform.NeoForgePacketNetwork
import org.lantern.uix.renderer.HudRenderer
import org.slf4j.LoggerFactory
import java.util.function.Consumer

@Mod(value = Lantern.MOD_ID, dist = [Dist.CLIENT])
class LanternNeoForge(modBus: IEventBus) {
    init {
        Lantern.logger = LoggerFactory.getLogger(Lantern.MOD_ID)
        Lantern.logger.info("Lantern NeoForge 1.21.10 initializing...")

        NeoForgePacketNetwork.register(modBus)
        NeoForgeClientEvents.register()
        modBus.addListener(
            AddPackFindersEvent::class.java,
            Consumer<AddPackFindersEvent>(LanternDynamicPackSource::register)
        )
        modBus.addListener(
            AddClientReloadListenersEvent::class.java,
            Consumer<AddClientReloadListenersEvent> { event ->
                event.addListener(
                    IdentifierBridge.of(Lantern.MOD_ID, "dynamic_resources"),
                    LanternReloadListener()
                )
            }
        )
        modBus.addListener(
            RegisterGuiLayersEvent::class.java,
            Consumer<RegisterGuiLayersEvent>(::registerGuiLayers)
        )
        modBus.addListener(
            RegisterRenderStateModifiersEvent::class.java,
            Consumer<RegisterRenderStateModifiersEvent>(CostumeRenderRegistration::registerRenderStateModifiers)
        )
        modBus.addListener(
            EntityRenderersEvent.AddLayers::class.java,
            Consumer<EntityRenderersEvent.AddLayers> { event ->
                RendererHandler.updateContext(event.context)
                CostumeRenderRegistration.addPlayerLayers(event)
            }
        )
        modBus.addListener(
            EntityRenderersEvent.RegisterRenderers::class.java,
            Consumer<EntityRenderersEvent.RegisterRenderers> { event ->
                event.registerBlockEntityRenderer(
                    net.minecraft.world.level.block.entity.BlockEntityType.BARREL
                ) { TrackedBarrelRenderer() }
            }
        )
        modBus.addListener(
            RegisterClientExtensionsEvent::class.java,
            Consumer<RegisterClientExtensionsEvent> { event ->
                event.registerBlock(LanternBlockClientExtensions, Blocks.BARREL)
            }
        )
        modBus.addListener(
            ModelEvent.ModifyBakingResult::class.java,
            Consumer<ModelEvent.ModifyBakingResult>(LanternItemModels::modifyBakingResult)
        )
        Lantern.logger.info("Lantern NeoForge 1.21.10 initialized successfully")
    }

    private fun registerGuiLayers(event: RegisterGuiLayersEvent) {
        event.registerBelowAll(
            IdentifierBridge.of(Lantern.MOD_ID, "hud"),
            GuiLayer { graphics, delta ->
                HudRenderer.render(graphics, delta.getGameTimeDeltaPartialTick(false))
            }
        )
        event.registerAboveAll(
            IdentifierBridge.of(Lantern.MOD_ID, "chat_notification"),
            GuiLayer { graphics, _ -> ChatChannelNotificationRenderer.render(graphics) }
        )
    }
}
