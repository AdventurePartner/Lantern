package org.lantern

import com.aystudio.core.bukkit.plugin.AyPlugin
import org.bukkit.Bukkit
import org.lantern.channel.LanternChannelMessageListener
import org.lantern.command.LanternCommand
import org.lantern.config.Configurations
import org.lantern.handler.CustomBlockTracker
import org.lantern.handler.CostumeAssignmentHandler
import org.lantern.listen.BlockListener
import org.lantern.listen.PlayerListener
import org.lantern.listen.WardrobeListener
import org.lantern.config.UiConfigurations
import org.lantern.placeholder.PlaceholderService

class LanternPlugin : AyPlugin() {

    companion object {
        lateinit var instance: LanternPlugin
    }

    lateinit var channelListener: LanternChannelMessageListener
        private set

    override fun onEnable() {
        instance = this
        Configurations.load()
        CustomBlockTracker.load()
        CustomBlockTracker.startAutoSave(this)
        CostumeAssignmentHandler.load()
        CostumeAssignmentHandler.startAutoSave(this)
        // 注册监听器
        Bukkit.getPluginManager().registerEvents(PlayerListener(), this)
        Bukkit.getPluginManager().registerEvents(BlockListener(), this)
        Bukkit.getPluginManager().registerEvents(WardrobeListener(), this)
        // 注册消息通道
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "lantern:main")
        channelListener = LanternChannelMessageListener()
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "lantern:main", channelListener)
        // 注册命令
        getCommand("lantern")?.let {
            val cmd = LanternCommand()
            it.setExecutor(cmd)
            it.tabCompleter = cmd
        }
        // 启动 PlaceholderAPI 变量推送（依赖 UiConfigurations 已加载）
        PlaceholderService.load(UiConfigurations.getPlaceholderConfigs())
    }

    override fun onDisable() {
        PlaceholderService.stopAll()
        CustomBlockTracker.stopAutoSave()
        CustomBlockTracker.save()
        CostumeAssignmentHandler.stopAutoSave()
        CostumeAssignmentHandler.save()
    }
}