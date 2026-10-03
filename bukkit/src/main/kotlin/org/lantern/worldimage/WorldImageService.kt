package org.lantern.worldimage

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.lantern.network.NetworkHandler

/**
 * 世界图片实例指令（opcode 23）的投递面：按半径筛选接收者，
 * 命令调试用途只发执行者本人。spawn 载荷的组包由调用方完成，这里只负责发送。
 */
object WorldImageService {

    /** 以 center 为圆心、radius 为半径的在线玩家（同世界）。 */
    fun receivers(center: Location, radius: Double): List<Player> {
        val world = center.world ?: return emptyList()
        val radiusSq = radius * radius
        return Bukkit.getOnlinePlayers().filter {
            it.world == world && it.location.distanceSquared(center) <= radiusSq
        }
    }

    fun spawn(players: Collection<Player>, instances: List<JsonObject>) {
        if (players.isEmpty() || instances.isEmpty()) return
        val packet = JsonObject()
        packet.addProperty("action", "spawn")
        val array = JsonArray()
        instances.forEach(array::add)
        packet.add("images", array)
        NetworkHandler.sendWorldImageCommand(players, packet)
    }

    fun clear(players: Collection<Player>) {
        if (players.isEmpty()) return
        val packet = JsonObject()
        packet.addProperty("action", "clear")
        NetworkHandler.sendWorldImageCommand(players, packet)
    }
}
