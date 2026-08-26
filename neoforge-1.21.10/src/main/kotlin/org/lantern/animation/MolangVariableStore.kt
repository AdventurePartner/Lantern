package org.lantern.animation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.lantern.Lantern
import software.bernie.geckolib.animatable.processing.AnimationState
import software.bernie.geckolib.loading.math.MathParser
import software.bernie.geckolib.loading.math.MathValue

/**
 * packet 17 同步的服务端注入变量（per-entity）。
 *
 * 值支持纯数字或 molang 表达式（表达式在客户端每帧求值，可引用 query.*——
 * 例如按生命百分比驱动的发光强度）；更新为合并语义，空串值 = 删除该变量。
 * 网络线程直接调用：只触碰 ConcurrentHashMap，值编译不依赖 MC 状态。
 */
object MolangVariableStore {

    private val values = ConcurrentHashMap<UUID, Map<String, MathValue>>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun update(uuid: UUID, vars: Map<String, String>) {
        values.compute(uuid) { _, existing ->
            val merged = existing?.toMutableMap() ?: LinkedHashMap()
            for ((key, raw) in vars) {
                if (raw.isBlank()) {
                    merged.remove(key)
                    continue
                }
                runCatching { merged[key] = MathParser.compileMolang(raw) }
                    .onFailure {
                        if (warned.add("$uuid:$key")) {
                            Lantern.logger.warn(
                                "[Lantern] Bad molang variable '{}={}' for {}: {}",
                                key, raw, uuid, it.message
                            )
                        }
                    }
            }
            if (merged.isEmpty()) null else merged
        }
    }

    fun remove(uuid: UUID) {
        values.remove(uuid)
    }

    /** 每帧求值 per-entity 变量值（变量本身可为表达式）；无变量实体返回共享空表 */
    fun resolve(uuid: UUID, state: AnimationState<*>): Map<String, Double> {
        val map = values[uuid] ?: return emptyMap()
        val resolved = HashMap<String, Double>(map.size)
        for ((key, expression) in map) {
            resolved[key] = runCatching { expression.get(state) }.getOrDefault(0.0)
        }
        return resolved
    }
}
