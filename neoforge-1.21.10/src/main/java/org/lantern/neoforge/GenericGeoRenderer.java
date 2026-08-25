package org.lantern.model.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.lantern.Lantern;
import org.lantern.animation.AnimationHost;
import org.lantern.model.GeckoResourceIds;
import org.lantern.model.entity.GenericReplacedEntity;
import org.lantern.model.geo.GenericGeoModel;
import org.lantern.model.renderstate.AnimationControlStore;
import org.lantern.model.renderstate.LanternDataTickets;
import org.lantern.model.renderstate.ReplacedRenderData;
import org.lantern.model.wrapper.CustomModelWrapper;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class GenericGeoRenderer<R extends EntityRenderState & GeoRenderState>
    extends GeoReplacedEntityRenderer<GenericReplacedEntity, Entity, R> {
    private static final Set<net.minecraft.resources.ResourceLocation> WARNED_MODELS =
        ConcurrentHashMap.newKeySet();

    private final String rendererKey;
    private final CustomModelWrapper wrapper;
    private final GenericGeoModel geoModel;
    private final net.minecraft.resources.ResourceLocation modelId;

    private GenericGeoRenderer(
        EntityRendererProvider.Context context,
        EntityType<?> entityType,
        String rendererKey,
        CustomModelWrapper wrapper
    ) {
        super(context, new GenericGeoModel(wrapper), new GenericReplacedEntity(entityType));
        this.geoModel = (GenericGeoModel) super.getGeoModel();
        this.rendererKey = rendererKey;
        this.wrapper = wrapper;
        this.modelId = GeckoResourceIds.model(wrapper.getModelLocation());
        withScale(wrapper.getScale());
    }

    public static GenericGeoRenderer<?> create(
        EntityRendererProvider.Context context,
        EntityType<?> entityType,
        String rendererKey,
        CustomModelWrapper wrapper
    ) {
        return new GenericGeoRenderer<>(context, entityType, rendererKey, wrapper);
    }

    @Override
    protected float getDeathMaxRotation(software.bernie.geckolib.renderer.base.GeoRenderState renderState) {
        if (wrapper.getAnimationStates().getDeath() != null) {
            return 0.0F;
        }
        return super.getDeathMaxRotation(renderState);
    }

    /**
     * 死亡动画持有末帧期间抑制受击/死亡红闪：GeckoLib 默认按 deathTime > 0 打红，
     * 真击杀后的 20 tick 尸体期会在倒地模型上闪一层红色。仅死亡抑制，存活期受击红闪保留
     */
    @Override
    public int getPackedOverlay(GenericReplacedEntity animatable, Entity entity, float u, float partialTick) {
        if (wrapper.getAnimationStates().getDeath() != null &&
            entity instanceof LivingEntity livingEntity &&
            livingEntity.isDeadOrDying()
        ) {
            return net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        }
        return super.getPackedOverlay(animatable, entity, u, partialTick);
    }

    @Override
    public void addRenderData(        GenericReplacedEntity animatable,
        Entity entity,
        R renderState,
        float partialTick
    ) {
        // Lantern 自托管动画：本渲染器不注册 GeckoLib 谓词控制器，
        // 骨骼姿势由 AnimationHost 计算。姿势在提取阶段采样存入 per-entity 的
        // DataTicket（提取是并发的，不能写骨骼），渲染阶段（submit，串行）写回骨骼。
        // 骨骼树已由 GenericGeoModel 按渲染器深拷贝隔离，多实体互不覆盖
        var poseMap = AnimationHost.drivePose(entity, wrapper);
        renderState.addGeckolibData(
            LanternDataTickets.REPLACED_ENTITY,
            new ReplacedRenderData(
                rendererKey,
                wrapper.getAnimationStates(),
                entity.getUUID(),
                AnimationControlStore.INSTANCE.get(entity.getUUID()),
                poseMap
            )
        );
        if (renderState.nameTagAttachment != null && wrapper.getNameTagOffsetY() != 0) {
            renderState.nameTagAttachment = renderState.nameTagAttachment.add(
                0,
                wrapper.getNameTagOffsetY(),
                0
            );
        }
    }

    @Override
    public boolean shouldShowName(Entity entity, double distanceToCameraSq) {
        return !wrapper.getHiddenName() && super.shouldShowName(entity, distanceToCameraSq);
    }

    @Override
    public void submit(
        R renderState,
        PoseStack poseStack,
        SubmitNodeCollector submitNodes,
        CameraRenderState cameraState
    ) {
        if (!GeckoLibResources.getBakedModels().containsKey(this.modelId)) {
            if (WARNED_MODELS.add(this.modelId)) {
                Lantern.INSTANCE.getLogger().warn(
                    "[Lantern] Entity model not yet cached, deferring render: {}",
                    this.modelId
                );
            }
            return;
        }
        // 渲染阶段（串行）：从 per-entity DataTicket 读回姿势写入本渲染器私有的克隆骨骼
        if (renderState instanceof software.bernie.geckolib.renderer.base.GeoRenderState geoState) {
            ReplacedRenderData data = geoState.getGeckolibData(LanternDataTickets.REPLACED_ENTITY);
            if (data != null && data.getPose() != null) {
                AnimationHost.applyPose(
                    this.geoModel.getAnimationProcessor(),
                    data.getPose(),
                    this.geoModel.getInitialPose()
                );
            }
        }
        super.submit(renderState, poseStack, submitNodes, cameraState);
    }
}
