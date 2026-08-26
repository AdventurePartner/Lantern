package org.lantern.animation

import software.bernie.geckolib.loading.math.MathValue
import software.bernie.geckolib.loading.math.value.Variable

/**
 * molang 求值上下文：per-entity 的服务端注入变量经 ThreadLocal 传递给
 * 已编译表达式里的 variable.* 节点。
 *
 * GeckoLib 的 Variable 注册表是全局静态的（同名 Variable 全服一个实例），
 * per-entity 隔离只能靠求值期上下文实现：编译完成后把表达式引用的 variable.* 节点
 * 的取值函数重定向到 ThreadLocal 表，采样时填入当前实体的变量值。
 * query.* 不在此处理——它们读 AnimationState.queryValues（由 AnimationPlayer 每帧构建）。
 *
 * 提取阶段多实体可能并发采样，每线程一份表；GeckoLib 原生管线（costume 等）
 * 不使用 variable.* 前缀，重定向不影响它们。
 */
object MolangContext {

    private val current = ThreadLocal.withInitial { HashMap<String, Double>() }

    /** 把已编译表达式中引用的 variable.* 节点重定向到当前线程的求值上下文 */
    fun bindUsedVariables(compiled: MathValue) {
        for (variable in compiled.usedVariables) {
            if (variable.name().startsWith("variable.")) {
                variable.set { current.get().getOrDefault(variable.name(), 0.0) }
            }
        }
    }

    /** 在给定变量值的作用域内执行采样求值，结束后清空上下文。
     *  表的 key 空间与编译后表达式的 Variable 全名对齐（variable.<name>），
     *  协议下发的裸名在此补前缀（容忍已带前缀的写法） */
    fun <R> evaluate(vars: Map<String, Double>, block: () -> R): R {
        val table = current.get()
        for ((key, value) in vars) {
            table[if (key.startsWith("variable.")) key else "variable.$key"] = value
        }
        return try {
            block()
        } finally {
            table.clear()
        }
    }
}
