package org.lantern.internal.icon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer

/** 1.20.1 顶点链必须按格式顺序逐项提交并以 endVertex 收尾 */
object IconVertexEmitter {
    @JvmStatic
    fun emit(pose: PoseStack.Pose, consumer: VertexConsumer, mesh: IconMesh, light: Int, overlay: Int) {
        val data = mesh.data
        val matrix = pose.pose()
        val normal = pose.normal()
        for (quad in 0 until mesh.quadCount) {
            val base = quad * IconMesh.STRIDE
            val nx = data[base]
            val ny = data[base + 1]
            val nz = data[base + 2]
            for (vertex in 0 until 4) {
                val o = base + 3 + vertex * 5
                consumer.vertex(matrix, data[o], data[o + 1], data[o + 2])
                    .color(255, 255, 255, 255)
                    .uv(data[o + 3], data[o + 4])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(normal, nx, ny, nz)
                    .endVertex()
            }
        }
    }
}
