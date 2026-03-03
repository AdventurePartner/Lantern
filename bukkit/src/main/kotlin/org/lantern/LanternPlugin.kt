package org.lantern

import com.aystudio.core.bukkit.plugin.AyPlugin
import org.bukkit.Bukkit
import org.lantern.channel.LanternChannelMessageListener
import org.lantern.command.LanternCommand
import org.lantern.config.Configurations
import org.lantern.listen.PlayerListener

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
        // 注册命令
        getCommand("lantern")?.let {
            val cmd = LanternCommand()
            it.setExecutor(cmd)
            it.tabCompleter = cmd
        }
    }
}