package org.lantern.model.handler

import java.util.UUID
import net.minecraft.client.Minecraft
import org.lantern.Lantern
import org.lantern.model.GeckoResourceIds
import org.lantern.model.renderstate.AnimationControlStore
import software.bernie.geckolib.cache.GeckoLibResources

/**
 * packetId 15 动画播控入口：校验目标实体确由 Lantern 实体模型渲染后，写入强制动画存储。
 * 在共享 NetworkParser 的钩子上注册，仅 neoforge 1.21.10 实现了完整链路。
 */
object AnimationControlHandler {

    /** once 模式查不到动画时长时的兜底持有上限，防止强制动画永久锁死 */
    private const val FALLBACK_ONCE_MAX_MS = 10_000L
    private const val EXPIRY_MARGIN_MS = 300L

    fun handle(uuid: UUID, play: Boolean, animation: String, transition: Int, loop: Boolean, speed: Float) {
        if (!play) {
            AnimationControlStore.stop(uuid, animation)
            return
        }
        val level = Minecraft.getInstance().level ?: return
        val entity = level.getEntity(uuid) ?: return
        val name = entity.customName?.string ?: return
        val wrapper = RendererHandler.getCustomModelWrapper(name) ?: run {
            Lantern.logger.debug(
                "[Lantern] Animation target '{}' is not a Lantern entity model, ignored",
                name
            )
            return
        }
        val safeSpeed = if (speed > 0.01f) speed else 1.0f
        val expiresAtMs = if (loop) {
            0L
        } else {
            val lengthMs = resolveAnimationLengthMs(wrapper.animationLocation, animation)
            Lantern.logger.info(
                "[Lantern][AnimDiag] play '{}' loop={} length={} -> expires in {}ms",
                animation, loop, lengthMs,
                if (lengthMs != null) (lengthMs / safeSpeed).toLong() + transition * 50L + EXPIRY_MARGIN_MS else FALLBACK_ONCE_MAX_MS
            )
            if (lengthMs != null) {
                System.currentTimeMillis() + (lengthMs / safeSpeed).toLong() + transition * 50L + EXPIRY_MARGIN_MS
            } else {
                Lantern.logger.debug(
                    "[Lantern] Animation '{}' length unknown for '{}', using fallback expiry",
                    animation, name
                )
                System.currentTimeMillis() + FALLBACK_ONCE_MAX_MS
            }
        }
        AnimationControlStore.play(uuid, animation, transition, loop, safeSpeed, expiresAtMs)
    }

    private fun resolveAnimationLengthMs(
        animationLocation: net.minecraft.resources.ResourceLocation,
        animation: String
    ): Long? {
        val baked = GeckoLibResources.getBakedAnimations()[GeckoResourceIds.animation(animationLocation)] ?: return null
        val length = baked.getAnimation(animation)?.length() ?: return null
        // GeckoLib Animation.length() 单位是 tick（1.75s 动画返回 35），换算毫秒需 ×50
        return (length * 50).toLong()
    }
}
