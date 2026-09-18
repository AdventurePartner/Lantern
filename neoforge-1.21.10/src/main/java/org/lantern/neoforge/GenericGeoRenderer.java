package org.lantern.model.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.lantern.Lantern;
import net.minecraft.world.phys.AABB;
import org.lantern.animation.AnimationHost;
import org.lantern.bind.BindStore;
import org.lantern.model.GeckoResourceIds;
import org.lantern.model.renderstate.BindTransform;
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
    /**
     * 首次渲染回执，按模型条目名去重。
     *
     * 「特效没出来」有两种完全不同的原因：实体没进渲染链（换模没匹配上），
     * 或者进了但播控没到。少了这一条就分不开，只能靠猜
     */
    private static final Set<String> ENGAGED_KEYS = ConcurrentHashMap.newKeySet();

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
        if (ENGAGED_KEYS.add(this.rendererKey)) {
            Lantern.INSTANCE.getLogger().info(
                "[Lantern] 实体模型接管渲染: 条目='{}' 首个实体={}", this.rendererKey, entity.getUUID()
            );
        }
        var poseMap = AnimationHost.drivePose(entity, wrapper);
        renderState.addGeckolibData(
            LanternDataTickets.REPLACED_ENTITY,
            new ReplacedRenderData(
                rendererKey,
                wrapper.getAnimationStates(),
                entity.getUUID(),
                AnimationControlStore.INSTANCE.get(entity.getUUID()),
                poseMap,
                resolveBindTransform(entity, partialTick)
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

    /**
     * 载体绑定的渲染重定向量。
     *
     * 位置一律取插值位置（Mth.lerp(partialTick, xOld, getX())），与原版
     * EntityRenderer#extractRenderState 取的是同一个口径。混用 tick 位置和插值位置
     * 会让载体相对宿主每帧抖一下——两者的时间基准差了最多一个 tick。
     *
     * 宿主在本客户端不存在（未加载/已退出）时返回 null：按载体自身位置渲染，
     * 不做重定向（visible=false 的条目会在 shouldRender 里被整个跳过）。
     *
     * 提取阶段可能并发执行，这里只做只读查表，不写任何共享状态
     */
    private BindTransform resolveBindTransform(Entity entity, float partialTick) {
        BindStore.Bind bind = BindStore.get(entity.getUUID());
        if (bind == null) {
            return null;
        }
        Entity host = entity.level().getEntity(bind.getHost());
        if (host == null) {
            return null;
        }
        float hostYaw = hostBodyYaw(host, partialTick);

        double offsetX = bind.getOffsetX();
        double offsetY = bind.getOffsetY();
        double offsetZ = bind.getOffsetZ();
        if (bind.getRotate()) {
            // 局部坐标 -> 世界坐标。MC 的 yaw 0 面朝 +Z（南）：
            // 前向 = (-sin, cos)，右向 = (-cos, -sin)。约定 x=右 y=上 z=前
            double yawRad = Math.toRadians(hostYaw);
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double rightX = -Math.cos(yawRad);
            double rightZ = -Math.sin(yawRad);
            double localX = offsetX;
            double localZ = offsetZ;
            offsetX = localX * rightX + localZ * forwardX;
            offsetZ = localX * rightZ + localZ * forwardZ;
        }

        double followerX = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double followerY = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double followerZ = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        double hostX = Mth.lerp(partialTick, host.xOld, host.getX());
        double hostY = Mth.lerp(partialTick, host.yOld, host.getY());
        double hostZ = Mth.lerp(partialTick, host.zOld, host.getZ());

        return new BindTransform(
            hostX + offsetX - followerX,
            hostY + offsetY - followerY,
            hostZ + offsetZ - followerZ,
            bind.getRotate() ? Float.valueOf(hostYaw) : null
        );
    }

    /** 宿主的身体朝向（插值）。生物取 yBodyRot，其余实体没有身体朝向的概念，取 yRot */
    private static float hostBodyYaw(Entity host, float partialTick) {
        if (host instanceof LivingEntity living) {
            return Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        }
        return Mth.rotLerp(partialTick, host.yRotO, host.getYRot());
    }

    /**
     * 视锥剔除按宿主判定。
     *
     * 载体的服务端坐标停在召唤点不动，画出来的却是宿主的位置——用载体自己的
     * 包围盒做剔除，宿主还在画面正中央时载体就已经被剔掉了。visible=true 时
     * 直接跳过剔除（宿主被墙挡住或转出视野也照渲染）。
     *
     * 宿主在本客户端不存在时：visible=true 按载体原位渲染，visible=false 不渲染——
     * 否则载体会突然弹回召唤点，比不画更难看
     */
    @Override
    public boolean shouldRender(Entity entity, Frustum frustum, double camX, double camY, double camZ) {
        BindStore.Bind bind = BindStore.get(entity.getUUID());
        if (bind == null) {
            return super.shouldRender(entity, frustum, camX, camY, camZ);
        }
        if (bind.getVisible()) {
            return true;
        }
        Entity host = entity.level().getEntity(bind.getHost());
        if (host == null) {
            return false;
        }
        double slack = 1.0 + Math.abs(bind.getOffsetX()) + Math.abs(bind.getOffsetY()) + Math.abs(bind.getOffsetZ());
        AABB hostBox = host.getBoundingBox().inflate(slack);
        return frustum.isVisible(hostBox);
    }

    /**
     * rotate=true 时用宿主的身体朝向替换载体自己的朝向，载体整体跟着宿主转。
     *
     * 直接替换而不是在外层叠一个差值旋转：GeckoLib 的 applyRotations 之后还有
     * 死亡倒地、睡眠、旋风斩这些姿态分支，对一个挂件载体全都不适用，
     * 一并跳掉比先叠加再抵消干净
     */
    @Override
    protected void applyRotations(R renderState, PoseStack poseStack, float nativeScale) {
        ReplacedRenderData data = renderState.getGeckolibData(LanternDataTickets.REPLACED_ENTITY);
        if (data != null && data.getBindTransform() != null && data.getBindTransform().getHostBodyYaw() != null) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - data.getBindTransform().getHostBodyYaw()));
            return;
        }
        super.applyRotations(renderState, poseStack, nativeScale);
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
        BindTransform bind = null;
        if (renderState instanceof software.bernie.geckolib.renderer.base.GeoRenderState geoState) {
            ReplacedRenderData data = geoState.getGeckolibData(LanternDataTickets.REPLACED_ENTITY);
            if (data != null && data.getPose() != null) {
                AnimationHost.applyPose(
                    this.geoModel.getAnimationProcessor(),
                    data.getPose(),
                    this.geoModel.getInitialPose()
                );
            }
            if (data != null) {
                bind = data.getBindTransform();
            }
        }
        if (bind == null) {
            super.submit(renderState, poseStack, submitNodes, cameraState);
            return;
        }
        // 载体绑定：此刻 poseStack 原点在载体自身位置，平移到宿主位置再交给 GeckoLib。
        // 服务端实体坐标一点没动，纯渲染层重定向。
        //
        // 必须 push/pop 包住：调用方 EntityRenderDispatcher#submit 在本方法返回后
        // 还要用同一层矩阵提交火焰、影子与碰撞箱，平移漏出去会把它们一起挪到宿主脚下
        poseStack.pushPose();
        try {
            poseStack.translate(bind.getDx(), bind.getDy(), bind.getDz());
            super.submit(renderState, poseStack, submitNodes, cameraState);
        } finally {
            poseStack.popPose();
        }
    }
}
