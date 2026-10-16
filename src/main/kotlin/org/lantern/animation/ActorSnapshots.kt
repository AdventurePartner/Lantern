package org.lantern.animation

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.HoeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.PickaxeItem
import net.minecraft.world.item.ShieldItem
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.item.SwordItem
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.UseAnim
import org.lantern.core.anim.ActorSnapshot
import org.lantern.core.anim.UseActionKind

/**
 * ActorSnapshot 的平台采集器（1.21.1 / 1.20.1）：每帧从实体把播放器判定链要读的全部量
 * 抽出一份快照。实体 API 读取集中在这里，common-core 的动画内核不接触 Minecraft 类型。
 * 1.21.10 有同名副本：那边物品使用动画改成了 ItemUseAnimation、剑和镐改成了数据组件
 */
object ActorSnapshots {

    fun capture(entity: Entity): ActorSnapshot {
        val living = entity as? LivingEntity
        val player = entity as? Player
        val level = entity.level()
        return ActorSnapshot(
            x = entity.x,
            y = entity.y,
            z = entity.z,
            physicallyGrounded = entity.onGround(),
            living = living != null,
            climbing = living?.onClimbable() == true,
            flying = player?.abilities?.flying == true,
            dead = living?.isDeadOrDying == true,
            inWater = living?.isInWater == true,
            passenger = living?.isPassenger == true,
            sneaking = living?.isShiftKeyDown == true,
            sprinting = living?.isSprinting == true,
            swinging = living?.swinging == true,
            attackAnim = living?.attackAnim ?: 0f,
            usingItem = living?.isUsingItem == true,
            usingMainHand = living?.usedItemHand == InteractionHand.MAIN_HAND,
            useAction = useActionOf(living),
            mainHandHold = HoldItems.typeOf(living?.mainHandItem),
            tickCount = entity.tickCount,
            gameTime = level.gameTime,
            dayTime = level.dayTime,
            alive = living?.isAlive == true,
            health = living?.health ?: 0f,
            maxHealth = living?.maxHealth ?: 0f,
            hurtTime = living?.hurtTime ?: 0,
            baby = living?.isBaby == true,
            scale = living?.scale ?: 1f,
            pitch = living?.xRot ?: 0f,
            yaw = living?.yRot ?: 0f,
            bodyYaw = living?.yBodyRot ?: 0f,
            yawSpeed = living?.let { it.yRot - it.yRotO } ?: 0f
        )
    }

    private fun useActionOf(living: LivingEntity?): UseActionKind =
        when (living?.useItem?.useAnimation) {
            UseAnim.BOW -> UseActionKind.BOW
            UseAnim.CROSSBOW -> UseActionKind.CROSSBOW
            UseAnim.BLOCK -> UseActionKind.BLOCK
            UseAnim.EAT -> UseActionKind.EAT
            UseAnim.DRINK -> UseActionKind.DRINK
            else -> UseActionKind.NONE
        }
}

/**
 * 主手物品类型 -> hold_* 状态名（持物待机姿态）。专属武器在前；
 * 重锤（1.21 起）按注册名判断，1.20.1 没有这个物品也不会误判
 */
private object HoldItems {
    private val classes: List<Pair<String, Class<out Item>>> = listOf(
        "trident" to TridentItem::class.java,
        "sword" to SwordItem::class.java,
        "axe" to AxeItem::class.java,
        "pickaxe" to PickaxeItem::class.java,
        "shovel" to ShovelItem::class.java,
        "hoe" to HoeItem::class.java,
        "bow" to BowItem::class.java,
        "crossbow" to CrossbowItem::class.java,
        "shield" to ShieldItem::class.java
    )

    fun typeOf(stack: ItemStack?): String? {
        if (stack == null || stack.isEmpty) return null
        val item = stack.item
        val key = BuiltInRegistries.ITEM.getKey(item)
        if (key.namespace == "minecraft" && key.path == "mace") return "mace"
        for ((name, cls) in classes) if (cls.isInstance(item)) return name
        return null
    }
}
