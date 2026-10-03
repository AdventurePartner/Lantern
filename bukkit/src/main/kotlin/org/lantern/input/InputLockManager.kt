package org.lantern.input

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player
import org.lantern.network.NetworkHandler

/**
 * 玩家输入锁（服务端登记 + 下发）。
 *
 * 受击硬直、施法前摇、演出期间不许乱跑，靠的都是这一层：把玩家的某几路输入
 * 在一段时间内压住。压制点在客户端（输入本来就在客户端产生），服务端只负责
 * 「谁、锁什么、锁多久」并把指令发下去。
 *
 * 多来源按 id 分别记账、并集生效：受击硬直和施法前摇可能同时存在，
 * 任一方到期不应该把另一方一起解掉。到期由客户端按 durationMs 自行判定，
 * 服务端这张表只用于查询、重连补发与退服清理
 */
object InputLockManager {

    /** 支持的动作集。move/jump/sneak/turn 对齐常见用法，attack/use 是同成本的顺手扩展 */
    val ACTIONS = setOf("move", "jump", "sneak", "turn", "attack", "use")

    data class LockSet(val locks: Set<String>, val expireAtMs: Long)

    private val locks = ConcurrentHashMap<UUID, ConcurrentHashMap<String, LockSet>>()

    /**
     * 施加输入锁。
     *
     * @param id 来源标识。同一来源重复调用即刷新，不会叠成两条
     * @param actions 动作名集合，未识别的名字会被剔除
     * @param durationMs 持续毫秒
     * @return 实际生效的动作集；为空表示参数没有一个合法，未下发
     */
    fun lock(player: Player, id: String, actions: Collection<String>, durationMs: Long): Set<String> {
        val valid = actions.map { it.trim().lowercase() }.filter { it in ACTIONS }.toSet()
        if (valid.isEmpty() || durationMs <= 0) return emptySet()
        val table = locks.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
        table[id] = LockSet(valid, System.currentTimeMillis() + durationMs)
        purge(table)
        NetworkHandler.sendInputLock(player, id, valid, durationMs)
        return valid
    }

    /** 解锁：id 为 null 时清除该玩家全部来源 */
    fun clear(player: Player, id: String? = null) {
        val table = locks[player.uniqueId]
        if (id == null) {
            locks.remove(player.uniqueId)
        } else {
            table?.remove(id)
        }
        NetworkHandler.sendInputUnlock(player, id)
    }

    /** 该玩家此刻被锁住的动作并集（已剔除过期项） */
    fun activeLocks(uuid: UUID): Set<String> {
        val table = locks[uuid] ?: return emptySet()
        purge(table)
        if (table.isEmpty()) {
            locks.remove(uuid, table)
            return emptySet()
        }
        val union = HashSet<String>()
        table.values.forEach { union.addAll(it.locks) }
        return union
    }

    fun isLocked(uuid: UUID, action: String): Boolean = action in activeLocks(uuid)

    /** 退服清理：锁是会话内状态，不跨登录保留 */
    fun forget(uuid: UUID) {
        locks.remove(uuid)
    }

    fun reset() {
        locks.clear()
    }

    private fun purge(table: ConcurrentHashMap<String, LockSet>) {
        val now = System.currentTimeMillis()
        table.entries.removeIf { it.value.expireAtMs <= now }
    }
}
