package org.lantern.internal.icon

import net.minecraft.resources.ResourceLocation

/**
 * 按原版 ItemModelGenerator 的规则把贴图挤成 1/16 厚的物品网格：
 * 正反两面整张贴图，不透明像素挨着透明像素（或贴图边界）的一侧补一条侧面。
 * 侧面按「朝向 + 所在行/列」归并，跨过中间的透明像素也只扩展端点——与原版一致，
 * 透明处的侧面采样到透明像素，渲染时自然看不见
 */
internal object IconMeshBuilder {

    private const val MIN_Z = 7.5f / 16f
    private const val MAX_Z = 8.5f / 16f

    /** UP/DOWN 沿一行展开（anchor 是行号），LEFT/RIGHT 沿一列展开（anchor 是列号） */
    private enum class Facing(val dx: Int, val dy: Int) {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        val alongRow: Boolean get() = this == UP || this == DOWN
    }

    private class Span(val facing: Facing, var min: Int, val anchor: Int) {
        var max = min

        fun expand(value: Int) {
            if (value < min) min = value else if (value > max) max = value
        }
    }

    /**
     * @param argb 第一帧的像素（width × frameHeight，行优先）
     * @param textureHeight 整张贴图的高度；动画贴图多帧竖排，V 只取第一帧那一段
     */
    fun build(
        texture: ResourceLocation,
        handheld: Boolean,
        argb: IntArray,
        width: Int,
        frameHeight: Int,
        textureHeight: Int
    ): IconMesh {
        fun transparent(x: Int, y: Int): Boolean =
            x < 0 || y < 0 || x >= width || y >= frameHeight || (argb[y * width + x] ushr 24) == 0

        val spans = ArrayList<Span>()
        for (y in 0 until frameHeight) {
            for (x in 0 until width) {
                if (transparent(x, y)) continue
                for (facing in Facing.entries) {
                    if (!transparent(x + facing.dx, y + facing.dy)) continue
                    val anchor = if (facing.alongRow) y else x
                    val position = if (facing.alongRow) x else y
                    val span = spans.firstOrNull { it.facing == facing && it.anchor == anchor }
                    if (span == null) spans.add(Span(facing, position, anchor)) else span.expand(position)
                }
            }
        }

        val quads = FloatArray((2 + spans.size) * IconMesh.STRIDE)
        val writer = QuadWriter(quads, width.toFloat(), textureHeight.toFloat())
        val px = 1f / width
        val py = 1f / frameHeight
        val frameV = frameHeight.toFloat()

        // 正面 SOUTH 与背面 NORTH：UV 分别为 [0,0,16,16] 与 [16,0,0,16]
        writer.quad(
            0f, 0f, 1f,
            0f, 1f, MAX_Z, 0f, 0f,
            0f, 0f, MAX_Z, 0f, frameV,
            1f, 0f, MAX_Z, width.toFloat(), frameV,
            1f, 1f, MAX_Z, width.toFloat(), 0f
        )
        writer.quad(
            0f, 0f, -1f,
            1f, 1f, MIN_Z, width.toFloat(), 0f,
            1f, 0f, MIN_Z, width.toFloat(), frameV,
            0f, 0f, MIN_Z, 0f, frameV,
            0f, 1f, MIN_Z, 0f, 0f
        )

        for (span in spans) {
            val a = span.anchor.toFloat()
            val lo = span.min.toFloat()
            val hi = span.max + 1f
            when (span.facing) {
                Facing.UP -> {
                    val y = 1f - span.anchor * py
                    writer.quad(
                        0f, 1f, 0f,
                        lo * px, y, MIN_Z, lo, a,
                        lo * px, y, MAX_Z, lo, a + 1f,
                        hi * px, y, MAX_Z, hi, a + 1f,
                        hi * px, y, MIN_Z, hi, a
                    )
                }
                Facing.DOWN -> {
                    val y = 1f - (span.anchor + 1) * py
                    writer.quad(
                        0f, -1f, 0f,
                        lo * px, y, MAX_Z, lo, a,
                        lo * px, y, MIN_Z, lo, a + 1f,
                        hi * px, y, MIN_Z, hi, a + 1f,
                        hi * px, y, MAX_Z, hi, a
                    )
                }
                Facing.LEFT -> {
                    val x = span.anchor * px
                    val top = 1f - lo * py
                    val bottom = 1f - hi * py
                    writer.quad(
                        -1f, 0f, 0f,
                        x, top, MIN_Z, a, lo,
                        x, bottom, MIN_Z, a, hi,
                        x, bottom, MAX_Z, a + 1f, hi,
                        x, top, MAX_Z, a + 1f, lo
                    )
                }
                Facing.RIGHT -> {
                    val x = (span.anchor + 1) * px
                    val top = 1f - lo * py
                    val bottom = 1f - hi * py
                    writer.quad(
                        1f, 0f, 0f,
                        x, top, MAX_Z, a, lo,
                        x, bottom, MAX_Z, a, hi,
                        x, bottom, MIN_Z, a + 1f, hi,
                        x, top, MIN_Z, a + 1f, lo
                    )
                }
            }
        }
        return IconMesh(texture, handheld, quads)
    }

    /** 顶点按原版 FaceInfo 的逆时针顺序给出；UV 以像素为单位传入，这里换算成整张贴图的 0..1 */
    private class QuadWriter(private val out: FloatArray, private val width: Float, private val height: Float) {
        private var offset = 0

        fun quad(
            nx: Float, ny: Float, nz: Float,
            x0: Float, y0: Float, z0: Float, u0: Float, v0: Float,
            x1: Float, y1: Float, z1: Float, u1: Float, v1: Float,
            x2: Float, y2: Float, z2: Float, u2: Float, v2: Float,
            x3: Float, y3: Float, z3: Float, u3: Float, v3: Float
        ) {
            put(nx, ny, nz)
            vertex(x0, y0, z0, u0, v0)
            vertex(x1, y1, z1, u1, v1)
            vertex(x2, y2, z2, u2, v2)
            vertex(x3, y3, z3, u3, v3)
        }

        private fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
            put(x, y, z)
            out[offset++] = u / width
            out[offset++] = v / height
        }

        private fun put(a: Float, b: Float, c: Float) {
            out[offset++] = a
            out[offset++] = b
            out[offset++] = c
        }
    }
}
