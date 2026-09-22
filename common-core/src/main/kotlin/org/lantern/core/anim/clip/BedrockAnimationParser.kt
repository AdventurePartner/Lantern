package org.lantern.core.anim.clip

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.lantern.core.CoreLog
import org.lantern.core.anim.molang.MolangCompiler
import org.lantern.core.anim.molang.MolangExpression
import org.lantern.core.anim.molang.MolangScope

/**
 * 基岩版 .animation.json 的自有解析器。
 *
 * 不依赖 GeckoLib 的 BakedAnimations/KeyframeStack（其公有 API 面随版本漂移、且部分
 * 关键语义无文档），Lantern 直接解析原始 JSON，动画时间轴 100% 自管。
 *
 * 支持的关键帧值形态（Blockbench 导出的全部变体），每轴可为数值或 molang 表达式：
 *   [x,y,z]
 *   {"vector": [x,y,z]}
 *   {"pre": <值>, "post": <值>, "lerp_mode": "catmullrom"}
 *
 * 表达式经平台注入的 [MolangCompiler] 编译，每帧由播放器带 per-entity 求值上下文取值——
 * query.* 由 Lantern 构建的 queryValues 供给，variable.* 为服务端注入变量
 */
data class Vec3(val x: Float, val y: Float, val z: Float)

/** 关键帧值：每轴一个已编译的 molang 表达式（数值即 Constant），按求值上下文取值 */
class ExprVec3(val x: MolangExpression, val y: MolangExpression, val z: MolangExpression) {

    fun eval(scope: MolangScope): Vec3 = Vec3(
        x.eval(scope).toFloat(),
        y.eval(scope).toFloat(),
        z.eval(scope).toFloat()
    )
}

/** pre = 关键帧前值（段落起点语义，区间端点实证：prev.post -> next.pre） */
data class Keyframe(val time: Float, val value: ExprVec3, val pre: ExprVec3?, val lerpMode: String?)

class BoneTracks(
    val rotation: List<Keyframe>,
    val position: List<Keyframe>,
    val scale: List<Keyframe>
)

class ClipData(
    val name: String,
    val length: Float,
    /** true=循环; "hold_on_last_frame"=停末帧; 其他=播一遍 */
    val loop: JsonElement?,
    val bones: Map<String, BoneTracks>
) {
    val loops: Boolean get() = loop == null || (loop.isJsonPrimitive && loop.asBoolean)
    val holdsLastFrame: Boolean
        get() = loop?.takeIf { it.isJsonPrimitive }?.asString == "hold_on_last_frame"
}

object BedrockAnimationParser {

    fun parse(root: JsonObject, compiler: MolangCompiler): Map<String, ClipData> {
        val animations = root.getAsJsonObject("animations") ?: return emptyMap()
        val result = LinkedHashMap<String, ClipData>()
        for ((name, element) in animations.entrySet()) {
            if (!element.isJsonObject) continue
            val anim = element.asJsonObject
            val length = anim.get("animation_length")?.asFloat ?: continue
            val bonesObj = anim.getAsJsonObject("bones") ?: JsonObject()
            val bones = LinkedHashMap<String, BoneTracks>()
            for ((boneName, boneElement) in bonesObj.entrySet()) {
                if (!boneElement.isJsonObject) continue
                val bone = boneElement.asJsonObject
                // 骨骼名统一小写作键：不同资产作者的大小写风格不一（有的写
                // Body/RightArm，本项目资产用 body/rightArm），集合相同却匹配不上会
                // 整段动画无效。写骨时同样按小写查找，两端一致即大小写不敏感
                bones[boneName.lowercase()] = BoneTracks(
                    rotation = parseTrack(bone, "rotation", compiler),
                    position = parseTrack(bone, "position", compiler),
                    scale = parseTrack(bone, "scale", compiler)
                )
            }
            result[name] = ClipData(name, length, anim.get("loop"), bones)
        }
        return result
    }

    private fun parseTrack(bone: JsonObject, key: String, compiler: MolangCompiler): List<Keyframe> {
        val element = bone.get(key) ?: return emptyList()
        // 常量轨道："rotation": [x,y,z]
        if (element.isJsonArray) {
            val v = parseExprVector(element.asJsonArray, compiler) ?: return emptyList()
            return listOf(Keyframe(0f, v, v, null))
        }
        if (!element.isJsonObject) return emptyList()
        val obj = element.asJsonObject
        val frames = LinkedHashMap<Float, Keyframe>()
        for ((timeStr, frameElement) in obj.entrySet()) {
            val time = timeStr.toFloatOrNull() ?: continue
            val (value, pre, lerpMode) = parseFrame(frameElement, compiler) ?: continue
            frames[time] = Keyframe(time, value, pre, lerpMode)
        }
        return frames.toSortedMap().map { it.value }
    }

    /** 返回 (post 值, pre 值, lerp_mode) */
    private fun parseFrame(element: JsonElement, compiler: MolangCompiler): Triple<ExprVec3, ExprVec3?, String?>? {
        if (element.isJsonArray) {
            val v = parseExprVector(element.asJsonArray, compiler) ?: return null
            return Triple(v, v, null)
        }
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val lerpMode = obj.get("lerp_mode")?.takeIf { it.isJsonPrimitive }?.asString
        val post = obj.get("post") ?: obj.get("vector") ?: return null
        val preElement = obj.get("pre")
        val value = parseValueVector(post, compiler) ?: return null
        val pre = preElement?.let { parseValueVector(it, compiler) }
        return Triple(value, pre, lerpMode)
    }

    private fun parseValueVector(element: JsonElement, compiler: MolangCompiler): ExprVec3? {
        return when {
            element.isJsonArray -> parseExprVector(element.asJsonArray, compiler)
            element.isJsonObject -> element.asJsonObject.get("vector")?.takeIf { it.isJsonArray }?.asJsonArray?.let { parseExprVector(it, compiler) }
            else -> null
        }
    }

    /** 每轴编译为 molang 表达式（数值->常量）。variable.* 节点到 per-entity 求值
     *  上下文的重定向由平台的编译器实现负责 */
    private fun parseExprVector(array: JsonArray, compiler: MolangCompiler): ExprVec3? {
        if (array.size() < 3) return null
        return runCatching {
            ExprVec3(
                compiler.compile(array.get(0)),
                compiler.compile(array.get(1)),
                compiler.compile(array.get(2))
            )
        }.onFailure {
            CoreLog.logger.warning("[Lantern] Failed to compile keyframe values $array: ${it.message}")
        }.getOrNull()
    }
}
