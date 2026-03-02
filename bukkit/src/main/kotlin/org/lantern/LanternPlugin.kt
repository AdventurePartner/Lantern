package org.lantern

import com.aystudio.core.bukkit.plugin.AyPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.lantern.channel.LanternChannelMessageListener
import org.lantern.config.Configurations
import org.lantern.listen.PlayerListener
import org.lantern.network.NetworkHandler

class LanternPlugin : AyPlugin() {

    companion object {
        lateinit var instance: LanternPlugin
    }

    override fun onEnable() {
        instance = this
        Configurations.load()
        // 注册监听器
        Bukkit.getPluginManager().registerEvents(PlayerListener(), this)
        // 注册消息通道
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "lantern:main")
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "lantern:main", LanternChannelMessageListener())
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String?>): Boolean {
        Configurations.load()
        Bukkit.getOnlinePlayers().forEach { NetworkHandler.sendPackets(it) }
        sender.sendMessage("${ChatColor.DARK_GREEN}Configuration reloaded.")
        return false
    }
}