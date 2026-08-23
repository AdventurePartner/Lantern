package org.lantern.internal.handler

import org.lantern.platform.ClientPathBridge
import java.nio.file.Files
import java.nio.file.Path

internal object ResourcePackPaths {

    fun resourcePacksDir(): Path {
        val gameDir = ClientPathBridge.gameDir()
        val lowercase = gameDir.resolve("resourcepacks")
        if (Files.exists(lowercase)) {
            return lowercase
        }
        return gameDir.resolve("resourcePacks")
    }
}
