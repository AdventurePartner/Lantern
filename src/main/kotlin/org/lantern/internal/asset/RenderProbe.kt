package org.lantern.internal.asset

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * 渲染入口各分支的命中次数。回退原版的分支都不打日志，排查「某台设备上全画原版」时
 * 由 RenderReadinessReport 汇总输出；只累加，不影响渲染结果
 */
object RenderProbe {

    private val counters = ConcurrentHashMap<String, LongAdder>()

    @JvmStatic
    fun hit(key: String) {
        counters.computeIfAbsent(key) { LongAdder() }.increment()
    }

    /** 读出自上次读取以来的计数并清零，按名字排序，略去 0 */
    fun drain(): Map<String, Long> =
        counters.keys.sorted()
            .associateWith { counters[it]?.sumThenReset() ?: 0L }
            .filterValues { it > 0 }
}
