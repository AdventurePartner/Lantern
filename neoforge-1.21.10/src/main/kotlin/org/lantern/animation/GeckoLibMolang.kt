package org.lantern.animation

import com.google.gson.JsonElement
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap
import java.util.concurrent.ConcurrentHashMap
import org.lantern.core.anim.molang.MolangCompiler
import org.lantern.core.anim.molang.MolangExpression
import org.lantern.core.anim.molang.MolangScope
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.processing.AnimationState
import software.bernie.geckolib.loading.math.MathParser
import software.bernie.geckolib.loading.math.MathValue
import software.bernie.geckolib.loading.math.value.Variable

/**
 * GeckoLib 5.3 的 molang 求值桥：common-core 的编译/求值接口在本平台的实现。
 *
 * 编译：MathParser 把 JSON 值/表达式文本编为 MathValue AST；关键帧表达式编译后
 * 把 variable.* 节点重定向到 [MolangContext] 的 ThreadLocal 表（变量值表达式不重定向，
 * 与历史行为一致——变量引用变量按全局默认 0 处理）。
 *
 * 求值：common 的 query 名字符串表转换为 GeckoLib 的 Variable 键 queryValues，构造
 * 轻量 AnimationState（renderState/manager 不被触碰，query.* 只读 queryValues map）。
 * 转换按 (scope 实例, revision) 每线程缓存——播放器采样中按层覆写 anim_time 时
 * revision 递增，下一次 eval 自动重转换
 */
object GeckoLibMolang : MolangCompiler {

    override fun compile(element: JsonElement): MolangExpression {
        val value = MathParser.parseJson(element)
        MolangContext.bindUsedVariables(value)
        return Expression(value)
    }

    override fun compileMolang(source: String): MolangExpression =
        Expression(MathParser.compileMolang(source))

    private class Expression(private val value: MathValue) : MolangExpression {

        override fun eval(scope: MolangScope): Double {
            val state = EvalStateCache.resolve(scope)
            val vars = scope.variables
            // 变量表每次求值经 ThreadLocal 灌入（空表零成本）；eval 结束即清空，
            // 跨实体不残留——与原先块级 evaluate 包裹的可见性语义一致
            return if (vars.isEmpty()) value.get(state)
            else MolangContext.evaluate(vars) { value.get(state) }
        }
    }
}

/** 每线程一份 (scope, revision, AnimationState) 转换缓存；query 名 -> Variable 的解析全局缓存 */
private object EvalStateCache {

    private class Entry(var scope: MolangScope?, var revision: Long, var state: AnimationState<GeoAnimatable>?)

    private val variableCache = ConcurrentHashMap<String, Variable>()
    private val cache = ThreadLocal.withInitial { Entry(null, -1, null) }

    fun resolve(scope: MolangScope): AnimationState<*> {
        val entry = cache.get()
        if (entry.scope === scope && entry.revision == scope.revision) return entry.state!!
        val queries = Reference2DoubleOpenHashMap<Variable>()
        for ((name, value) in scope.queries) {
            queries.put(variableCache.computeIfAbsent(name) { MathParser.getVariableFor(name) }, value)
        }
        val state = AnimationState<GeoAnimatable>(null, null, 0f, queries, null)
        entry.scope = scope
        entry.revision = scope.revision
        entry.state = state
        return state
    }
}
