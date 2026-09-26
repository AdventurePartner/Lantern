package org.lantern.uix.renderer

/**
 * Slot 坐标写入桥。Slot.x/y 是 public final 字段（渲染/命中/拖拽全链路实时读取），
 * 经 Mixin @Accessor + @Mutable（SlotAccessor）实现写入。
 * SlotLayoutManager 据此把原版槽位移动到 uix 自定义布局位置，
 * 原版的点击、拖拽、shift 快移、tooltip 随之在新位置全部生效。
 */
interface SlotMutableBridge {
    fun `lantern$setSlotX`(value: Int)

    fun `lantern$setSlotY`(value: Int)
}
