package org.lantern.platform

import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

object ClientPathBridge {
    @JvmStatic
    fun gameDir(): Path = FMLPaths.GAMEDIR.get()
}
