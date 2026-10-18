package org.lantern.internal.icon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer

/** 1.21.x 顶点链；Forge 1.20.1 有同 FQN 副本（vertex/.../endVertex） */
object IconVertexEmitter {
    @JvmStatic
    fun emit(pose: PoseStack.Pose, consumer: VertexConsumer, mesh: IconMesh, light: Int, overlay: Int) {
        val data = mesh.data
        for (quad in 0 until mesh.quadCount) {
            val base = quad * IconMesh.STRIDE
            val nx = data[base]
            val ny = data[base + 1]
            val nz = data[base + 2]
            for (vertex in 0 until 4) {
                val o = base + 3 + vertex * 5
                consumer.addVertex(pose, data[o], data[o + 1], data[o + 2])
                    .setColor(-1)
                    .setUv(data[o + 3], data[o + 4])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz)
            }
        }
    }
}
