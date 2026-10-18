package org.lantern.core.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

/** 按 bukkit NetworkHandler 的写法拼出来的样例包（字段名、Java 数字类型都照抄） */
object PacketSamples {
    private val ENTITY = UUID.fromString("5f1c3a2e-8b4d-4c1a-9e2f-0a1b2c3d4e5f").toString()
    private val PLAYER = UUID.fromString("0b6e2c4d-1f3a-4b5c-8d7e-9f0a1b2c3d4e").toString()

    fun all(): List<Pair<Int, JsonObject>> = listOf(
        1 to characters(30),
        2 to entityModels(),
        3 to keys(),
        4 to itemIcons(),
        5 to uiScreens(),
        6 to obj { it.addProperty("screen-id", "main_menu") },
        7 to obj { it.addProperty("key", "s3cr3t-key") },
        8 to costumes(),
        9 to costumeAssignments(),
        10 to blockModels(),
        11 to blockPositions(50),
        12 to blockUpdate("add"),
        12 to blockUpdate("remove"),
        13 to placeholder(),
        14 to chatChannels(),
        15 to animationControl(),
        15 to obj {
            it.addProperty("uuid", ENTITY)
            it.addProperty("action", "seek")
            it.addProperty("animation", "walk")
            it.addProperty("transition", 0)
            it.addProperty("mode", "loop")
            it.addProperty("speed", 1.0f)
            it.addProperty("time", 1.25f)
        },
        17 to molang(),
        18 to obj {
            it.addProperty("action", "shoulder")
            it.addProperty("enabled", true)
            it.addProperty("offset-x", -0.75)
            it.addProperty("offset-y", 0.2)
            it.addProperty("distance", 4.0)
        },
        18 to cameraLock(),
        18 to obj {
            it.addProperty("action", "lock")
            it.addProperty("entity", ENTITY)
            it.addProperty("smooth", 0.3)
            it.addProperty("duration", 0.0)
            it.addProperty("sync", false)
        },
        18 to obj { it.addProperty("action", "unlock") },
        18 to obj {
            it.addProperty("action", "shake")
            it.addProperty("amplitude", 0.3)
            it.addProperty("frequency", 8.0)
            it.addProperty("duration", 0.5)
            it.addProperty("decay", true)
        },
        18 to obj {
            it.addProperty("action", "fov")
            it.addProperty("value", 90.0)
            it.addProperty("transition", 1.0)
        },
        18 to obj {
            it.addProperty("action", "offset")
            it.addProperty("pitch", 0.0)
            it.addProperty("yaw", 12.5)
            it.addProperty("roll", -3.0)
            it.addProperty("transition", 2.0)
        },
        18 to cameraPath(),
        18 to obj {
            it.addProperty("action", "watch")
            it.add("pos", xyz(10.5, 70.0, -3.25))
            it.add("look", xyz(0.0, 64.0, 0.0))
            it.addProperty("duration", 3.0)
            it.addProperty("smooth", 1.0)
        },
        // 动作轨道的相机参数直接来自 YAML：数字可能是 Integer 也可能是 Double
        18 to obj {
            it.addProperty("action", "shake")
            it.addProperty("amplitude", 0.2)
            it.addProperty("frequency", 10)
            it.addProperty("duration", 1)
        },
        19 to playerActions(),
        20 to obj {
            it.addProperty("follower", ENTITY)
            it.addProperty("host", PLAYER)
            val offset = JsonArray()
            offset.add(0.0)
            offset.add(1.8)
            offset.add(-0.25)
            it.add("offset", offset)
            it.addProperty("rotate", true)
            it.addProperty("visible", true)
            it.addProperty("durationMs", 0L)
        },
        20 to obj { it.addProperty("follower", ENTITY) },
        21 to obj {
            it.addProperty("id", "skill:dash")
            val locks = JsonArray()
            locks.add("move")
            locks.add("jump")
            it.add("locks", locks)
            it.addProperty("durationMs", 1500L)
        },
        21 to obj {
            it.addProperty("clear", true)
            it.addProperty("id", "skill:dash")
        },
        22 to worldImages(),
        23 to obj {
            it.addProperty("action", "spawn")
            val images = JsonArray()
            images.add(obj { image ->
                image.addProperty("id", "marker-1")
                image.addProperty("template", "quest_marker")
                image.add("pos", xyz(1.5, 65.0, -20.5))
            })
            it.add("images", images)
        },
        23 to obj { it.addProperty("action", "clear") },
        99 to obj { it.addProperty("full", false) }
    )

    fun sized(): List<Triple<String, Int, JsonObject>> = listOf(
        Triple("op 15 animation play", 15, animationControl()),
        Triple("op 12 block add", 12, blockUpdate("add")),
        Triple("op 17 molang", 17, molang()),
        Triple("op 18 camera lock", 18, cameraLock()),
        Triple("op 1 characters x30", 1, characters(30)),
        Triple("op 2 entity models x20", 2, entityModels(20)),
        Triple("op 11 block positions x1000", 11, blockPositions(1000)),
        Triple("op 5 ui screens", 5, uiScreens(10))
    )

    fun animationControl(): JsonObject = obj {
        it.addProperty("uuid", ENTITY)
        it.addProperty("action", "play")
        it.addProperty("animation", "attack")
        it.addProperty("transition", 5)
        it.addProperty("mode", "once")
        it.addProperty("speed", 1.0f)
        it.addProperty("layer", "combat")
        it.addProperty("seq", 1234L)
        it.addProperty("exit", 3)
    }

    fun entityModels(count: Int = 3): JsonObject {
        val array = JsonArray()
        repeat(count) { index ->
            array.add(obj {
                it.addProperty("name", "§c骷髅王 $index")
                it.addProperty("geo", "geo/boss/skeleton_king.geo.json")
                it.addProperty("texture", "textures/entity/boss/skeleton_king.png")
                if (index == 1) {
                    it.addProperty("animation", "animations/boss/skeleton_king.animation.json")
                } else {
                    it.add("animations", obj { animations ->
                        animations.addProperty("file", "animations/boss/skeleton_king.animation.json")
                        animations.add("states", obj { states ->
                            states.addProperty("idle", "idle")
                            states.addProperty("walk", "walk")
                            states.add("attack", obj { state ->
                                state.addProperty("animation", "attack_heavy")
                                state.addProperty("mode", "once")
                                state.addProperty("transition", 3)
                            })
                        })
                    })
                }
                it.addProperty("scale", 1.5)
                it.addProperty("height", 2.4)
                it.addProperty("width", 0.9)
                it.addProperty("hidden", false)
                it.addProperty("offset-y", 0.0)
            })
        }
        return obj { it.add("models", array) }
    }

    fun uiScreens(screenCount: Int = 2): JsonObject {
        val screens = JsonArray()
        repeat(screenCount) { screenIndex ->
            screens.add(obj { screen ->
                screen.addProperty("id", "screen_$screenIndex")
                screen.addProperty("screen-type", if (screenIndex == 0) "hud" else "gui")
                screen.addProperty("match-title", "§6背包 $screenIndex")
                screen.add("styles", obj { styles ->
                    styles.add(".title", obj { rule ->
                        rule.addProperty("color", "#FFD700")
                        rule.addProperty("font-size", 12)
                    })
                })
                screen.add("root", node("panel", 0, depth = 0))
            })
        }
        return obj { it.add("screens", screens) }
    }

    private fun node(type: String, index: Int, depth: Int): JsonObject = obj { element ->
        element.addProperty("type", type)
        element.addProperty("id", "node_${depth}_$index")
        element.addProperty("x", index * 18)
        element.addProperty("y", depth * 20)
        element.addProperty("width", 160)
        element.addProperty("height", 18)
        element.addProperty("texture", "lantern:textures/gui/slot_$index.png")
        element.addProperty("text", "{health} / 20")
        element.addProperty("opacity", 0.85)
        element.add("style", obj { style ->
            style.addProperty("margin-top", 2)
            style.addProperty("display", "flex")
        })
        if (depth < 2) {
            val children = JsonArray()
            repeat(4) { children.add(node(if (it % 2 == 0) "image" else "text", it, depth + 1)) }
            element.add("children", children)
        }
    }

    fun blockPositions(count: Int): JsonObject {
        val array = JsonArray()
        repeat(count) { index ->
            array.add(obj {
                it.addProperty("x", 1200 + index % 40)
                it.addProperty("y", 64 + index % 5)
                it.addProperty("z", -800 - index / 40)
                it.addProperty("v", index % 7)
            })
        }
        return obj { it.add("positions", array) }
    }

    fun blockUpdate(action: String): JsonObject = obj {
        it.addProperty("action", action)
        it.addProperty("x", 1234)
        it.addProperty("y", 70)
        it.addProperty("z", -5678)
        if (action == "add") it.addProperty("v", 4)
    }

    fun molang(): JsonObject = obj {
        it.addProperty("uuid", ENTITY)
        it.add("vars", obj { vars ->
            vars.addProperty("v.charge", "0.75")
            vars.addProperty("v.speed", "query.ground_speed * 2")
        })
    }

    fun cameraLock(): JsonObject = obj {
        it.addProperty("action", "lock")
        it.add("target", xyz(105.5, 72.0, -38.25))
        it.addProperty("smooth", 0.3)
        it.addProperty("duration", 2.0)
        it.addProperty("sync", false)
    }

    private fun characters(count: Int): JsonObject {
        val array = JsonArray()
        repeat(count) { index ->
            array.add(obj {
                it.addProperty("character", (0xE000 + index).toChar().toString())
                it.addProperty("texture", "lantern:font/icons/icon_$index.png")
                it.addProperty("width", 9.0)
                it.addProperty("height", 9.0)
                it.addProperty("wide", 8.0)
                it.addProperty("offset-x", 0.0)
                it.addProperty("offset-y", if (index % 3 == 0) -1.5 else 0.0)
            })
        }
        return obj { it.add("characters", array) }
    }

    private fun keys(): JsonObject {
        val array = JsonArray()
        listOf("key.keyboard.r" to true, "key.keyboard.left.alt" to false, "ctrl+k" to true).forEach { (key, press) ->
            array.add(obj {
                it.addProperty("key", key)
                it.addProperty("press", press)
            })
        }
        return obj { it.add("keys", array) }
    }

    private fun itemIcons(): JsonObject {
        val array = JsonArray()
        repeat(5) { index ->
            array.add(obj {
                it.addProperty("data", 10001 + index)
                it.addProperty("identifier", "minecraft:paper")
                it.addProperty("texture", "item/icons/sword_$index")
                it.addProperty("type", if (index == 0) "handheld" else "generated")
            })
        }
        return obj { it.add("icons", array) }
    }

    private fun costumes(): JsonObject {
        val array = JsonArray()
        array.add(obj {
            it.addProperty("id", "player_default")
            it.addProperty("display-name", "§b默认外观")
            it.addProperty("geo", "geo/player/default.geo.json")
            it.addProperty("texture", "textures/player/default.png")
            it.add("animations", obj { animations ->
                animations.addProperty("file", "animations/player/default.animation.json")
                animations.add("states", obj { states ->
                    states.addProperty("idle", "idle")
                    states.add("jump", obj { state ->
                        state.addProperty("animation", "jump")
                        state.addProperty("mode", "hold")
                    })
                })
                val bones = JsonArray()
                bones.add("torso")
                bones.add("left_arm")
                animations.add("upper-body-bones", bones)
            })
            it.addProperty("host-driven", true)
            it.addProperty("scale", 1.0)
            it.add("offset", xyz(0.0, 0.0, 0.0))
            it.addProperty("slot", "full_body")
            it.addProperty("bone-sync", true)
            it.add("bone-mapping", obj { mapping ->
                mapping.addProperty("head", "head")
                mapping.addProperty("body", "body")
                mapping.addProperty("left_arm", "left_arm")
                mapping.addProperty("right_arm", "right_arm")
                mapping.addProperty("left_leg", "left_leg")
                mapping.addProperty("right_leg", "right_leg")
            })
        })
        return obj { it.add("costumes", array) }
    }

    private fun costumeAssignments(): JsonObject = obj {
        val assignments = JsonArray()
        assignments.add(obj { entry ->
            entry.addProperty("uuid", PLAYER)
            val costumes = JsonArray()
            costumes.add(obj { slot ->
                slot.addProperty("slot", "full_body")
                slot.addProperty("costume", "player_default")
            })
            entry.add("costumes", costumes)
        })
        it.add("assignments", assignments)
        val removals = JsonArray()
        removals.add(obj { removal ->
            removal.addProperty("uuid", ENTITY)
            removal.addProperty("slot", "head")
        })
        removals.add(obj { removal -> removal.addProperty("uuid", PLAYER) })
        it.add("removals", removals)
    }

    private fun blockModels(): JsonObject {
        val array = JsonArray()
        repeat(3) { index ->
            array.add(obj {
                it.addProperty("id", "table_$index")
                it.addProperty("custom_variation", index + 1)
                if (index > 0) it.addProperty("custom_model_data", 20000 + index)
                it.addProperty("geo", "geo/block/table.geo.json")
                it.addProperty("texture", "textures/block/table.png")
                it.addProperty("animation", "animations/block/table.animation.json")
                it.addProperty("scale", 1.0f)
                it.addProperty("idle_animation", "idle")
                it.addProperty("block_scale", 0.9f)
                it.addProperty("item_offset_x", 0.0f)
                it.addProperty("item_offset_y", 0.1f)
                it.addProperty("item_offset_z", 0.0f)
                it.addProperty("hardness", 1.5f)
                if (index == 2) it.addProperty("preferred_tool", "axe")
                if (index == 2) it.addProperty("break_sound", "minecraft:block.wood.break")
            })
        }
        return obj { it.add("blocks", array) }
    }

    private fun placeholder(): JsonObject = obj {
        it.addProperty("screen-id", "hud_main")
        it.add("values", obj { values ->
            values.addProperty("%player_name%", "Steve")
            values.addProperty("%vault_eco_balance%", "1,024.50")
        })
    }

    private fun chatChannels(): JsonObject {
        val channels = JsonArray()
        channels.add(obj {
            it.addProperty("id", "global")
            it.addProperty("display-name", "§a全服")
            val prefixes = JsonArray()
            prefixes.add("!")
            it.add("prefixes", prefixes)
            val filter = JsonArray()
            filter.add("[全服]")
            it.add("filter", filter)
            it.add("style", obj { style ->
                style.addProperty("active-color", "#55FF55")
                style.addProperty("width", 48)
            })
        })
        channels.add(obj {
            it.addProperty("id", "team")
            it.addProperty("display-name", "team")
            it.add("prefixes", JsonArray())
            it.add("filter", JsonArray())
        })
        return obj {
            it.add("channels", channels)
            it.add("style", obj { style ->
                style.add("window", obj { window ->
                    window.addProperty("offset-x", 4)
                    window.addProperty("background", "#80000000")
                    window.addProperty("line-spacing", 1.5)
                    window.addProperty("shadow", true)
                })
            })
        }
    }

    private fun cameraPath(): JsonObject = obj {
        it.addProperty("action", "path")
        it.addProperty("speed", 1.0)
        it.addProperty("loop", false)
        val frames = JsonArray()
        repeat(4) { index ->
            frames.add(obj { frame ->
                frame.addProperty("t", index * 20)
                frame.addProperty("x", 100.5 + index)
                frame.addProperty("y", 70.0)
                frame.addProperty("z", -40.25)
                frame.addProperty("yaw", 90.0f + index * 15)
                frame.addProperty("pitch", -10.5f)
                if (index == 2) frame.addProperty("fov", 60.0)
                frame.addProperty("interp", if (index == 3) "hold" else "smooth")
            })
        }
        it.add("keyframes", frames)
    }

    private fun playerActions(): JsonObject {
        val actions = JsonArray()
        actions.add(obj {
            it.addProperty("id", "翻滚")
            it.addProperty("key", "left_alt")
            it.addProperty("file", "animations/player/roll.animation.json")
            it.addProperty("transition", 2)
            it.addProperty("exit-transition", 5)
            it.addProperty("speed", 1.0)
            it.addProperty("cooldown", 800L)
            it.addProperty("layer", "motion")
            it.addProperty("uninterruptible", true)
            it.addProperty("exclusive", true)
            it.addProperty("suppress-vanilla-attack", 0L)
            it.addProperty("invulnerable", 500L)
            it.addProperty("airborne", true)
            it.addProperty("require-ground", false)
            it.addProperty("distance", 4.0)
            it.addProperty("dash-duration", 300L)
            it.addProperty("vertical", 0.0)
            it.addProperty("trigger", "key")
            it.addProperty("server-checked", false)
            it.add("directions", obj { directions ->
                directions.addProperty("forward", "前翻滚")
                directions.addProperty("backward", "后翻滚")
                directions.addProperty("none", "前翻滚")
            })
        })
        actions.add(obj {
            it.addProperty("id", "连击")
            it.addProperty("key", "")
            it.addProperty("file", "animations/player/player_default.animation.json")
            it.addProperty("transition", 2)
            it.addProperty("exit-transition", 3)
            it.addProperty("speed", 1.0)
            it.addProperty("cooldown", 0L)
            it.addProperty("layer", "combat")
            it.addProperty("uninterruptible", false)
            it.addProperty("exclusive", false)
            it.addProperty("suppress-vanilla-attack", 400L)
            it.addProperty("invulnerable", 0L)
            it.addProperty("airborne", true)
            it.addProperty("require-ground", false)
            it.addProperty("distance", 0.0)
            it.addProperty("dash-duration", 0L)
            it.addProperty("vertical", 0.0)
            it.addProperty("trigger", "attack")
            it.addProperty("server-checked", true)
            it.addProperty("costume", "player_default")
            it.add("directions", JsonObject())
            val steps = JsonArray()
            listOf("左砍", "右砍", "蓄力砍").forEach { animation ->
                steps.add(obj { step ->
                    step.addProperty("animation", animation)
                    step.addProperty("cancel-at", 0.7)
                    step.addProperty("window", 600L)
                    step.addProperty("transition", 2)
                    step.addProperty("exit-transition", 3)
                })
            }
            it.add("steps", steps)
        })
        return obj { it.add("actions", actions) }
    }

    private fun worldImages(): JsonObject = obj {
        it.add("images", obj { images ->
            images.add("quest_marker", obj { template ->
                template.addProperty("texture", "lantern:textures/world/marker.png")
                template.addProperty("width", 1.0)
                template.addProperty("height", 1.0)
                template.addProperty("billboard", true)
                template.addProperty("animation", "bob")
            })
        })
        it.add("animations", obj { animations ->
            animations.add("bob", obj { animation ->
                animation.addProperty("duration", 40)
                animation.addProperty("loop", true)
                val keyframes = JsonArray()
                listOf(0 to 0.0, 20 to 0.25, 40 to 0.0).forEach { (t, v) ->
                    keyframes.add(obj { frame ->
                        frame.addProperty("t", t)
                        frame.addProperty("v", v)
                    })
                }
                animation.add("offset-y", keyframes)
            })
        })
    }

    private fun xyz(x: Double, y: Double, z: Double): JsonObject = obj {
        it.addProperty("x", x)
        it.addProperty("y", y)
        it.addProperty("z", z)
    }

    private inline fun obj(build: (JsonObject) -> Unit): JsonObject = JsonObject().also(build)
}
