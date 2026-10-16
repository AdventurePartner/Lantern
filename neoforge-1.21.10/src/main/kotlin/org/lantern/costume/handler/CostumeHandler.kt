package org.lantern.costume.handler

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.resources.ResourceLocation
import org.lantern.Lantern
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderer.CostumeRenderer
import org.lantern.costume.renderstate.CostumeRenderContext
import org.lantern.costume.slot.CostumeSlot
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.internal.mixin.accessor.PoseStackAccessor
import org.lantern.model.GeckoResourceIds
import software.bernie.geckolib.cache.GeckoLibResources
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CostumeHandler {
    private val definitions = ConcurrentHashMap<String, CostumeModelWrapper>()
    private val playerCostumes = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, String>>()
    private val renderers = ConcurrentHashMap<String, RendererEntry>()
    private val warnedModels = ConcurrentHashMap.newKeySet<ResourceLocation>()
    private val validatedCostumes = ConcurrentHashMap.newKeySet<String>()
    private val brokenCostumes = ConcurrentHashMap.newKeySet<String>()

    fun reload() {
        definitions.clear()
        playerCostumes.clear()
        renderers.clear()
        warnedModels.clear()
        validatedCostumes.clear()
        brokenCostumes.clear()
    }

    fun replaceDefinitions(newDefinitions: Map<String, CostumeModelWrapper>) {
        definitions.clear()
        definitions.putAll(newDefinitions)
        renderers.clear()
        warnedModels.clear()
        validatedCostumes.clear()
        brokenCostumes.clear()
        playerCostumes.values.forEach { slots ->
            slots.entries.removeIf { (_, costumeId) -> !definitions.containsKey(costumeId) }
        }
    }

    fun getCostume(costumeId: String): CostumeModelWrapper? = definitions[costumeId]

    fun refreshRenderers() {
        renderers.clear()
        warnedModels.clear()
        validatedCostumes.clear()
        brokenCostumes.clear()
    }

    fun hasAny(playerUUID: UUID): Boolean = playerCostumes[playerUUID]?.isNotEmpty() == true

    /**
     * P1 玩家宿主化收尾：该玩家是否套着 hostDriven 的 full_body 整替外观。
     * LivingEntityRendererMixin 据此把原版身体 tint 置 0（cutout 丢弃，
     * 盔甲/手持/layers 不受影响）——两具身体重叠的终解
     */
    @JvmStatic
    fun hasHostDrivenFullBody(playerUUID: UUID): Boolean {
        val slots = playerCostumes[playerUUID] ?: return false
        val costumeId = slots[CostumeSlot.FULL_BODY] ?: return false
        return definitions[costumeId]?.hostDriven == true
    }

    /**
     * P1 盔甲适配：取该玩家 hostDriven 整替外观的骨骼处理器（盔甲模型的姿态来源）。
     * 渲染器按 "uuid:costumeId" 持有 per-UUID 深拷贝骨骼树，本帧姿势在 submit 阶段
     * 由 AnimationHost 写入，盔甲的 setupAnim 在 flush 阶段读回——同帧内不会被覆盖
     */
    @JvmStatic
    fun hostBones(playerUUID: UUID): software.bernie.geckolib.animatable.processing.AnimationProcessor<*>? {
        val costumeId = hostFullBodyId(playerUUID) ?: return null
        return renderers["$playerUUID:$costumeId"]?.renderer?.geoModel?.animationProcessor
    }

    /**
     * 该玩家当前生效的 hostDriven 整替外观 id。
     * 连招按外观限定时用它选组——切了动画组，招式也得跟着换
     */
    @JvmStatic
    fun hostCostumeId(playerUUID: UUID): String? = hostFullBodyId(playerUUID)

    /** P1 盔甲适配：取 hostDriven 整替外观定义（盔甲需要它的 scale/offset 对齐身体） */
    @JvmStatic
    fun hostWrapper(playerUUID: UUID): CostumeModelWrapper? =
        hostFullBodyId(playerUUID)?.let(definitions::get)

    private fun hostFullBodyId(playerUUID: UUID): String? {
        val costumeId = playerCostumes[playerUUID]?.get(CostumeSlot.FULL_BODY) ?: return null
        return if (definitions[costumeId]?.hostDriven == true) costumeId else null
    }

    fun getAssignedCostumes(playerUUID: UUID): Map<CostumeSlot, CostumeModelWrapper> =
        playerCostumes[playerUUID]
            ?.mapNotNull { (slot, costumeId) -> definitions[costumeId]?.let { slot to it } }
            ?.toMap()
            ?: emptyMap()

    fun assignCostume(playerUUID: UUID, costumeId: String) {
        val wrapper = definitions[costumeId] ?: return
        val slots = playerCostumes.computeIfAbsent(playerUUID) { ConcurrentHashMap() }
        val previous = slots.put(wrapper.slot, costumeId)
        // 换整替外观 = 换动画库。播控指令是相对某一套库下发的，库换掉之后那条
        // 指令引用的剪辑在新库里查不到，留着只会在换回去时凭空续播半段。
        // 整替槽位真的变了才清，同 id 重复下发（登录全量同步）不受影响
        if (wrapper.slot == CostumeSlot.FULL_BODY && previous != null && previous != costumeId) {
            org.lantern.core.anim.control.AnimationControlStore.stop(playerUUID, null)
        }
    }

    fun removeCostume(playerUUID: UUID, slot: CostumeSlot? = null) {
        if (slot == null) {
            playerCostumes.remove(playerUUID)
        } else {
            playerCostumes[playerUUID]?.remove(slot)
        }
    }

    /**
     * 实体离场时淘汰该玩家的渲染器缓存，外观分配保留：服务端只在分配变化时广播，
     * 玩家走出视距再回来不会重发，删了分配外观就丢了
     */
    fun evictRenderers(playerUUID: UUID) {
        // hostDriven 渲染器键为 "uuid:costumeId"（含骨骼树深拷贝），随实体离场
        // 一并淘汰防止会话内无上限增长；共享渲染器（key=costumeId）不受影响，
        // 换维度/重回视距时按 submitForPlayer 的 computeIfAbsent 懒重建恢复
        val prefix = "$playerUUID:"
        if (renderers.keys.removeIf { it.startsWith(prefix) }) {
            Lantern.logger.debug("[Lantern] Evicted costume renderers for player {}", playerUUID)
        }
    }

    fun submitForPlayer(
        context: CostumeRenderContext,
        poseStack: PoseStack,
        submitNodes: SubmitNodeCollector,
        cameraState: CameraRenderState,
        packedLight: Int,
        partialTick: Float
    ) {
        val assigned = playerCostumes[context.playerId] ?: return
        assigned.values.forEach { costumeId ->
            if (costumeId in brokenCostumes) return@forEach
            val wrapper = definitions[costumeId] ?: return@forEach
            if (!isCostumeReady(costumeId, wrapper)) return@forEach
            // hostDriven 外观按玩家分配渲染器（骨骼树深拷贝隔离，多玩家同款不串台）；
            // 普通装饰外观维持按 costumeId 共享（快照路径的历史行为）
            val rendererKey = if (wrapper.hostDriven) "${context.playerId}:$costumeId" else costumeId
            val entry = renderers.computeIfAbsent(rendererKey) {
                val animatable = CostumeAnimatable(wrapper.animationStates, wrapper.hostDriven)
                val renderer = CostumeRenderer(wrapper, animatable)
                if (wrapper.hostDriven) renderer.bindPlayer(context.playerId)
                RendererEntry(animatable, renderer)
            }
            val depth = (poseStack as PoseStackAccessor).`lantern$getLastIndex`()
            try {
                entry.renderer.submit(
                    poseStack,
                    entry.animatable,
                    context,
                    submitNodes,
                    cameraState,
                    packedLight,
                    partialTick,
                    null
                )
            } catch (ex: RuntimeException) {
                // 一套坏外观不能把整个玩家渲染（连带后面的图层）一起带崩：弹回位姿、停用该外观
                while ((poseStack as PoseStackAccessor).`lantern$getLastIndex`() > depth) {
                    poseStack.popPose()
                }
                brokenCostumes.add(costumeId)
                renderers.remove(rendererKey)
                Lantern.logger.error("[Lantern] Disabled broken costume renderer: {}", costumeId, ex)
            }
        }
    }

    /**
     * 贴图资源缺失时标记为坏外观并只警告一次；模型还没进 GeckoLib 缓存时推迟。
     * 模型不查资源文件：GeckoLib 5 允许放在 geckolib/models/ 下而 yml 写 geo/，按 yml 路径查会误判
     */
    private fun isCostumeReady(costumeId: String, wrapper: CostumeModelWrapper): Boolean {
        if (costumeId !in validatedCostumes) {
            val manager = Minecraft.getInstance().resourceManager
            // hostDriven 外观画的是玩家皮肤，配置里的 texture 不参与渲染
            if (!wrapper.hostDriven && wrapper.textureUrl == null &&
                manager.getResource(wrapper.textureLocation).isEmpty
            ) {
                brokenCostumes.add(costumeId)
                Lantern.logger.warn("[Lantern] Costume texture resource missing: {} ({})", wrapper.textureLocation, costumeId)
                return false
            }
            validatedCostumes.add(costumeId)
        }
        val modelId = GeckoResourceIds.model(wrapper.modelLocation)
        if (!GeckoLibResources.getBakedModels().containsKey(modelId)) {
            if (warnedModels.add(modelId)) {
                Lantern.logger.warn("[Lantern] Costume model not yet cached, deferring render: {}", modelId)
            }
            return false
        }
        return true
    }

    private data class RendererEntry(
        val animatable: CostumeAnimatable,
        val renderer: CostumeRenderer
    )
}
