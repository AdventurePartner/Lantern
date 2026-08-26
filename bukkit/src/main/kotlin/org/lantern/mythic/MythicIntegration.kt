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

    fun register(plugin: LanternPlugin) {
        CustomComponentRegistry(plugin, listOf("org.lantern.mythic"))
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanternanim")
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanternvar")
            .registerCustomComponent(MythicComponentType.MECHANIC, "lanterncam")

        // registerCustomComponent 内部失败只打 WARN 不抛异常，必须回查确认机制真正挂载
        for (name in listOf("lanternanim", "lanternvar", "lanterncam")) {
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
        plugin.logger.info("MythicMobs integration enabled (mechanics: lanternanim / lanim, lanternvar / lvar, lanterncam)")
    }
}
