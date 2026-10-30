package org.lantern.internal.handler

import net.lingala.zip4j.ZipFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 原版扫描 resourcepacks/ 时用 java.util.zip 打开 zip，只要有一个加密条目就整包报 invalid CEN header；
 * 加密包由 [EncryptedPackLoader] 自己解密，原版扫描阶段直接跳过。
 * 原版在 Minecraft 构造期间就会扫描，这里不能依赖 Minecraft 实例
 */
object EncryptedZips {

    private class Stamp(val size: Long, val modified: Long, val encrypted: Boolean)

    private val stamps = ConcurrentHashMap<Path, Stamp>()

    @JvmStatic
    fun isEncrypted(path: Path): Boolean {
        if (path.fileSystem != FileSystems.getDefault()) return false
        if (!path.fileName.toString().endsWith(".zip", ignoreCase = true)) return false
        return try {
            val file = path.toAbsolutePath()
            if (!Files.isRegularFile(file)) return false
            val size = Files.size(file)
            val modified = Files.getLastModifiedTime(file).toMillis()
            stamps[file]?.takeIf { it.size == size && it.modified == modified }?.let { return it.encrypted }
            val encrypted = ZipFile(file.toFile()).use { it.isEncrypted }
            stamps[file] = Stamp(size, modified, encrypted)
            encrypted
        } catch (_: Exception) {
            false
        }
    }
}
