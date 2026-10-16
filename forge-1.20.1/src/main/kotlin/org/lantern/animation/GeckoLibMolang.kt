package org.lantern.animation

import com.google.gson.JsonElement
import java.lang.reflect.Field
import java.util.function.DoubleSupplier
import org.lantern.core.anim.molang.MolangCompiler
import org.lantern.core.anim.molang.MolangExpression
import org.lantern.core.anim.molang.MolangScope
import software.bernie.geckolib.core.molang.LazyVariable
import software.bernie.geckolib.core.molang.MolangParser
import software.bernie.geckolib.core.molang.expressions.MolangValue

/**
 * 共享 GeckoLibMolang 的 1.20.1 版：这一版 GeckoLib 用的是 mclib 的旧 molang
 * （MolangParser / LazyVariable），表达式会被整体转成小写，变量同样按名全局共享。
 * 求值时临时换入本实体作用域的值、求完换回；LazyVariable 不暴露当前 supplier，只能反射读
 */
object GeckoLibMolang : MolangCompiler {

    override fun compile(element: JsonElement): MolangExpression = wrap(MolangParser.parseJson(element))

    override fun compileMolang(source: String): MolangExpression = wrap(MolangParser.parseExpression(source))

    private fun wrap(value: MolangValue): MolangExpression {
        if (value.isConstant) {
            val constant = value.get()
            return MolangExpression { constant }
        }
        return MolangExpression { scope -> ScopedVariables.evaluate(scope, value) }
    }
}

/** 渲染线程独占：MolangParser.VARIABLES 本身就不是线程安全的表 */
private object ScopedVariables {

    private val supplierField: Field =
        LazyVariable::class.java.getDeclaredField("valueSupplier").apply { isAccessible = true }

    private val swapped = ArrayList<LazyVariable>()
    private val previous = ArrayList<DoubleSupplier>()

    fun evaluate(scope: MolangScope, value: MolangValue): Double {
        val base = swapped.size
        for ((name, v) in scope.queries) swap(name, v)
        for ((name, v) in scope.variables) {
            swap(if (name.startsWith("variable.")) name else "variable.$name", v)
        }
        try {
            return value.get()
        } finally {
            for (i in swapped.size - 1 downTo base) {
                swapped[i].set(previous[i])
                swapped.removeAt(i)
                previous.removeAt(i)
            }
        }
    }

    private fun swap(name: String, v: Double) {
        val variable = MolangParser.INSTANCE.getVariable(name.lowercase())
        previous.add(supplierField.get(variable) as DoubleSupplier)
        variable.set(DoubleSupplier { v })
        swapped.add(variable)
    }
}
