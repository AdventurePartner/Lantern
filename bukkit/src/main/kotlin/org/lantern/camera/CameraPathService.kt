package org.lantern.camera

import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 运镜路径：客户端打点会话（内存）+ 落盘 `plugins/Lantern/cameraPaths/<id>.yml` + 播放分发。
 *
 * 打点语义：玩家走到机位、看向目标、`add` 记录眼睛位置与 yaw/pitch；
 * 每个关键帧声明「上一帧 → 本帧」的 interp：linear / smooth / hold（帧动画跳切）。
 */
object CameraPathService {

    data class Frame(
        val t: Int,
        val x: Double, val y: Double, val z: Double,
        val yaw: Float, val pitch: Float,
        val fov: Double?,
        val interp: String
    )

    private class Session(val id: String, val frames: MutableList<Frame>)

    private val sessions = ConcurrentHashMap<UUID, Session>()

    private val INTERPS = setOf("linear", "smooth", "hold")

    private fun dir(): File = File(LanternPlugin.instance.dataFolder, "cameraPaths").apply { mkdirs() }

    private fun validId(id: String): Boolean = id.length in 1..32 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    fun savedIds(): List<String> =
        dir().listFiles { file -> file.isFile && file.extension.equals("yml", true) }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    /** 开始/继续编辑：目标路径已有存档则载入。返回结果消息。 */
    fun start(player: Player, id: String): String {
        if (!validId(id)) return "Invalid path id '$id' (allowed: letters, digits, -, _)"
        val file = File(dir(), "$id.yml")
        val frames = if (file.isFile) loadFrames(file) else mutableListOf()
        sessions[player.uniqueId] = Session(id, frames)
        return if (frames.isEmpty()) {
            "Editing new camera path '$id' — walk to a viewpoint and /lantern campath add"
        } else {
            "Editing camera path '$id' (${frames.size} keyframes loaded)"
        }
    }

    /** 打一个点：记录当前眼睛位姿。弹性解析：数字第一个=t、第二个=fov，单词=interp。 */
    fun add(player: Player, tokens: List<String?>): String {
        val session = sessions[player.uniqueId] ?: return "No editing session — /lantern campath start <id> first"
        var t: Int? = null
        var interp: String? = null
        var fov: Double? = null
        for (raw in tokens) {
            val token = raw?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val number = token.toDoubleOrNull()
            when {
                number != null && t == null -> t = number.toInt()
                number != null -> fov = number
                interp == null -> interp = token.lowercase()
                else -> return "Unexpected argument '$token'"
            }
        }
        val resolvedInterp = interp ?: "smooth"
        if (resolvedInterp !in INTERPS) return "Invalid interp '$interp' (linear | smooth | hold)"
        val eye = player.eyeLocation
        val nextT = t ?: (session.frames.lastOrNull()?.let { it.t + 20 } ?: 0)
        val frame = Frame(nextT, eye.x, eye.y, eye.z, eye.yaw, eye.pitch, fov, resolvedInterp)
        session.frames.add(frame)
        return "Keyframe #${session.frames.size} @ t=${frame.t} ($resolvedInterp" +
            (fov?.let { ", fov=$it" } ?: "") + ") at ${eye.x.toInt()}, ${eye.y.toInt()}, ${eye.z.toInt()}"
    }

    fun undo(player: Player): String {
        val session = sessions[player.uniqueId] ?: return "No editing session"
        val removed = session.frames.removeLastOrNull() ?: return "No keyframes to undo"
        return "Removed keyframe @ t=${removed.t} (${session.frames.size} left)"
    }

    fun clear(player: Player): String {
        val session = sessions[player.uniqueId] ?: return "No editing session"
        session.frames.clear()
        return "Cleared all keyframes of '${session.id}'"
    }

    fun list(player: Player): List<String> {
        val session = sessions[player.uniqueId] ?: return listOf("No editing session")
        if (session.frames.isEmpty()) return listOf("Path '${session.id}' is empty")
        return buildList {
            add("Path '${session.id}' — ${session.frames.size} keyframes:")
            session.frames.forEachIndexed { index, frame ->
                add(
                    "  #${index + 1} t=${frame.t} ${frame.interp}" +
                        (frame.fov?.let { " fov=$it" } ?: "") +
                        " pos=${frame.x.toInt()},${frame.y.toInt()},${frame.z.toInt()}" +
                        " yaw=${frame.yaw.toInt()} pitch=${frame.pitch.toInt()}"
                )
            }
        }
    }

    /** 粒子预览：关键帧火苗点 + 段间直线采样（曲率的真实手感以 play 预演为准）。 */
    fun preview(player: Player): String {
        val session = sessions[player.uniqueId] ?: return "No editing session"
        if (session.frames.size < 2) return "Need at least 2 keyframes to preview"
        val world = player.world
        session.frames.forEach { frame ->
            world.spawnParticle(Particle.FLAME, frame.x, frame.y, frame.z, 1, 0.0, 0.0, 0.0, 0.0)
        }
        // Spigot API 1.20.1：红色尘粒枚举名是 REDSTONE（1.20.5+ 才更名 DUST）
        val dust = Particle.DustOptions(Color.AQUA, 1.0f)
        for (i in 0 until session.frames.size - 1) {
            val a = session.frames[i]
            val b = session.frames[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            val steps = (distance * 2.0).toInt().coerceIn(1, 64)
            for (step in 1 until steps) {
                val u = step.toDouble() / steps
                world.spawnParticle(
                    Particle.REDSTONE, a.x + dx * u, a.y + dy * u, a.z + dz * u, 1, 0.0, 0.0, 0.0, 0.0, dust)
            }
        }
        return "Preview shown for ${session.frames.size} keyframes (flame = keyframe, aqua line = segment)"
    }

    /** 会话内自预演（未保存也能放）。 */
    fun playSelf(player: Player, speed: Double): String {
        val session = sessions[player.uniqueId] ?: return "No editing session"
        if (session.frames.size < 2) return "Need at least 2 keyframes to play"
        NetworkHandler.cameraPath(player, session.frames, speed)
        return "Playing path '${session.id}' (${session.frames.size} keyframes, speed=$speed)"
    }

    fun save(player: Player): String {
        val session = sessions[player.uniqueId] ?: return "No editing session"
        if (session.frames.isEmpty()) return "Nothing to save"
        val yml = YamlConfiguration()
        yml.set("speed", 1.0)
        yml.set("loop", false)
        // 列表套映射格式（与 getMapList 读取对应；手改文件同样写这个格式）
        val list = ArrayList<Map<String, Any?>>(session.frames.size)
        session.frames.forEach { frame ->
            list.add(
                linkedMapOf<String, Any?>(
                    "t" to frame.t,
                    "x" to frame.x,
                    "y" to frame.y,
                    "z" to frame.z,
                    "yaw" to frame.yaw,
                    "pitch" to frame.pitch,
                    "fov" to frame.fov,
                    "interp" to frame.interp
                )
            )
        }
        yml.set("keyframes", list)
        yml.save(File(dir(), "${session.id}.yml"))
        return "Saved path '${session.id}' (${session.frames.size} keyframes)"
    }

    fun stopEditing(player: Player): String {
        val session = sessions.remove(player.uniqueId) ?: return "No editing session"
        return "Stopped editing '${session.id}' (${session.frames.size} keyframes in session)"
    }

    /** 正式播放存档路径到目标玩家。成功返回 null，失败返回错误消息。 */
    fun playTo(target: Player, id: String, speed: Double): String? {
        if (!validId(id)) return "Invalid path id '$id'"
        val file = File(dir(), "$id.yml")
        if (!file.isFile) return "Camera path '$id' not found (saved: ${savedIds().joinToString(", ").ifEmpty { "none" }})"
        val frames = loadFrames(file)
        if (frames.size < 2) return "Path '$id' has fewer than 2 keyframes"
        val yml = YamlConfiguration.loadConfiguration(file)
        val loop = yml.getBoolean("loop", false)
        NetworkHandler.cameraPath(target, frames, speed, loop)
        return null
    }

    /** 兼容两种格式：列表套映射（当前 save 与手改）、映射套映射（早期版本 keyframes.0.t）。 */
    private fun loadFrames(file: File): MutableList<Frame> {
        val yml = YamlConfiguration.loadConfiguration(file)
        val frames = mutableListOf<Frame>()
        fun readFrame(raw: Map<*, *>): Frame? {
            val t = (raw["t"] as? Number)?.toInt() ?: return null
            val x = (raw["x"] as? Number)?.toDouble() ?: return null
            val y = (raw["y"] as? Number)?.toDouble() ?: return null
            val z = (raw["z"] as? Number)?.toDouble() ?: return null
            val yaw = (raw["yaw"] as? Number)?.toFloat() ?: 0f
            val pitch = (raw["pitch"] as? Number)?.toFloat() ?: 0f
            val fov = raw["fov"] as? Number
            val interp = (raw["interp"] as? String)?.lowercase()?.takeIf { it in INTERPS } ?: "smooth"
            return Frame(t, x, y, z, yaw, pitch, fov?.toDouble(), interp)
        }
        if (yml.isList("keyframes")) {
            yml.getMapList("keyframes").forEach { raw ->
                readFrame(raw)?.let(frames::add)
            }
        } else {
            val section = yml.getConfigurationSection("keyframes")
            section?.getKeys(false)
                ?.mapNotNull { it.toIntOrNull() }
                ?.sorted()
                ?.forEach { index ->
                    section.getConfigurationSection(index.toString())
                        ?.getValues(false)
                        ?.let { raw -> readFrame(raw)?.let(frames::add) }
                }
        }
        return frames
    }

    fun removePlayer(uuid: UUID) {
        sessions.remove(uuid)
    }
}
