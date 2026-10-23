package org.lantern.internal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.lantern.costume.armor.LanternArmorModel;
import org.lantern.costume.handler.CostumeHandler;
import org.lantern.costume.renderstate.CostumeRenderData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P1 盔甲适配：hostDriven 整替玩家的盔甲改用 Lantern 骨架版模型渲染。
 *
 * 只替换模型实例，提交仍走原版 EquipmentLayerRenderer.renderLayers——纹理桶、
 * 染色、纹饰 trim、附魔光、发光轮廓全部原样继承。原版四件盔甲模型是
 * AvatarRenderer 全局共享的 PlayerModel，其 setupAnim 会 resetPose 全量覆盖，
 * 所以不能往它身上写姿态；LanternArmorModel 自持部件树并覆写 setupAnim，
 * 写入点落在 ModelFeatureRenderer 的 renderToBuffer 前一步。
 *
 * 守卫按玩家宿主化状态判定：普通生物的 renderState 不是 AvatarRenderState，
 * 非宿主化玩家取不到 hostDriven 整替外观，两者都原样走原版路径。
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Shadow
    @Final
    private EquipmentLayerRenderer equipmentRenderer;

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void lantern$hostDrivenArmorPiece(
        PoseStack poseStack,
        SubmitNodeCollector submitNodes,
        ItemStack stack,
        EquipmentSlot slot,
        int packedLight,
        HumanoidRenderState renderState,
        CallbackInfo ci
    ) {
        if (!(renderState instanceof AvatarRenderState)) return;
        UUID playerId = renderState.getRenderData(CostumeRenderData.PLAYER_UUID);
        if (playerId == null || !CostumeHandler.hasHostDrivenFullBody(playerId)) return;

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        // 与原版 shouldRender 同判据：无装备资产的物品（南瓜、头颅等）走 CustomHeadLayer，不接管
        if (equippable == null || equippable.assetId().isEmpty() || equippable.slot() != slot) return;

        EquipmentClientInfo.LayerType layerType = slot == EquipmentSlot.LEGS
            ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
            : EquipmentClientInfo.LayerType.HUMANOID;

        this.equipmentRenderer.renderLayers(
            layerType,
            equippable.assetId().orElseThrow(),
            LanternArmorModel.of(slot),
            renderState,
            stack,
            poseStack,
            submitNodes,
            packedLight,
            renderState.outlineColor
        );
        ci.cancel();
    }
}
