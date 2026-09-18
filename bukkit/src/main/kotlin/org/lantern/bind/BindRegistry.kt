package org.lantern.bind

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.lantern.LanternPlugin
import org.lantern.network.NetworkHandler

/**
 * 载体绑定登记表（服务端权威侧）。
 *
 * 绑定只改渲染：客户端把载体画到宿主的插值位置上，载体实体的服务端坐标、
 * 碰撞体、AI、移动逻辑一概不动。所以「杯子挂在玩家手上」的做法是
 * summon 一只不动的载体实体，再把它绑到玩家身上，而不是每 tick teleport——
 * 后者在网络上是每秒 20 个位置包乘以视野内人数，前者只有一次绑定包。
 *
 * 表是纯内存的：重启后绑定不保留。绑定的生命周期本就跟着一次技能演出走，
 * 落盘反而会在重启后留下一堆指向已消失实体的死条目
 */
object BindRegistry {

    /**
     * @param offsetX/Y/Z rotate=true 时为宿主朝向的局部坐标（x=右 y=上 z=前），
     *                    rotate=false 时为世界坐标偏移
     * @param rotate  载体整体 yaw 跟随宿主
     * @param visible 宿主脱离视野时载体仍渲染（客户端跳过视锥剔除）
     * @param expireAtMs 到期解绑的毫秒时间戳；0 = 持续到宿主或载体消失
     */
    data class Bind(
        val follower: UUID,
        val host: UUID,
        val offsetX: Double,
        val offsetY: Double,
        val offsetZ: Double,
        val rotate: Boolean,
        val visible: Boolean,
        val expireAtMs: Long
    )

    private val binds = ConcurrentHashMap<UUID, Bind>()

    /**
     * 每条绑定已经同步过的观察者集合。
     *
     * 绑定包按宿主位置做半径广播，所以「绑定时不在场、后来走进来」的玩家必须补发；
     * 反过来走出范围要从集合里摘掉，下次再进来重新发一遍。没有这张表就只能
     * 每秒对全场重播绑定表，那是白白烧带宽
     */
    private val syncedViewers = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    /**
     * 连续几次检查都找不到实体才判定消失。
     *
     * Bukkit.getEntity 对「区块未加载」和「实体已死」都返回 null，无法区分。
     * 单次未命中就解绑会让载体在宿主路过未加载区块时莫名其妙断掉，
     * 因此按连续未命中计数，三次（约 3 秒）才认定真的没了
     */
    private const val MISSES_BEFORE_DROP = 3
    private val missCounts = ConcurrentHashMap<UUID, Int>()

    private var task: BukkitTask? = null

    /**
     * 建立绑定。必须在主线程调用（要读实体表并派发 Bukkit 事件）。
     *
     * @return 失败原因文本，成功返回 null
     */
    fun bind(
        hostUuid: UUID,
        followerUuid: UUID,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        rotate: Boolean,
        visible: Boolean,
        durationMs: Long
    ): String? {
        if (hostUuid == followerUuid) return "宿主与载体不能是同一个实体"
        val host = Bukkit.getEntity(hostUuid) ?: return "宿主实体不存在或所在区块未加载: $hostUuid"
        val follower = Bukkit.getEntity(followerUuid) ?: return "载体实体不存在或所在区块未加载: $followerUuid"
        val bind = Bind(
            follower = followerUuid,
            host = hostUuid,
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            rotate = rotate,
            visible = visible,
            expireAtMs = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
        )
        binds[followerUuid] = bind
        missCounts.remove(followerUuid)
        val viewers = ConcurrentHashMap.newKeySet<UUID>()
        syncedViewers[followerUuid] = viewers
        NetworkHandler.nearbyViewers(host).forEach { viewer ->
            NetworkHandler.sendBind(viewer, bind)
            viewers.add(viewer.uniqueId)
        }
        Bukkit.getPluginManager().callEvent(LanternBindEvent(host, follower, bind))
        // 运行时可观测：绑定成功在画面上没有独立信号（看起来只是特效位置对了），
        // 失败也只是"特效没跟着走"。不打这一条就无法区分"没绑上"和"绑上了但偏移不对"
        LanternPlugin.instance.logger.info(
            "[Lantern] 绑定建立: 载体=$followerUuid 宿主=$hostUuid 偏移=$offsetX/$offsetY/$offsetZ " +
                "rotate=$rotate visible=$visible 时长=${if (durationMs > 0) "${durationMs}ms" else "持续"} " +
                "观察者=${viewers.size}"
        )
        return null
    }

    /** 解除绑定并广播。必须在主线程调用。 */
    fun unbind(followerUuid: UUID, reason: String = "api"): Boolean {
        val bind = binds.remove(followerUuid) ?: return false
        missCounts.remove(followerUuid)
        val viewers = HashSet(syncedViewers.remove(followerUuid) ?: emptySet<UUID>())
        // 收过绑定的都要收到解绑；走出广播半径被摘掉记录的观察者也补一条——
        // 他手里的绑定条目还在，载体活着就一直挂在宿主身上
        Bukkit.getEntity(bind.host)?.let { host ->
            NetworkHandler.nearbyViewers(host).forEach { viewers.add(it.uniqueId) }
        }
        viewers.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { NetworkHandler.sendUnbind(it, followerUuid) }
        }
        Bukkit.getPluginManager().callEvent(LanternUnbindEvent(followerUuid, bind.host, reason))
        return true
    }

    fun get(followerUuid: UUID): Bind? = binds[followerUuid]

    fun all(): Collection<Bind> = binds.values

    /** 挂在该宿主身上的全部载体 */
    fun followersOf(hostUuid: UUID): List<Bind> = binds.values.filter { it.host == hostUuid }

    /** 玩家退服：把它从各条绑定的已同步集合里摘掉，重新进来会重新收到 */
    fun forgetViewer(viewerUuid: UUID) {
        syncedViewers.values.forEach { it.remove(viewerUuid) }
    }

    /** 玩家登录时全量补发其视野内的绑定（周期任务也会兜到，这里让它即时生效） */
    fun syncTo(player: Player) {
        binds.values.forEach { bind ->
            val host = Bukkit.getEntity(bind.host) ?: return@forEach
            if (!isViewer(player, host)) return@forEach
            NetworkHandler.sendBind(player, bind)
            syncedViewers.computeIfAbsent(bind.follower) { ConcurrentHashMap.newKeySet() }
                .add(player.uniqueId)
        }
    }

    /** 每秒一轮：到期回收、实体消失回收、范围进出补发。 */
    fun start(plugin: LanternPlugin) {
        stop()
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun reset() {
        stop()
        binds.clear()
        syncedViewers.clear()
        missCounts.clear()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        for (bind in binds.values.toList()) {
            if (bind.expireAtMs in 1..now) {
                unbind(bind.follower, "expired")
                continue
            }
            val host = Bukkit.getEntity(bind.host)
            val follower = Bukkit.getEntity(bind.follower)
            if (host == null || !host.isValid || follower == null || !follower.isValid) {
                val misses = (missCounts[bind.follower] ?: 0) + 1
                if (misses >= MISSES_BEFORE_DROP) {
                    unbind(bind.follower, "gone")
                } else {
                    missCounts[bind.follower] = misses
                }
                continue
            }
            missCounts.remove(bind.follower)
            // 宿主传送到别的世界：客户端查不到宿主、服务端按宿主所在世界广播，
            // 条目留着只会给新进范围的玩家发一条画不出来的绑定
            if (host.world != follower.world) {
                unbind(bind.follower, "world")
                continue
            }
            syncViewers(bind, host)
        }
    }

    /** 进入宿主附近的玩家补发绑定，离开的摘掉记录（再进来会重发） */
    private fun syncViewers(bind: Bind, host: Entity) {
        val viewers = syncedViewers.computeIfAbsent(bind.follower) { ConcurrentHashMap.newKeySet() }
        val inRange = HashSet<UUID>()
        NetworkHandler.nearbyViewers(host).forEach { viewer ->
            inRange.add(viewer.uniqueId)
            if (viewers.add(viewer.uniqueId)) {
                NetworkHandler.sendBind(viewer, bind)
            }
        }
        viewers.retainAll(inRange)
    }

    private fun isViewer(player: Player, host: Entity): Boolean =
        NetworkHandler.isNearby(host, player)
}
