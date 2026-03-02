package org.lantern.util

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import java.util.logging.Level

object PlayerUtils {

    fun Player.executeCommands(commands: List<String>) {
        val finalCommands = commands.map { it.replace("%player%", this.name) }
        finalCommands.filter { it.startsWith("player:") }
            .map { it.substring(7) }
            .forEach(this::performCommand)
        finalCommands.filter { it.startsWith("console:") }
            .map { it.substring(8) }
            .forEach { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), it) }
        finalCommands.filter { it.startsWith("op:") }
            .map { it.substring(3) }
            .takeIf { it.isNotEmpty() }
            ?.let {
                val isOp = this.isOp
                try {
                    this.isOp = true
                    it.forEach(this::performCommand)
                } catch (e: Exception) {
                    LanternPlugin.instance.logger.log(Level.WARNING, e) { "Failed to execute command." }
                } finally {
                    this.isOp = isOp
                }
            }
    }
}