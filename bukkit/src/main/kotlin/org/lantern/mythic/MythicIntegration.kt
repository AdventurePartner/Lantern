package org.lantern.mythic

import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.skills.CustomComponentRegistry
import io.lumine.mythic.core.skills.CustomComponentRegistry.MythicComponentType
import io.lumine.mythic.core.skills.mechanics.CustomMechanic
import org.lantern.LanternPlugin

/**
 * MythicMobs 集成入口。仅在服务端安装了 MythicMobs 时由插件主类调用，
 * 未安装时本对象不会被加载，全部 MM 引用都隔离在 org.lantern.mythic 包内。
 */
object MythicIntegration {

    /**
     * MM 机制的主线程落点。技能可能在异步线程执行，必须回主线程；但已经在主线程时
     * 直接跑——无条件 runTask 会把执行推后一 tick，恰好让每秒一次的换组巡检
     * （同为定时任务、创建更早、同 tick 先跑）抢在锁与播控落表之前
     */
    inline fun onMainThread(crossinline block: () -> Unit) {
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            block()
        } else {
            org.bukkit.Bukkit.getScheduler().runTask(LanternPlugin.instance, Runnable { block() })
        }
    }

    fun register(plugin: LanternPlugin) {
        CustomComponentRegistry(plugin, listOf("org.lantern.mythic"))
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanternanim")
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanternvar")
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanterncam")
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanternlock")
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanternbind")

        // registerCustomComponent 内部失败只打 WARN 不抛异常，必须回查确认机制真正挂载
        for (name in listOf("lanternanim", "lanternvar", "lanterncam", "lanternlock", "lanternbind")) {
            val mechanic = MythicBukkit.inst().skillManager
                .getMechanic(name)
                ?: MythicBukkit.inst().skillManager.getMechanic(name.uppercase())
            val loaded = (mechanic as? CustomMechanic)?.mechanic?.isPresent == true
            if (!loaded) {
                throw IllegalStateException(
                    "$name mechanic did not attach (check MythicMobs log above for the cause)"
                )
            }
        }
        plugin.logger.info(
            "MythicMobs integration enabled (mechanics: lanternanim / lanim, lanternvar / lvar, " +
                "lanterncam, lanternlock / llock, lanternbind / lbind)"
        )
    }
}
