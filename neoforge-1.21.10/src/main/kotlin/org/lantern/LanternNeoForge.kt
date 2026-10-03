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
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent
import net.neoforged.neoforge.event.AddPackFindersEvent
import org.lantern.internal.chat.ChatChannelNotificationRenderer
import org.lantern.internal.handler.LanternReloadListener
import org.lantern.internal.network.NetworkParser
import org.lantern.internal.pack.LanternDynamicPackSource
import org.lantern.model.handler.AnimationControlHandler
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
        // 首轮资源重载前预载本地包，启动时 GeckoLib 扫描烘焙即可命中模型文件
        org.lantern.internal.handler.LocalPackLoader.reload()

        NeoForgePacketNetwork.register(modBus)
        NeoForgeClientEvents.register()
        NetworkParser.animationControlHandler = { uuid, action, animation, transition, loop, speed, seek, uninterruptible, toCombat, library, seq, exit ->
            AnimationControlHandler.handle(uuid, action, animation, transition, loop, speed, seek, uninterruptible, toCombat, library, seq, exit)
        }
        NetworkParser.animationEventSender = { uuid, animation, event ->
            NeoForgePacketNetwork.sendAnimationEvent(uuid, animation, event)
        }
        // packet 17 变量同步：只触碰 ConcurrentHashMap 且值编译无 MC 依赖，网络线程直接执行
        org.lantern.core.anim.molang.MolangVariableStore.compiler = org.lantern.animation.GeckoLibMolang
        NetworkParser.molangVariableHandler = org.lantern.core.anim.molang.MolangVariableStore::update
        // packet 18 相机（shoulder + 演出指令）：NetworkParser 统一调度到主线程后更新渲染状态
        org.lantern.core.camera.CameraControl.port = org.lantern.camera.control.CameraPortNeoForge
        NetworkParser.cameraActionHandler = org.lantern.core.camera.CameraControl::handle
        NetworkParser.playerActionHandler = org.lantern.core.action.PlayerActionDefs::load
        // packet 20：只写 ConcurrentHashMap，与 packet 17 同理，网络线程直接执行
        NetworkParser.entityBindHandler = org.lantern.core.bind.BindStore::handle
        // packet 21：写表必须与并集缓存重算同线程，收包线程非主线程时调度过去
        NetworkParser.inputLockHandler = { obj ->
            val client = net.minecraft.client.Minecraft.getInstance()
            if (client.isSameThread) {
                org.lantern.core.input.InputLockStore.handle(obj)
            } else {
                client.execute { org.lantern.core.input.InputLockStore.handle(obj) }
            }
        }
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
        // 原版层（index < 0 的 HUD）：相机覆盖层之上、准星/快捷栏之下，
        // 可见性跟随原版 HUD（界面打开时仍绘制），用于替代原版元素
        event.registerAbove(
            VanillaGuiLayers.CAMERA_OVERLAYS,
            IdentifierBridge.of(Lantern.MOD_ID, "hud_base"),
            GuiLayer { graphics, _ -> HudRenderer.renderBase(graphics) }
        )
        // registerAboveAll：vignette（暗角）在 CAMERA_OVERLAYS 图层按环境光照绘制，
        // BelowAll 会被它在黑暗环境下压暗；顶层 HUD 必须画在所有原版图层之上
        event.registerAboveAll(
            IdentifierBridge.of(Lantern.MOD_ID, "hud"),
            GuiLayer { graphics, _ -> HudRenderer.renderTop(graphics) }
        )
        event.registerAboveAll(
            IdentifierBridge.of(Lantern.MOD_ID, "chat_notification"),
            GuiLayer { graphics, _ -> ChatChannelNotificationRenderer.render(graphics) }
        )
    }
}
