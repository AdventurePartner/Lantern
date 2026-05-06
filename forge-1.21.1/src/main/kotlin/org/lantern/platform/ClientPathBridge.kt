package org.lantern.platform

import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

object ClientPathBridge {
    @JvmStatic
    fun gameDir(): Path = FMLPaths.GAMEDIR.get()
}

