package org.lantern.costume.handler

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import org.lantern.Lantern
import org.lantern.costume.bone.PlayerBoneSnapshot
import org.lantern.costume.entity.CostumeAnimatable
import org.lantern.costume.renderer.CostumeItemRenderer
import org.lantern.costume.renderer.CostumeRenderer
import org.lantern.costume.slot.CostumeSlot
import org.lantern.costume.wrapper.CostumeModelWrapper
import org.lantern.core.anim.control.AnimationControlStore
import org.lantern.internal.asset.AssetSync
import org.lantern.internal.asset.LanternAssets
import org.lantern.internal.mixin.accessor.PoseStackAccessor
import software.bernie.geckolib.animation.AnimationProcessor
import software.bernie.geckolib.cache.GeckoLibCache
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CostumeHandler {
    private val costumeDefinitions = ConcurrentHashMap<String, CostumeModelWrapper>()

    // UUID -> (CostumeSlot -> costumeId)
    private val playerCostumes = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, String>>()

    // UUID -> (CostumeSlot -> CostumeRenderer)
    private val playerRenderers = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, CostumeRenderer>>()

    // UUID -> (CostumeSlot -> CostumeAnimatable)
    private val playerAnimatables = ConcurrentHashMap<UUID, ConcurrentHashMap<CostumeSlot, CostumeAnimatable>>()

    private val itemRenderers = ConcurrentHashMap<String, CostumeItemRenderer>()
    private val validatedCostumes = ConcurrentHashMap.newKeySet<String>()
    private val brokenCostumes = ConcurrentHashMap.newKeySet<String>()
    private val deferredModels = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun reload() {
        costumeDefinitions.clear()
        playerCostumes.clear()
        playerRenderers.clear()
        playerAnimatables.clear()
        itemRenderers.clear()
        validatedCostumes.clear()
        brokenCostumes.clear()
        deferredModels.clear()
    }

    fun addCostume(id: String, wrapper: CostumeModelWrapper) {
        costumeDefinitions[id] = wrapper
        playerRenderers.values.forEach { it.remove(wrapper.slot) }
        playerAnimatables.values.forEach { it.remove(wrapper.slot) }
        itemRenderers.remove(id)
        validatedCostumes.remove(id)
        brokenCostumes.remove(id)
        deferredModels.remove(wrapper.modelLocation)
    }

    fun replaceDefinitions(definitions: Map<String, CostumeModelWrapper>) {
        costumeDefinitions.clear()
        costumeDefinitions.putAll(definitions)
        playerCostumes.values.forEach { slots ->
            slots.entries.removeIf { (_, costumeId) -> !costumeDefinitions.containsKey(costumeId) }
        }
        refreshRenderers()
    }

    fun refreshRenderers() {
        playerRenderers.clear()
        playerAnimatables.clear()
        itemRenderers.clear()
        validatedCostumes.clear()
        brokenCostumes.clear()
        deferredModels.clear()
    }

    fun getCostume(costumeId: String): CostumeModelWrapper? = costumeDefinitions[costumeId]

    fun hasAny(playerUUID: UUID): Boolean = playerCostumes[playerUUID]?.isNotEmpty() == true

    /** 该玩家是否套着 hostDriven 的 full_body 整替外观（原版身体此时不再绘制） */
    @JvmStatic
    fun hasHostDrivenFullBody(playerUUID: UUID): Boolean = hostFullBodyId(playerUUID) != null

    /** 该玩家当前生效的 hostDriven 整替外观 id；连招按外观限定时用它选组 */
    @JvmStatic
    fun hostCostumeId(playerUUID: UUID): String? = hostFullBodyId(playerUUID)

    /** hostDriven 整替外观定义：动画库与状态表从这里取，盔甲/附件也要它的 scale/offset */
    @JvmStatic
    fun hostWrapper(playerUUID: UUID): CostumeModelWrapper? =
        hostFullBodyId(playerUUID)?.let(costumeDefinitions::get)

    private fun hostFullBodyId(playerUUID: UUID): String? {
        val costumeId = playerCostumes[playerUUID]?.get(CostumeSlot.FULL_BODY) ?: return null
        return if (costumeDefinitions[costumeId]?.hostDriven == true) costumeId else null
    }

    /** hostDriven 整替外观的骨骼处理器（本帧姿势已由 prepareHost 写入）；渲染器还没建出来时为 null */
    @JvmStatic
    fun hostBones(playerUUID: UUID): AnimationProcessor<*>? {
        if (hostFullBodyId(playerUUID) == null) return null
        return playerRenderers[playerUUID]?.get(CostumeSlot.FULL_BODY)?.costumeModel?.animationProcessor
    }

    /**
     * 玩家渲染入口调用：hostDriven 整替外观可用时算好本帧姿势并返回骨骼处理器。
     * 原版 PlayerModel 部件与披风等附属层都在 CostumeRenderLayer 之前渲染，姿势必须先于它们就绪。
     * 外观缺资源或还没进 GeckoLib 缓存时返回 null，调用方照原版画身体，玩家不会凭空消失
     */
    @JvmStatic
    fun prepareHost(entity: Entity): AnimationProcessor<*>? {
        val costumeId = hostFullBodyId(entity.uuid) ?: return null
        if (brokenCostumes.contains(costumeId)) return null
        val wrapper = costumeDefinitions[costumeId] ?: return null
        if (!isCostumeReady(costumeId, wrapper)) return null
        val renderers = playerRenderers.computeIfAbsent(entity.uuid) { ConcurrentHashMap() }
        val animatables = playerAnimatables.computeIfAbsent(entity.uuid) { ConcurrentHashMap() }
        val animatable = animatables.computeIfAbsent(CostumeSlot.FULL_BODY) { CostumeAnimatable(true) }
        val renderer = renderers.computeIfAbsent(CostumeSlot.FULL_BODY) { CostumeRenderer(wrapper, animatable) }
        return try {
            renderer.prepareHostPose(entity)
            renderer.costumeModel.animationProcessor
        } catch (ex: RuntimeException) {
            brokenCostumes.add(costumeId)
            renderers.remove(CostumeSlot.FULL_BODY)
            animatables.remove(CostumeSlot.FULL_BODY)
            Lantern.logger.error("[Lantern] Disabled broken costume renderer: {}", costumeId, ex)
            null
        }
    }

    fun assignCostume(playerUUID: UUID, costumeId: String) {
        val wrapper = costumeDefinitions[costumeId] ?: return
        val slot = wrapper.slot
        val previous = playerCostumes.computeIfAbsent(playerUUID) { ConcurrentHashMap() }.put(slot, costumeId)
        playerRenderers[playerUUID]?.remove(slot)
        playerAnimatables[playerUUID]?.remove(slot)
        // 换整替外观 = 换动画库，旧库下发的强制播放在新库里查不到剪辑，留着只会在换回去时凭空续播。
        // 同 id 重复下发（登录全量同步）不清
        if (slot == CostumeSlot.FULL_BODY && previous != null && previous != costumeId) {
            AnimationControlStore.stop(playerUUID, null)
        }
    }

    fun removeCostume(playerUUID: UUID, slot: CostumeSlot? = null) {
        if (slot == null) {
            playerCostumes.remove(playerUUID)
            playerRenderers.remove(playerUUID)
            playerAnimatables.remove(playerUUID)
        } else {
            playerCostumes[playerUUID]?.remove(slot)
            playerRenderers[playerUUID]?.remove(slot)
            playerAnimatables[playerUUID]?.remove(slot)
        }
    }

    fun getItemRenderer(costumeId: String): CostumeItemRenderer? {
        val wrapper = costumeDefinitions[costumeId] ?: return null
        return itemRenderers.computeIfAbsent(costumeId) { CostumeItemRenderer(wrapper) }
    }

    fun renderForPlayer(
        entity: Entity,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        partialTick: Float,
        boneSnapshot: PlayerBoneSnapshot
    ) {
        val slots = playerCostumes[entity.uuid] ?: return
        val renderers = playerRenderers.computeIfAbsent(entity.uuid) { ConcurrentHashMap() }
        val animatables = playerAnimatables.computeIfAbsent(entity.uuid) { ConcurrentHashMap() }

        slots.forEach { (slot, costumeId) ->
            if (brokenCostumes.contains(costumeId)) {
                return@forEach
            }
            val wrapper = costumeDefinitions[costumeId] ?: return@forEach
            if (!isCostumeReady(costumeId, wrapper)) {
                return@forEach
            }

            val animatable = animatables.computeIfAbsent(slot) { CostumeAnimatable(wrapper.hostDriven) }
            val renderer = renderers.computeIfAbsent(slot) { CostumeRenderer(wrapper, animatable) }
            renderer.currentBoneSnapshot = boneSnapshot
            val depth = PoseStackDepth.capture(poseStack)
            try {
                renderer.render(entity, poseStack, bufferSource, packedLight, partialTick)
            } catch (ex: RuntimeException) {
                PoseStackDepth.restore(poseStack, depth)
                brokenCostumes.add(costumeId)
                renderers.remove(slot)
                animatables.remove(slot)
                Lantern.logger.error("[Lantern] Disabled broken costume renderer: {}", costumeId, ex)
            }
        }
    }

    /** 手持外观物品：没就绪时调用方走原版物品渲染 */
    @JvmStatic
    fun isItemReady(costumeId: String): Boolean {
        val wrapper = costumeDefinitions[costumeId] ?: return false
        if (brokenCostumes.contains(costumeId) || !isCostumeReady(costumeId, wrapper)) return false
        return LanternAssets.isTextureReady(wrapper.textureLocation, wrapper.textureUrl)
    }

    /**
     * 资源还在后台加载（AssetSync.isLoading）时缺文件只返回 false，不记成损坏：
     * 加载完成后 refreshRenderers 之前记下的损坏标记会一直挡住渲染
     */
    private fun isCostumeReady(costumeId: String, wrapper: CostumeModelWrapper): Boolean {
        if (!validatedCostumes.contains(costumeId)) {
            if (!LanternAssets.hasResource(wrapper.modelLocation)) {
                if (AssetSync.isLoading) return false
                brokenCostumes.add(costumeId)
                Lantern.logger.warn("[Lantern] Costume model resource missing: {} ({})", wrapper.modelLocation, costumeId)
                return false
            }
            // hostDriven 外观画的是玩家自己的皮肤，配置里的贴图路径不参与渲染
            if (!wrapper.hostDriven && wrapper.textureUrl == null && !LanternAssets.hasResource(wrapper.textureLocation)) {
                if (AssetSync.isLoading) return false
                brokenCostumes.add(costumeId)
                Lantern.logger.warn("[Lantern] Costume texture resource missing: {} ({})", wrapper.textureLocation, costumeId)
                return false
            }
            validatedCostumes.add(costumeId)
        }

        if (GeckoLibCache.getBakedModels()[wrapper.modelLocation] == null) {
            if (!AssetSync.isLoading && deferredModels.add(wrapper.modelLocation)) {
                Lantern.logger.warn("[Lantern] Costume model not yet cached, deferring render: {}", wrapper.modelLocation)
            }
            return false
        }
        return true
    }

    private object PoseStackDepth {
        fun capture(poseStack: PoseStack): Int {
            return (poseStack as PoseStackAccessor).`lantern$getPoseStack`().size
        }

        fun restore(poseStack: PoseStack, depth: Int) {
            val deque = (poseStack as PoseStackAccessor).`lantern$getPoseStack`()
            while (deque.size > depth) {
                poseStack.popPose()
            }
        }
    }

    /**
     * 实体离场时淘汰该玩家的渲染器缓存，外观分配保留：服务端只在分配变化时广播，
     * 玩家走出视距再回来不会重发，删了分配外观就丢了
     */
    fun evictRenderers(playerUUID: UUID) {
        playerRenderers.remove(playerUUID)
        playerAnimatables.remove(playerUUID)
    }
}
