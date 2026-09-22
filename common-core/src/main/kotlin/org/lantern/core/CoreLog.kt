package org.lantern.core

import java.util.logging.Logger

/**
 * common-core 的日志出口。
 *
 * 本模块零 Minecraft 依赖，引用不到各平台的 Lantern 主对象；
 * 下沉到这里的存储/解析类统一经它打日志。平台侧日志仍走各自的 Lantern.logger
 */
object CoreLog {
    val logger: Logger = Logger.getLogger("Lantern")
}
