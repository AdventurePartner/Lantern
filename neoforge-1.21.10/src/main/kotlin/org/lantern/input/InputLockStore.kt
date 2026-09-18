package org.lantern.input

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家输入锁（客户端，packet 21）。
 *
 * 压制点必须在客户端：输入产生于客户端，玩家按住 W 时服务端能做的只有把人
 * 拽回去，那是橡皮筋不是锁。这里在输入被消费之前就把对应的位清掉，
 * 玩家按了也等于没按——移动、跳跃、视角三路各自的钩子见
 * [org.lantern.platform.NeoForgeClientEvents] 与 MouseHandlerMixin。
 *
 * 多来源按 id 分别记账、并集生效：受击硬直与施法前摇可能同时存在，
 * 任一方到期不该把另一方一起解掉
 */
object InputLockStore {

    private class LockSet(val locks: Set<String>, val expireAtMs: Long)

    private val locks = ConcurrentHashMap<String, LockSet>()

    /**
     * 每帧都要查的并集结果缓存。
     *
     * 渲染帧率下 activeLocks 一秒会被问上百次，每次都去遍历 map 求并集是白烧 CPU；
     * 只在表变动或最近一条到期时重算
     */
    @Volatile
    private var cachedUnion: Set<String> = emptySet()

    /** 缓存有效期：取全部锁里最早的到期时刻，过了就必须重算 */
    @Volatile
    private var cacheValidUntilMs: Long = 0L

    /**
     * packet 21：clear=true 即解锁，否则加锁。
     *
     * 写入调度到主线程：activeLocks 的并集缓存在主线程重算，收包若在别的线程写表，
     * 「判空 → 写 MAX_VALUE 有效期」中间插进一条新锁就会把 invalidate 覆盖掉，
     * 新锁要等下一条包才可见。全部落在同一线程上就没有这个窗口
     */
    fun handle(obj: JsonObject) {
        val client = net.minecraft.client.Minecraft.getInstance()
        if (!client.isSameThread) {
            client.execute { handle(obj) }
            return
        }
        if (obj.get("clear")?.asBoolean == true) {
            val id = obj.get("id")?.asString
            if (id == null) locks.clear() else locks.remove(id)
            invalidate()
            return
        }
        val id = obj.get("id")?.asString ?: return
        val durationMs = obj.get("durationMs")?.asLong ?: 0L
        if (durationMs <= 0) return
        val array = obj.getAsJsonArray("locks") ?: return
        val parsed = LinkedHashSet<String>()
        for (element in array) {
            if (element.isJsonPrimitive) parsed.add(element.asString.lowercase())
        }
        if (parsed.isEmpty()) return
        locks[id] = LockSet(parsed, System.currentTimeMillis() + durationMs)
        invalidate()
    }

    /** 此刻生效的动作并集（已剔除过期项） */
    @JvmStatic
    fun activeLocks(): Set<String> {
        val now = System.currentTimeMillis()
        if (now < cacheValidUntilMs) return cachedUnion
        if (locks.isEmpty()) {
            cachedUnion = emptySet()
            // 空表不需要重算，把有效期推远；下一次 handle 会 invalidate
            cacheValidUntilMs = Long.MAX_VALUE
            return emptySet()
        }
        locks.entries.removeIf { it.value.expireAtMs <= now }
        val union = LinkedHashSet<String>()
        var earliest = Long.MAX_VALUE
        locks.values.forEach { set ->
            union.addAll(set.locks)
            if (set.expireAtMs < earliest) earliest = set.expireAtMs
        }
        cachedUnion = union
        cacheValidUntilMs = earliest
        return union
    }

    @JvmStatic
    fun isLocked(action: String): Boolean = action in activeLocks()

    @JvmStatic
    fun clear() {
        locks.clear()
        invalidate()
    }

    private fun invalidate() {
        cacheValidUntilMs = 0L
    }
}
