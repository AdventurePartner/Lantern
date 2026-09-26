package org.lantern.uix.renderer

/**
 * AbstractContainerScreen 面板几何信息的访问桥。
 * 由 Mixin accessor（AbstractContainerScreenAccessor）实现，
 * overlay 据此把布局原点锚定到容器面板左上角。
 */
interface ContainerPanelBridge {
    fun `lantern$getLeftPos`(): Int

    fun `lantern$getTopPos`(): Int

    fun `lantern$getImageWidth`(): Int

    fun `lantern$getImageHeight`(): Int
}
