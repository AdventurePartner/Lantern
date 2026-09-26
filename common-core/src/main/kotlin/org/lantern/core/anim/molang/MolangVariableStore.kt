package org.lantern.core.anim.molang

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.lantern.core.CoreLog

/**
 * packet 17 同步的服务端注入变量（per-entity）。
 *
 * 值支持纯数字或 molang 表达式（表达式在客户端每帧求值，可引用 query.*——
 * 例如按生命百分比驱动的发光强度）；更新为合并语义，空串值 = 删除该变量。
 * 网络线程直接调用：只触碰 ConcurrentHashMap，值编译不依赖 MC 状态。
 *
 * 表达式编译经平台注入的 [MolangCompiler]（平台入口 attach，收包必然在装配之后）
 */
object MolangVariableStore {

    lateinit var compiler: MolangCompiler

    private val values = ConcurrentHashMap<UUID, Map<String, MolangExpression>>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun update(uuid: UUID, vars: Map<String, String>) {
        values.compute(uuid) { _, existing ->
            val merged = existing?.toMutableMap() ?: LinkedHashMap()
            for ((key, raw) in vars) {
                if (raw.isBlank()) {
                    merged.remove(key)
                    continue
                }
                runCatching { merged[key] = compiler.compileMolang(raw) }
                    .onFailure {
                        if (warned.add("$uuid:$key")) {
                            CoreLog.logger.warning(
                                "[Lantern] Bad molang variable '$key=$raw' for $uuid: ${it.message}"
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

    /** 退服清空：变量按实体 UUID 存，换服后同 UUID 的实体会读到上一服的值 */
    fun clear() {
        values.clear()
        warned.clear()
    }

    /**
     * 每帧求值 per-entity 变量值（变量本身可为表达式）。
     * 求值时 scope 的 variables 尚为空表——变量引用变量按 0 处理（与历史行为一致）。
     * 无变量实体返回共享空表
     */
    fun resolve(uuid: UUID, scope: MolangScope): Map<String, Double> {
        val map = values[uuid] ?: return emptyMap()
        val resolved = HashMap<String, Double>(map.size)
        for ((key, expression) in map) {
            resolved[key] = runCatching { expression.eval(scope) }.getOrDefault(0.0)
        }
        return resolved
    }
}
