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
import net.minecraft.world.entity.Mob;
import org.lantern.Lantern;
import org.lantern.animation.AnimationHost;
import org.lantern.model.GeckoResourceIds;
import org.lantern.model.entity.GenericReplacedEntity;
import org.lantern.model.enums.EntityAnimationState;
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
    public void addRenderData(
        GenericReplacedEntity animatable,
        Entity entity,
        R renderState,
        float partialTick
    ) {
        renderState.addGeckolibData(
            LanternDataTickets.REPLACED_ENTITY,
            new ReplacedRenderData(
                rendererKey,
                wrapper.getAnimationStates(),
                detectActionState(entity),
                entity.getUUID(),
                AnimationControlStore.INSTANCE.get(entity.getUUID())
            )
        );
        // Lantern 自托管动画：本渲染器不注册 GeckoLib 谓词控制器，
        // 每帧由 AnimationHost 采样并直写骨骼（唯一写入方）
        AnimationHost.drive(entity, wrapper, this.geoModel.getAnimationProcessor());
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
        net.minecraft.resources.ResourceLocation modelId = GeckoResourceIds.model(wrapper.getModelLocation());
        if (!GeckoLibResources.getBakedModels().containsKey(modelId)) {
            if (WARNED_MODELS.add(modelId)) {
                Lantern.INSTANCE.getLogger().warn(
                    "[Lantern] Entity model not yet cached, deferring render: {}",
                    modelId
                );
            }
            return;
        }
        super.submit(renderState, poseStack, submitNodes, cameraState);
    }

    private static EntityAnimationState detectActionState(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            if (livingEntity.isDeadOrDying()) {
                return EntityAnimationState.DEATH;
            }
            if (livingEntity.hurtTime > 0) {
                return EntityAnimationState.HURT;
            }
        }
        if (entity instanceof Mob mob && mob.isAggressive()) {
            return EntityAnimationState.ATTACK;
        }
        return null;
    }
}
