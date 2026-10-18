package org.lantern.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class PacketSchemasTest {

    /**
     * 字段表按位置编码：服务端和客户端只要有一边的表不同，解出来的就是错位的值。
     * 改了 PacketSchemas 就把 LanternProtocol.PROTOCOL_VERSION 加一，再把这里两个值一起更新，
     * 新旧两端靠版本号互相拒收，而不是静默读错
     */
    @Test
    fun `schema changes come with a protocol version bump`() {
        assertEquals(2, LanternProtocol.PROTOCOL_VERSION)
        assertEquals("285bf081", PacketSchemas.fingerprint())
    }
}
