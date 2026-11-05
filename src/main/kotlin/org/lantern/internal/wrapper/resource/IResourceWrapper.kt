package org.lantern.internal.wrapper.resource

import net.minecraft.resources.ResourceLocation
import java.io.InputStream

interface IResourceWrapper {

    fun getResourceLocation(): ResourceLocation

    fun getResource(): InputStream

    /**
     * 服务前检查底层资源是否仍可用（本地包文件可能在运行期被改名/删除）。
     * 不可用时按资源缺失处理，让原版走 FileNotFoundException → missingno 兜底，
     * 而不是把 NoSuchFileException 泄进资源重载 future 炸掉整轮重载。
     */
    fun isAvailable(): Boolean = true
}