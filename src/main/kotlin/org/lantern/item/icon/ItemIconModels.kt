package org.lantern.item.icon

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.lantern.internal.asset.RenderProbe
import org.lantern.internal.icon.IconVertexEmitter
import org.lantern.internal.icon.ItemIconMeshes
import java.util.concurrent.ConcurrentHashMap

/**
 * 运行时物品图标（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1）：
 * ItemRenderer.getModel 换成占位模型，ItemRenderer.render 开头自己画网格。
 * 1.21.10 走 ItemModel 体系，在 LanternItemModels 里另有实现
 */
object ItemIconModels {

    /** 诊断用：最近经过物品挂钩的 CustomModelData，最多记 64 个 */
    private val seen = ConcurrentHashMap.newKeySet<Int>()

    fun drainSeen(): List<Int> = seen.sorted().also { seen.clear() }

    /** 图标网格已生成时返回占位模型，否则 null（调用方继续走烘焙模型或原版物品） */
    @JvmStatic
    fun model(customModelData: Int, stack: ItemStack): BakedModel? {
        if (seen.size < 64) seen.add(customModelData)
        val mesh = ItemIconMeshes.get(customModelData) ?: run {
            RenderProbe.hit("icon.no_mesh")
            return null
        }
        RenderProbe.hit("icon.mesh")
        (mesh.platformModel as? LanternIconBakedModel)?.let { return it }
        // 粒子（吃东西、工具损坏）沿用原物品模型的贴图：图标贴图不在方块图集里
        val particle = Minecraft.getInstance().itemRenderer.itemModelShaper.getItemModel(stack).particleIcon
        return LanternIconBakedModel(mesh, particle).also { mesh.platformModel = it }
    }

    /** ItemRenderer.render 开头调用：是占位模型就按原版 generated 的变换画完并返回 true */
    @JvmStatic
    fun render(
        model: BakedModel,
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        leftHand: Boolean,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ): Boolean {
        if (model !is LanternIconBakedModel) return false
        RenderProbe.hit("icon.drawn")
        poseStack.pushPose()
        model.transforms.getTransform(displayContext).apply(leftHand, poseStack)
        poseStack.translate(-0.5f, -0.5f, -0.5f)
        // itemEntityTranslucentCull 在「极佳」画质下写进物品实体渲染目标，GUI 里会看不见
        val renderType = RenderType.entityTranslucentCull(model.mesh.texture)
        val consumer = ItemRenderer.getFoilBufferDirect(buffer, renderType, true, stack.hasFoil())
        IconVertexEmitter.emit(poseStack.last(), consumer, model.mesh, light, overlay)
        poseStack.popPose()
        return true
    }
}
