package org.lantern.camera

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 玩家的越肩相机会话状态（服务端权威；退出清空，V1 不持久化）。 */
data class PlayerCameraState(
    var enabled: Boolean,
    var offsetX: Double,
    var offsetY: Double,
    var distance: Double
)

/**
 * 越肩相机服务端服务（camera.yml 独立配置）。
 *
 * 职责：解析配置（默认值/限幅/步进/键位表）、持有 per-player 会话状态、
 * 消化客户端键盘调节事件（相机键优先于 keys.yml 命令派发）、packet 18 推送。
 */
object ShoulderCameraService {

    private var globalEnabled = true
    private var adjustEnabled = true

    private var defaultOffsetX = 0.75
    private var defaultOffsetY = 0.0
    private var defaultDistance = 4.0

    private var maxOffsetX = 1.5
    private var maxOffsetY = 1.0
    private var minDistance = 1.5
    private var maxDistance = 8.0

    private var stepOffsetX = 0.05
    private var stepOffsetY = 0.05
    private var stepDistance = 0.25

    /** 键 spec（小写）-> 动作；spec 语法与 keys.yml 一致 */
    private val keyActions = LinkedHashMap<String, String>()

    private val playerStates = ConcurrentHashMap<UUID, PlayerCameraState>()

    fun load(config: FileConfiguration) {
        val section = config.getConfigurationSection("shoulder") ?: run {
            LanternPlugin.instance.logger.warning("camera.yml missing 'shoulder' section, camera control disabled")
            globalEnabled = false
            adjustEnabled = false
            keyActions.clear()
            return
        }

        globalEnabled = section.getBoolean("enabled", true)
        adjustEnabled = section.getBoolean("adjust.enabled", true)

        maxOffsetX = section.getDouble("max-offset-x", 1.5).coerceAtLeast(0.0)
        maxOffsetY = section.getDouble("max-offset-y", 1.0).coerceAtLeast(0.0)
        minDistance = section.getDouble("min-distance", 1.5).coerceAtLeast(0.5)
        maxDistance = section.getDouble("max-distance", 8.0).coerceAtLeast(minDistance)

        defaultOffsetX = section.getDouble("default-offset-x", 0.75).coerceIn(-maxOffsetX, maxOffsetX)
        defaultOffsetY = section.getDouble("default-offset-y", 0.0).coerceIn(-maxOffsetY, maxOffsetY)
        defaultDistance = section.getDouble("default-distance", 4.0).coerceIn(minDistance, maxDistance)

        stepOffsetX = section.getDouble("adjust.step-offset-x", 0.05).coerceIn(0.01, 1.0)
        stepOffsetY = section.getDouble("adjust.step-offset-y", 0.05).coerceIn(0.01, 1.0)
        stepDistance = section.getDouble("adjust.step-distance", 0.25).coerceIn(0.05, 2.0)

        keyActions.clear()
        val adjust = section.getConfigurationSection("adjust")
        if (adjust != null) {
            fun bind(path: String, action: String) {
                val spec = adjust.getString(path)?.trim()?.lowercase()
                if (!spec.isNullOrEmpty()) keyActions[spec] = action
            }
            bind("swap-key", "swap")
            bind("left-key", "left")
            bind("right-key", "right")
            bind("up-key", "up")
            bind("down-key", "down")
            bind("farther-key", "farther")
            bind("closer-key", "closer")
        }

        // 配置默认值变化后，既有会话状态仍按旧值保留（玩家自己的微调成果），
        // 但要重新夹到新限幅内；全服关闭时强制关掉存量玩家的开关（reload 语义）
        playerStates.values.forEach { state ->
            clampState(state)
            if (!globalEnabled) state.enabled = false
        }

        // 键位与 keys.yml 冲突：处理侧相机优先，提前告知管理员
        val collisions = keyActions.keys.filter { org.lantern.handler.CacheHandler.keys.containsKey(it) }
        if (collisions.isNotEmpty()) {
            LanternPlugin.instance.logger.warning(
                "camera.yml adjust keys collide with keys.yml bindings (camera takes precedence): $collisions"
            )
        }

        LanternPlugin.instance.logger.info(
            "Shoulder camera loaded: enabled=$globalEnabled, defaults(offset-x=$defaultOffsetX, offset-y=$defaultOffsetY, distance=$defaultDistance)"
        )
    }

    fun globalEnabled(): Boolean = globalEnabled

    /** 需要并入 packet 3 让客户端轮询的调节键（全服或微调关闭时不下发）。 */
    fun pollKeySpecs(): List<String> =
        if (globalEnabled && adjustEnabled) keyActions.keys.toList() else emptyList()

    fun stateOf(player: Player): PlayerCameraState =
        stateOf(player.uniqueId)

    fun stateOf(uuid: UUID): PlayerCameraState =
        playerStates.computeIfAbsent(uuid) { defaultState() }

    private fun defaultState() = PlayerCameraState(globalEnabled, defaultOffsetX, defaultOffsetY, defaultDistance)

    private fun clampState(state: PlayerCameraState) {
        state.offsetX = state.offsetX.coerceIn(-maxOffsetX, maxOffsetX)
        state.offsetY = state.offsetY.coerceIn(-maxOffsetY, maxOffsetY)
        state.distance = state.distance.coerceIn(minDistance, maxDistance)
    }

    /**
     * 客户端键盘事件入口（在命令派发之前调用）。
     * @return true = 该键是相机调节键（已处理/已吞掉，不再走 keys.yml 命令）
     */
    fun handleKey(player: Player, key: String, press: Boolean, inGui: Boolean): Boolean {
        if (!press || inGui || !globalEnabled || !adjustEnabled) return false
        val action = keyActions[key.lowercase()] ?: return false
        if (!stateOf(player.uniqueId).enabled) return true
        // 键盘包来自网络线程，调整与发包调度回主线程
        Bukkit.getScheduler().runTask(LanternPlugin.instance, Runnable {
            if (!player.isOnline) return@Runnable
            val state = stateOf(player.uniqueId)
            // 方向键语义 = 相机移动方向（用户 2026-08-27 明确选定）：
            // 按 ← 相机左移(offsetX-)、按 ↑ 相机抬高(offsetY+)；画面内容朝相反方向滑动
            when (action) {
                "swap" -> state.offsetX = -state.offsetX
                "left" -> state.offsetX -= stepOffsetX
                "right" -> state.offsetX += stepOffsetX
                "up" -> state.offsetY += stepOffsetY
                "down" -> state.offsetY -= stepOffsetY
                "farther" -> state.distance += stepDistance
                "closer" -> state.distance -= stepDistance
                else -> return@Runnable
            }
            clampState(state)
            pushState(player)
        })
        return true
    }

    /** 切换玩家越肩开关（全服关闭时不可开）。返回切换后的状态；全服关闭返回 null。 */
    fun toggle(player: Player): Boolean? {
        if (!globalEnabled) return null
        val state = stateOf(player.uniqueId)
        state.enabled = !state.enabled
        pushState(player)
        return state.enabled
    }

    /** 恢复默认参数（开关保持现状）。 */
    fun reset(player: Player) {
        val state = stateOf(player.uniqueId)
        state.offsetX = defaultOffsetX
        state.offsetY = defaultOffsetY
        state.distance = defaultDistance
        clampState(state)
        pushState(player)
    }

    /** 管理员设置参数（同受限幅约束）。param: offset-x | offset-y | distance。 */
    fun setParam(player: Player, param: String, value: Double): Boolean {
        val state = stateOf(player.uniqueId)
        when (param) {
            "offset-x" -> state.offsetX = value
            "offset-y" -> state.offsetY = value
            "distance" -> state.distance = value
            else -> return false
        }
        clampState(state)
        pushState(player)
        return true
    }

    fun pushState(player: Player) {
        val state = stateOf(player.uniqueId)
        // 下发生效值 = 全服开关 && 玩家开关（reload 关总开关能即时踢掉存量玩家的越肩）
        NetworkHandler.sendCameraState(
            player, globalEnabled && state.enabled, state.offsetX, state.offsetY, state.distance
        )
    }

    fun removePlayer(uuid: UUID) {
        playerStates.remove(uuid)
    }
}
