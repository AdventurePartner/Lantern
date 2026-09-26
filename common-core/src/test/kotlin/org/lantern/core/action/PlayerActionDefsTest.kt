package org.lantern.core.action

import com.google.gson.JsonParser
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerActionDefsTest {

    @BeforeTest
    fun reset() {
        PlayerActionDefs.clear()
    }

    @Test
    fun `方向动作解析与八方向回落链`() {
        val payload = JsonParser.parseString("""
            {"actions": [{"id": "roll", "key": "left_alt", "file": "animations/roll.animation.json",
              "directions": {"forward": "roll_f", "left": "roll_l", "none": "roll_n"},
              "distance": 4.0, "cooldown": 900}]}
        """).asJsonObject
        PlayerActionDefs.load(payload)
        val defs = PlayerActionDefs.definitions()
        assertEquals(1, defs.size)
        val def = defs[0]
        assertEquals("roll", def.id)
        assertEquals(4.0, def.distance)
        assertEquals(900L, def.cooldownMs)
        // 配了的直取
        assertEquals("roll_f", PlayerActionDefs.resolveAnimation(def, "forward"))
        assertEquals("roll_l", PlayerActionDefs.resolveAnimation(def, "left"))
        // 斜向没配：回落相邻主方向
        assertEquals("roll_f", PlayerActionDefs.resolveAnimation(def, "forward_left"))
        assertEquals("roll_f", PlayerActionDefs.resolveAnimation(def, "forward_right"))
        // 后向没配：回落 none
        assertEquals("roll_n", PlayerActionDefs.resolveAnimation(def, "backward_left"))
        assertEquals("roll_n", PlayerActionDefs.resolveAnimation(def, "backward"))
    }

    @Test
    fun `连招条目按 trigger 与 costume 解析`() {
        val payload = JsonParser.parseString("""
            {"actions": [
              {"id": "combo_a", "file": "anim/combo.json", "trigger": "attack", "costume": "sword",
               "steps": [{"animation": "l1", "cancel-at": 0.6, "window": 700},
                          {"animation": "l2"}]},
              {"id": "combo_fallback", "file": "anim/combo.json", "trigger": "attack",
               "steps": [{"animation": "x"}]}
            ]}
        """).asJsonObject
        PlayerActionDefs.load(payload)
        // 外观限定优先
        val sword = PlayerActionDefs.attackCombo("sword")
        assertNotNull(sword)
        assertEquals("combo_a", sword.id)
        assertEquals(0.6f, sword.steps[0].cancelAt)
        assertEquals(700L, sword.steps[0].windowMs)
        // 未限定外观回落不限定的那条；非 hostDriven（null）不启用连招
        assertEquals("combo_fallback", PlayerActionDefs.attackCombo("bow")!!.id)
        assertNull(PlayerActionDefs.attackCombo(null))
    }

    @Test
    fun `缺 directions 或 key 的条目被丢弃`() {
        val payload = JsonParser.parseString("""
            {"actions": [
              {"id": "no_key", "file": "a.json", "directions": {"forward": "x"}},
              {"id": "no_dirs", "key": "r", "file": "a.json"}
            ]}
        """).asJsonObject
        PlayerActionDefs.load(payload)
        assertTrue(PlayerActionDefs.definitions().isEmpty())
    }

    @Test
    fun `clear 作废定义`() {
        PlayerActionDefs.load(JsonParser.parseString(
            "{\"actions\": [{\"id\": \"roll\", \"key\": \"left_alt\", \"file\": \"a.json\", \"directions\": {\"none\": \"x\"}}]}"
        ).asJsonObject)
        PlayerActionDefs.clear()
        assertTrue(PlayerActionDefs.definitions().isEmpty())
    }
}
