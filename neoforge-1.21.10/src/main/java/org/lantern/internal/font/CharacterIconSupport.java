package org.lantern.internal.font;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.lantern.internal.handler.TextureHandler;

import java.util.List;

/**
 * characterIcon 在 1.21.10 的渲染支持。
 * 1.21.10 字体渲染拆成两阶段：PreparedTextBuilder.accept 先收集字形，visit(GlyphVisitor)
 * 再提交渲染，自定义贴图无法作为字形进入该管线。因此 accept 阶段记录图标 quad
 * （FontPreparedTextBuilderMixin），由两条消费路径各自发射：
 * - GUI 文本（GuiRenderer.prepareText，聊天/HUD）→ BlitRenderState
 * - 世界文本（Font.drawInBatch，名牌等）→ RenderType.text 批处理
 */
public final class CharacterIconSupport {

    private CharacterIconSupport() {
    }

    /**
     * accept 阶段记录的图标。resource 延迟到发射时解析，
     * 这样 http 贴图异步下载完成后无需重新 prepare 即可生效。
     */
    public record Icon(float x, float y, float width, float height, String resource, int color, int shadowColor) {
    }

    /** duck 接口：由 FontPreparedTextBuilderMixin 织入 Font$PreparedTextBuilder */
    public interface IconSink {
        List<Icon> lantern$getIcons();

        void lantern$clearIcons();
    }

    /** 世界空间文本：与 1.21.1 版 StringRenderOutputMixin 相同的 RenderType.text 批处理路径 */
    public static void emitWorld(
        List<Icon> icons,
        MultiBufferSource bufferSource,
        Matrix4f pose,
        Font.DisplayMode mode,
        int packedLight
    ) {
        for (Icon icon : icons) {
            ResourceLocation location = TextureHandler.INSTANCE.getTexture(icon.resource());
            RenderType renderType = switch (mode) {
                case SEE_THROUGH -> RenderType.textSeeThrough(location);
                case POLYGON_OFFSET -> RenderType.textPolygonOffset(location);
                default -> RenderType.text(location);
            };
            VertexConsumer consumer = bufferSource.getBuffer(renderType);
            lantern$worldQuad(consumer, pose, icon.x(), icon.y(), icon.width(), icon.height(), icon.color(), packedLight);
            if (ARGB.alpha(icon.shadowColor()) != 0) {
                lantern$worldQuad(consumer, pose, icon.x() + 1.0F, icon.y() + 1.0F, icon.width(), icon.height(), icon.shadowColor(), packedLight);
            }
        }
    }

    private static void lantern$worldQuad(
        VertexConsumer consumer, Matrix4f pose, float x, float y, float width, float height, int color, int packedLight
    ) {
        consumer.addVertex(pose, x, y + height, 0.0F).setColor(color).setUv(0.0F, 1.0F).setLight(packedLight);
        consumer.addVertex(pose, x + width, y + height, 0.0F).setColor(color).setUv(1.0F, 1.0F).setLight(packedLight);
        consumer.addVertex(pose, x + width, y, 0.0F).setColor(color).setUv(1.0F, 0.0F).setLight(packedLight);
        consumer.addVertex(pose, x, y, 0.0F).setColor(color).setUv(0.0F, 0.0F).setLight(packedLight);
    }

    /**
     * GUI 文本：图标与 GuiGraphics.blit 相同的 BlitRenderState 提交到当前层。
     * 必须在 GuiRenderState.forEachText 的遍历回调内调用（current 指向文本所属层节点）。
     * 图标不清空——PreparedText 每帧重新 visit，需要重复发射。
     */
    public static void emitGui(GuiTextRenderState textState, GuiRenderState renderState) {
        Font.PreparedText prepared = textState.ensurePrepared();
        if (!(prepared instanceof IconSink sink)) {
            return;
        }
        List<Icon> icons = sink.lantern$getIcons();
        if (icons.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        for (Icon icon : icons) {
            AbstractTexture texture = minecraft.getTextureManager()
                .getTexture(TextureHandler.INSTANCE.getTexture(icon.resource()));
            GpuTextureView view = texture.getTextureView();
            if (ARGB.alpha(icon.shadowColor()) != 0) {
                lantern$submitBlit(renderState, textState, view, icon.x() + 1.0F, icon.y() + 1.0F, icon.width(), icon.height(), icon.shadowColor());
            }
            lantern$submitBlit(renderState, textState, view, icon.x(), icon.y(), icon.width(), icon.height(), icon.color());
        }
    }

    private static void lantern$submitBlit(
        GuiRenderState renderState, GuiTextRenderState textState, GpuTextureView view,
        float x, float y, float width, float height, int color
    ) {
        renderState.submitBlitToCurrentLayer(new BlitRenderState(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(view),
            textState.pose,
            Math.round(x), Math.round(y),
            Math.round(x + width), Math.round(y + height),
            0.0F, 1.0F, 0.0F, 1.0F,
            color,
            textState.scissor
        ));
    }
}
