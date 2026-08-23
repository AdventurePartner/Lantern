package org.lantern.platform

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

object ClientPathBridge {
    @JvmStatic
    fun gameDir(): Path = FabricLoader.getInstance().gameDir
}

