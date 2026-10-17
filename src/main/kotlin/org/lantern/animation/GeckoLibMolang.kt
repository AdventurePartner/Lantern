package org.lantern.animation

import com.google.gson.JsonElement
import java.util.function.DoubleSupplier
import org.lantern.core.anim.molang.MolangCompiler
import org.lantern.core.anim.molang.MolangExpression
import org.lantern.core.anim.molang.MolangScope
import software.bernie.geckolib.loading.math.MathParser
import software.bernie.geckolib.loading.math.MathValue
import software.bernie.geckolib.loading.math.value.Variable

/**
 * GeckoLib 4.7 / 4.9 的 molang 求值桥（Fabric 1.21.1 / NeoForge 1.21.1；1.20.1 与 1.21.10 各有副本）。
 *
 * 4.7 的 Variable 按名全局共享、没有求值期作用域：GeckoLib 自己的 query 大多是读当前
 * 渲染对象的惰性 supplier，只有 anim_time 每个控制器重设一次。所以求值时把本实体作用域里
 * 的 query / variable 临时换成本实体的值，求完原样换回，GeckoLib 自己的动画不受影响。
 * 常量表达式（关键帧绝大多数是常量）在编译期折叠，不走替换
 */
object GeckoLibMolang : MolangCompiler {

    override fun compile(element: JsonElement): MolangExpression = wrap(MathParser.parseJson(element))

    override fun compileMolang(source: String): MolangExpression = wrap(MathParser.compileMolang(source))

    private fun wrap(value: MathValue): MolangExpression {
        if (!value.isMutable) {
            val constant = value.get()
            return MolangExpression { constant }
        }
        return MolangExpression { scope -> ScopedVariables.evaluate(scope, value) }
    }
}

/** 渲染线程独占：Fabric / NeoForge 的实体渲染与收包处理都在主线程 */
private object ScopedVariables {

    private val variables = HashMap<String, Variable>()
    private val swapped = ArrayList<Variable>()
    private val previous = ArrayList<DoubleSupplier>()

    fun evaluate(scope: MolangScope, value: MathValue): Double {
        val base = swapped.size
        for ((name, v) in scope.queries) swap(name, v)
        for ((name, v) in scope.variables) {
            swap(if (name.startsWith("variable.")) name else "variable.$name", v)
        }
        try {
            return value.get()
        } finally {
            for (i in swapped.size - 1 downTo base) {
                swapped[i].value().set(previous[i])
                swapped.removeAt(i)
                previous.removeAt(i)
            }
        }
    }

    private fun swap(name: String, v: Double) {
        val variable = variables.getOrPut(name) { MathParser.getVariableFor(name) }
        previous.add(variable.value().getAndSet(DoubleSupplier { v }))
        swapped.add(variable)
    }
}
