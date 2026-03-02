package org.lantern.internal.storage

import org.lantern.internal.wrapper.key.CharacterWrapper
import org.lantern.internal.wrapper.key.KeyWrapper
import java.util.concurrent.ConcurrentHashMap

class ClientStorage {
    val characters = ConcurrentHashMap<Char, CharacterWrapper>()
    val keyboards = mutableMapOf<String, KeyWrapper>()
    // 存储 itemIcons: customModelData -> (identifier, texturePath)
    val itemIcons = ConcurrentHashMap<Int, Pair<String, String>>()
}