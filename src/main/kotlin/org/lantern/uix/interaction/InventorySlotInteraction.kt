package org.lantern.uix.interaction

import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lantern.uix.canvas.impl.GuiCanvas
import org.lantern.uix.event.EventDispatcher
import org.lantern.uix.input.FocusManager
import org.lantern.uix.widget.IWidget
import org.lantern.uix.widget.slot.SlotWidgetImpl
import kotlin.math.min

/**
 * gui 界面（GuiCanvas）里 slot 节点的背包交互：点击、Shift 快移、双击收集、拖拽分配、
 * 数字键/副手键交换、丢弃，逻辑照搬原版 AbstractContainerScreen（不含触屏模式与点到界面外丢出）。
 *
 * 操作的是玩家背包菜单：服务端没开别的容器时它的当前菜单就是背包菜单，
 * 原版打开背包也不通知服务端，所以点击包直接按背包菜单的容器编号发即可。
 */
object InventorySlotInteraction {

    private const val DOUBLE_CLICK_MS = 250L
    private const val OFFHAND_SWAP_BUTTON = 40
    private const val OUTSIDE_SLOT = -999

    private var root: IWidget? = null
    private var hovered: SlotWidgetImpl? = null
    private var interacted = false

    private val quickCraftSlots = LinkedHashSet<Slot>()
    private var quickCrafting = false
    private var quickCraftType = 0
    private var quickCraftButton = 0
    private var quickCraftRemainder = 0
    private var skipNextRelease = true
    private var doubleClick = false
    private var lastClickSlot: Slot? = null
    private var lastClickButton = -1
    private var lastClickTime = 0L
    private var lastQuickMoved: ItemStack = ItemStack.EMPTY

    /** GuiCanvas 每帧布局完成后调用：记下鼠标下方的 slot，供按键与物品提示使用。 */
    fun frame(root: IWidget, mouseX: Int, mouseY: Int) {
        this.root = root
        hovered = slotWidgetAt(root, mouseX.toDouble(), mouseY.toDouble())
    }

    /** 返回 true 表示点在 slot 上并已处理，GuiCanvas 不再把这次点击分发给其它组件。 */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, shift: Boolean, pickKey: Boolean): Boolean {
        val menu = menu() ?: return false
        val player = Minecraft.getInstance().player ?: return false
        val slot = slotAt(mouseX, mouseY, menu)
        if (slot == null) {
            doubleClick = false
            lastClickSlot = null
            skipNextRelease = true
            return false
        }

        val now = System.currentTimeMillis()
        doubleClick = lastClickSlot === slot && lastClickButton == button && now - lastClickTime < DOUBLE_CLICK_MS
        lastClickSlot = slot
        lastClickButton = button
        lastClickTime = now
        skipNextRelease = false

        if (button != 0 && button != 1 && !(pickKey && player.abilities.instabuild)) {
            skipNextRelease = true
            return true
        }
        if (quickCrafting) return true

        if (menu.carried.isEmpty) {
            when {
                pickKey -> click(menu, slot, button, ClickType.CLONE)
                shift -> {
                    lastQuickMoved = if (slot.hasItem()) slot.item.copy() else ItemStack.EMPTY
                    click(menu, slot, button, ClickType.QUICK_MOVE)
                }
                else -> click(menu, slot, button, ClickType.PICKUP)
            }
            skipNextRelease = true
        } else {
            quickCrafting = true
            quickCraftButton = button
            quickCraftSlots.clear()
            quickCraftType = when (button) {
                0 -> 0
                1 -> 1
                else -> 2
            }
        }
        return true
    }

    fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
        if (!quickCrafting) return false
        val menu = menu() ?: return false
        val slot = slotAt(mouseX, mouseY, menu) ?: return true
        val carried = menu.carried
        if (!carried.isEmpty
            && (carried.count > quickCraftSlots.size || quickCraftType == 2)
            && AbstractContainerMenu.canItemQuickReplace(slot, carried, true)
            && slot.mayPlace(carried)
            && menu.canDragTo(slot)
        ) {
            quickCraftSlots.add(slot)
            recalculateRemainder(menu)
        }
        return true
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int, shift: Boolean, pickKey: Boolean): Boolean {
        val menu = menu()
        val player = Minecraft.getInstance().player
        if (menu == null || player == null) {
            clearDrag()
            return false
        }
        val slot = slotAt(mouseX, mouseY, menu)

        if (doubleClick && slot != null && button == 0 && menu.canTakeItemForPickAll(ItemStack.EMPTY, slot)) {
            if (shift) {
                if (!lastQuickMoved.isEmpty) {
                    for (other in menu.slots) {
                        if (other.mayPickup(player)
                            && other.hasItem()
                            && other.container === slot.container
                            && AbstractContainerMenu.canItemQuickReplace(other, lastQuickMoved, true)
                        ) {
                            click(menu, other, button, ClickType.QUICK_MOVE)
                        }
                    }
                }
            } else {
                click(menu, slot, button, ClickType.PICKUP_ALL)
            }
            doubleClick = false
        } else {
            if (quickCrafting && quickCraftButton != button) {
                clearDrag()
                skipNextRelease = true
                return true
            }
            if (skipNextRelease) {
                skipNextRelease = false
                return true
            }
            if (quickCrafting && quickCraftSlots.isNotEmpty()) {
                send(menu, OUTSIDE_SLOT, AbstractContainerMenu.getQuickcraftMask(0, quickCraftType), ClickType.QUICK_CRAFT)
                for (target in quickCraftSlots) {
                    click(menu, target, AbstractContainerMenu.getQuickcraftMask(1, quickCraftType), ClickType.QUICK_CRAFT)
                }
                send(menu, OUTSIDE_SLOT, AbstractContainerMenu.getQuickcraftMask(2, quickCraftType), ClickType.QUICK_CRAFT)
            } else if (!menu.carried.isEmpty && slot != null) {
                if (pickKey) {
                    click(menu, slot, button, ClickType.CLONE)
                } else {
                    if (shift) lastQuickMoved = if (slot.hasItem()) slot.item.copy() else ItemStack.EMPTY
                    click(menu, slot, button, if (shift) ClickType.QUICK_MOVE else ClickType.PICKUP)
                }
            }
        }
        clearDrag()
        return true
    }

    /** [matches] 由各平台按自己的按键事件判断某个按键绑定是否被按下。 */
    fun keyPressed(matches: (KeyMapping) -> Boolean, control: Boolean): Boolean {
        if (FocusManager.focused != null) return false
        val menu = menu() ?: return false
        val player = Minecraft.getInstance().player ?: return false
        val slot = hovered?.resolve(menu) ?: return false
        val options = Minecraft.getInstance().options

        if (menu.carried.isEmpty) {
            if (matches(options.keySwapOffhand)) {
                click(menu, slot, OFFHAND_SWAP_BUTTON, ClickType.SWAP)
                return true
            }
            for (i in 0 until 9) {
                if (matches(options.keyHotbarSlots[i])) {
                    click(menu, slot, i, ClickType.SWAP)
                    return true
                }
            }
        }
        if (!slot.hasItem()) return false
        if (matches(options.keyPickItem) && player.abilities.instabuild) {
            click(menu, slot, 0, ClickType.CLONE)
            return true
        }
        if (matches(options.keyDrop)) {
            click(menu, slot, if (control) 1 else 0, ClickType.THROW)
            return true
        }
        return false
    }

    /** 拖拽分配途经的格子：预计放下后的物品，以及到达堆叠上限时的黄色数量文字。 */
    fun dragPreview(widget: SlotWidgetImpl): Pair<ItemStack, String?>? {
        if (!quickCrafting || quickCraftSlots.size <= 1) return null
        val menu = menu() ?: return null
        val slot = widget.resolve(menu) ?: return null
        if (slot !in quickCraftSlots) return null
        val carried = menu.carried
        if (carried.isEmpty || !AbstractContainerMenu.canItemQuickReplace(slot, carried, true) || !menu.canDragTo(slot)) {
            return null
        }
        val limit = min(carried.maxStackSize, slot.getMaxStackSize(carried))
        val existing = if (slot.item.isEmpty) 0 else slot.item.count
        val count = AbstractContainerMenu.getQuickCraftPlaceCount(quickCraftSlots, quickCraftType, carried) + existing
        return if (count > limit) {
            carried.copyWithCount(limit) to ChatFormatting.YELLOW.toString() + limit
        } else {
            carried.copyWithCount(count) to null
        }
    }

    /** 跟随鼠标画的手上物品；拖拽分配时只显示还没分出去的数量。 */
    fun carriedDisplay(): Pair<ItemStack, String?>? {
        val menu = menu() ?: return null
        val carried = menu.carried
        if (carried.isEmpty) return null
        if (quickCrafting && quickCraftSlots.size > 1) {
            val rest = carried.copyWithCount(quickCraftRemainder)
            return rest to if (rest.isEmpty) ChatFormatting.YELLOW.toString() + "0" else null
        }
        return carried to null
    }

    /** 鼠标停在 [root] 里有物品的 slot 上、手上没拿东西时要显示提示的物品。 */
    fun hoveredItem(root: IWidget): ItemStack? {
        if (root !== this.root) return null
        val menu = menu() ?: return null
        if (!menu.carried.isEmpty) return null
        return hovered?.resolve(menu)?.item?.takeIf { !it.isEmpty }
    }

    /**
     * GuiCanvas.removed 时调用。交互过且当前仍是背包菜单才通知服务端关闭，让它把手上和合成格里的物品放回背包；
     * 不能用 closeContainer()，它会在 setScreen 过程中再次 setScreen。
     */
    fun close() {
        val player = Minecraft.getInstance().player
        if (interacted && player != null && player.containerMenu === player.inventoryMenu) {
            player.connection.send(ServerboundContainerClosePacket(player.inventoryMenu.containerId))
        }
        root = null
        hovered = null
        interacted = false
        clearDrag()
        skipNextRelease = true
        doubleClick = false
        lastClickSlot = null
        lastClickButton = -1
        lastQuickMoved = ItemStack.EMPTY
    }

    private fun menu(): InventoryMenu? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        if (mc.screen !is GuiCanvas || player.isSpectator) return null
        val menu = player.inventoryMenu
        return if (player.containerMenu === menu) menu else null
    }

    private fun slotWidgetAt(root: IWidget, mouseX: Double, mouseY: Double): SlotWidgetImpl? =
        EventDispatcher.findDeepest(root, mouseX.toInt(), mouseY.toInt()) {
            it is SlotWidgetImpl && it.slotIndex >= 0
        } as? SlotWidgetImpl

    private fun slotAt(mouseX: Double, mouseY: Double, menu: InventoryMenu): Slot? {
        val root = root ?: return null
        return slotWidgetAt(root, mouseX, mouseY)?.resolve(menu)
    }

    private fun recalculateRemainder(menu: InventoryMenu) {
        val carried = menu.carried
        if (carried.isEmpty) return
        if (quickCraftType == 2) {
            quickCraftRemainder = carried.maxStackSize
            return
        }
        quickCraftRemainder = carried.count
        for (slot in quickCraftSlots) {
            val existing = if (slot.item.isEmpty) 0 else slot.item.count
            val limit = min(carried.maxStackSize, slot.getMaxStackSize(carried))
            val placed = min(AbstractContainerMenu.getQuickCraftPlaceCount(quickCraftSlots, quickCraftType, carried) + existing, limit)
            quickCraftRemainder -= placed - existing
        }
    }

    private fun clearDrag() {
        quickCrafting = false
        quickCraftSlots.clear()
    }

    private fun click(menu: InventoryMenu, slot: Slot, button: Int, type: ClickType) =
        send(menu, slot.index, button, type)

    private fun send(menu: InventoryMenu, slotId: Int, button: Int, type: ClickType) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        interacted = true
        mc.gameMode?.handleInventoryMouseClick(menu.containerId, slotId, button, type, player)
    }
}
