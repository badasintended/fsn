package badasintended.slotlink.client.gui.screen

import badasintended.slotlink.client.gui.widget.*
import badasintended.slotlink.client.util.GuiTextures
import badasintended.slotlink.client.util.c2s
import badasintended.slotlink.compat.invsort.InventorySortButton
import badasintended.slotlink.compat.recipe.RecipeViewer
import badasintended.slotlink.config.config
import badasintended.slotlink.init.Packets.CLEAR_CRAFTING_GRID
import badasintended.slotlink.init.Packets.MOVE
import badasintended.slotlink.init.Packets.RESIZE
import badasintended.slotlink.init.Packets.RESTOCK
import badasintended.slotlink.init.Packets.SCROLL
import badasintended.slotlink.init.Packets.SORT
import badasintended.slotlink.screen.RequestScreenHandler
import badasintended.slotlink.screen.slot.LockedSlot
import badasintended.slotlink.util.bool
import badasintended.slotlink.util.enum
import badasintended.slotlink.util.int
import badasintended.slotlink.util.string
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import badasintended.slotlink.util.backgroundHeight as utilBackgroundHeight
import badasintended.slotlink.util.backgroundWidth as utilBackgroundWidth
import badasintended.slotlink.util.x as utilX
import badasintended.slotlink.util.y as utilY

import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget
import net.minecraft.client.gui.widget.TexturedButtonWidget
import net.minecraft.screen.AbstractRecipeScreenHandler
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class RequestScreen<H : RequestScreenHandler>(handler: H, inv: PlayerInventory, title: Text) :
    ModScreen<H>(handler, inv, title), RecipeBookProvider {

    var craftingGrid by config::showCraftingGrid
    private val recipeBook = RecipeBookWidget()
    private val RECIPE_BUTTON_TEXTURE = Identifier("textures/gui/recipe_button.png")

    var arrowX = -1
    var arrowY = -1

    private val syncId by handler::syncId
    private val maxScroll by handler::maxScroll
    private val totalSlots by handler::totalSlotSize
    private val filledSlots by handler::filledSlotSize
    private val viewedHeight by handler::viewedHeight

    private val titleWidth by lazy { textRenderer.getWidth(title) }
    private val craftingText = Text.translatable("container.crafting")

    private lateinit var scrollBar: ScrollBarWidget
    private lateinit var searchBar: TextFieldWidget

    private var lastScroll = 0
    private var filter = ""

    private var skipChar = false

    private var inventorySortButton: ClickableWidget? = null

    override val baseTlKey: String
        get() = "container.slotlink.request"

    override fun init() {
        val craftHeight = if (craftingGrid) 67 else 0

        var viewedHeight = 3
        for (i in 3..6) if (height > (119 + craftHeight + (i * 18))) viewedHeight = i

        backgroundWidth = 9 * 18 + 14
        backgroundHeight = viewedHeight * 18 + 114 + craftHeight

        handler.resize(viewedHeight, craftingGrid)
        c2s(RESIZE) {
            int(syncId)
            int(viewedHeight)
            bool(craftingGrid)
        }

        super.init()

        playerInventoryTitleY = backgroundHeight - 94

        var x = x + 7
        val y = y + titleY + 11

        if (craftingGrid) {
            recipeBook.initialize(width, height, client, width < 379, handler as AbstractRecipeScreenHandler<*>)
            this.x = recipeBook.findLeftEdge(width, backgroundWidth)
            x = this.x + 7
            add(TexturedButtonWidget(
                x - 2, y + viewedHeight * 18 + 27, 20, 18, 0, 0, 19, RECIPE_BUTTON_TEXTURE
            ) {
                recipeBook.toggleOpen()
                val oldX = this.x
                this.x = recipeBook.findLeftEdge(width, backgroundWidth)
                val offset = this.x - oldX
                arrowX += offset
                for (i in children()) {
                    if (i is ClickableWidget) {
                        i.x += offset
                        if (i is SlotWidget<*>) {
                            i.offsetX(offset)
                        }
                    }
                }
            })
            addSelectableChild(recipeBook)
            setInitialFocus(recipeBook)

            // Crafting output slot
            add(CraftingResultSlotWidget(handler, x + 108, y + viewedHeight * 18 + 27))

            // Clear crafting grid button
            add(ButtonWidget(x + 13, y + 18 + viewedHeight * 18, 8)) {
                texture = GuiTextures.REQUEST
                background = false
                u = { 210 }
                v = { 16 }
                tooltip = { tl("craft.clear") }
                onPressed = {
                    c2s(CLEAR_CRAFTING_GRID) {
                        int(syncId)
                    }
                }
            }

            arrowX = x + 83
            arrowY = y + 32 + viewedHeight * 18
        }

        // Linked slot view
        for (i in 0 until viewedHeight * 9) {
            add(MultiSlotWidget(handler, i, x + (i % 9) * 18, y + (i / 9) * 18))
        }

        // Linked slot scroll bar
        scrollBar = add(ScrollBarWidget(x + 4 + 9 * 18, y, viewedHeight * 18)) {
            hasKnob = { maxScroll > 0 }
            onUpdated = {
                val scroll = (it * maxScroll + 0.5).toInt()
                if (scroll != lastScroll) c2s(SCROLL) {
                    int(syncId)
                    int(scroll)
                }
                lastScroll = scroll
            }
        }

        // Sort button
        add(ButtonWidget(x - 29, y, 20)) {
            allowSpectator = true
            texture = GuiTextures.REQUEST
            bgU = 216
            bgV = 32
            u = { 228 }
            v = { config.sort.ordinal * 14 + 52 }
            padding(3)
            tooltip = { tl("sort.${config.sort}") }
            onPressed = {
                config.sort = config.sort.next()
                scrollBar.knob = 0f
                sort()
            }
        }

        // Toggle crafting grid button
        add(ButtonWidget(x - 29, y + 22, 20)) {
            allowSpectator = true
            texture = GuiTextures.REQUEST
            bgU = 216
            bgV = 32
            u = { 200 }
            v = { if (craftingGrid) 52 else 66 }
            padding(3)
            tooltip = { tl("craft.${craftingGrid}") }
            onPressed = {
                craftingGrid = !craftingGrid
                init(client!!, client!!.window.scaledWidth, client!!.window.scaledHeight)
            }
        }

        if (craftingGrid) {
            // Crafting output slot
            add(CraftingResultSlotWidget(handler, x + 108, y + viewedHeight * 18 + 27))

            // Clear crafting grid button
            val clearText = tl("craft.clear")
            add(ButtonWidget(x + 13, y + 18 + viewedHeight * 18, 8)) {
                texture = GuiTextures.REQUEST
                background = false
                u = { 210 }
                v = { 16 }
                tooltip = { clearText }
                onPressed = {
                    c2s(CLEAR_CRAFTING_GRID) {
                        int(syncId)
                    }
                }
            }

            arrowX = x + 83
            arrowY = y + 32 + viewedHeight * 18
        }

        val invSorterW = if (inventorySortButton == null) 0 else 16

        // Move all to network button
        add(ButtonWidget(x + 9 * 18 - 8 - invSorterW, y + viewedHeight * 18 + 3 + craftHeight, 8)) {
            texture = GuiTextures.REQUEST
            background = false
            u = { 194 }
            v = { 16 }
            onPressed = {
                c2s(MOVE) {
                    int(syncId)
                }
            }
            tooltip = {
                if (handler.cursorStack.isEmpty) tl("move.all") else tl("move.clazz")
            }
        }

        // Restock player inventory button
        add(ButtonWidget(x + 9 * 18 - 16 - invSorterW, y + viewedHeight * 18 + 3 + craftHeight, 8)) {
            texture = GuiTextures.REQUEST
            background = false
            u = { 202 }
            v = { 16 }
            onPressed = {
                c2s(RESTOCK) {
                    int(syncId)
                }
            }
            tooltip = {
                if (handler.cursorStack.isEmpty) tl("restock.all") else tl("restock.cursor")
            }
        }

        // Search bar autofocus button
        add(ButtonWidget(x - 29, y + 44, 20)) {
            allowSpectator = true
            texture = GuiTextures.REQUEST
            bgU = 216
            bgV = 32
            u = { 242 }
            v = { if (config.autoFocusSearchBar) 52 else 66 }
            padding(3)
            tooltip = { tl("autoFocus.${config.autoFocusSearchBar}") }
            onPressed = {
                config.autoFocusSearchBar = !config.autoFocusSearchBar
            }
        }

        // Sync to rei button
        RecipeViewer.instance?.also { recipeViewer ->
            add(ButtonWidget(x - 29, y + 66, 20)) {
                allowSpectator = true
                texture = GuiTextures.REQUEST
                bgU = 216
                bgV = 32
                u = { 214 }
                v = { recipeViewer.textureV + (if (config.syncRecipeViewerSearch) 0 else 14) }
                padding(3)
                tooltip = { tl("searchSync.${config.syncRecipeViewerSearch}", recipeViewer.modName) }
                onPressed = {
                    config.syncRecipeViewerSearch = !config.syncRecipeViewerSearch
                }
            }
        }

        // Inventory Sorter's sort button
        inventorySortButton?.apply { add(this) }

        // Search bar
        searchBar = add(TextFieldWidget(x + 9 * 18 - 90, y - 13, 90, 12, tl("search"))) {
            setMaxLength(50)
            text = filter
            tooltip.add(tl("search.tip1"))
            tooltip.add(tl("search.tip2"))
            tooltip.add(tl("search.tip3"))
            setChangedListener {
                if (it != filter) {
                    scrollBar.knob = 0f
                    filter = it
                    sort()
                    if (config.syncRecipeViewerSearch) RecipeViewer.instance?.search(filter)
                }
            }
            if (config.autoFocusSearchBar) {
                grab = true
            }
        }

        sort()
    }

    inline fun <T> bounds(action: (Int, Int, Int, Int) -> T): T {
        return action(utilX - 22, utilY, utilBackgroundWidth + 40, utilBackgroundHeight)
    }

    private fun sort() {
        c2s(SORT) {
            int(syncId)
            enum(config.sort)
            string(filter)
        }
    }

    override fun <T> addSelectableChild(child: T): T where T : Element?, T : Selectable? {
        if (child is ClickableWidget && child is InventorySortButton && !child.initialized) {
            child.initialized = true
            inventorySortButton = child
            return child
        }
        return super.addSelectableChild(child)
    }

    override fun handledScreenTick() {
        super.handledScreenTick()
        if (searchBar.grab) searchBar.tick()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        recipeBook.render(context, mouseX, mouseY, delta)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        super.drawBackground(context, delta, mouseX, mouseY)

        val viewedH = viewedHeight * 18
        context.drawTexture(GuiTextures.REQUEST, x, y, 0, 0, 194, viewedH - 18 + 17)
        context.drawTexture(GuiTextures.REQUEST, x, y + viewedH - 18 + 17, 0, 107, 194, 115)

        if (craftingGrid) {
            context.drawTexture(GuiTextures.CRAFTING, x, y + viewedH + 17 + 7, 0, 0, 176, 157)
        }
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        super.drawForeground(context, mouseX, mouseY)

        handler.slots.forEach {
            if (it is LockedSlot) {
                context.drawTexture(GuiTextures.REQUEST, it.x, it.y, 240, 0, 16, 16)
            }
        }

        if (craftingGrid) {
            context.drawText(textRenderer, craftingText, titleX + 21, playerInventoryTitleY - 67, 0x404040, false)
        }

        if (x + titleX < mouseX && mouseX <= x + titleX + titleWidth && y + titleY < mouseY && mouseY <= y + titleY + textRenderer.fontHeight) {
            context.drawTooltip(textRenderer, tl("slotCount", filledSlots, totalSlots), mouseX - x, mouseY - y)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return if (searchBar.grab) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchBar.grab = false
                true
            } else {
                searchBar.keyPressed(keyCode, scanCode, modifiers)
            }
        } else if (client!!.options.chatKey.matchesKey(keyCode, scanCode)) {
            skipChar = true
            searchBar.grab = true
            true
        } else {
            super.keyPressed(keyCode, scanCode, modifiers)
        }
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        skipChar = false
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun charTyped(char: Char, modifiers: Int): Boolean {
        return if (skipChar) false else super.charTyped(char, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (maxScroll > 0 && mouseX >= x + 7 && mouseX < x + 169 && mouseY >= y + 17 && mouseY < y + 17 + viewedHeight * 18) {
            scrollBar.knob = (scrollBar.knob - amount / maxScroll).toFloat().coerceIn(0f, 1f)
            c2s(SCROLL) {
                int(syncId)
                int((scrollBar.knob * maxScroll + 0.5).toInt())
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun close() {
        config.save()
        super.close()
    }

    override fun refreshRecipeBook() {
        recipeBook.refresh()
    }

    override fun getRecipeBookWidget(): RecipeBookWidget = recipeBook

}
