package org.lantern.internal.chat

import net.minecraft.client.GuiMessage

interface ChatComponentBridge {
    fun `lantern$getAllMessages`(): MutableList<GuiMessage>

    fun `lantern$refreshTrimmedMessages`()
}
