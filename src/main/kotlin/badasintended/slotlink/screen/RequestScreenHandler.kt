package badasintended.slotlink.screen

import badasintended.slotlink.block.entity.BlockEntityWatcher
import badasintended.slotlink.block.entity.MasterBlockEntity
import badasintended.slotlink.block.entity.RequestBlockEntity
import badasintended.slotlink.init.Packets.UPDATE_CURSOR
import badasintended.slotlink.init.Packets.UPDATE_MAX_SCROLL
import badasintended.slotlink.init.Packets.UPDATE_SLOT_NUMBERS
import badasintended.slotlink.init.Packets.UPDATE_VIEWED_STACK
import badasintended.slotlink.init.Screens
import badasintended.slotlink.recipe.fastRecipeManager
import badasintended.slotlink.screen.slot.LockedSlot
import badasintended.slotlink.screen.view.ItemView
import badasintended.slotlink.screen.view.toView
import badasintended.slotlink.storage.FilteredItemStorage
import badasintended.slotlink.storage.NetworkStorage
import badasintended.slotlink.util.actionBar
import badasintended.slotlink.util.allEmpty
import badasintended.slotlink.util.cursorStorage
import badasintended.slotlink.util.input
import badasintended.slotlink.util.int
import badasintended.slotlink.util.isEmpty
import badasintended.slotlink.util.item
import badasintended.slotlink.util.merge
import badasintended.slotlink.util.nbt
import badasintended.slotlink.util.result
import badasintended.slotlink.util.s2c
import badasintended.slotlink.util.stack
import badasintended.slotlink.util.storage
import java.util.*
import kotlin.collections.set
import kotlin.math.ceil
import kotlin.math.min
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.CraftingInventory
import net.minecraft.inventory.CraftingResultInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.recipe.CraftingRecipe
import net.minecraft.recipe.Ingredient
import net.minecraft.recipe.Recipe
import net.minecraft.recipe.RecipeGridAligner
import net.minecraft.recipe.RecipeType
import net.minecraft.registry.Registries
import net.minecraft.screen.CraftingScreenHandler
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerListener
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.CraftingResultSlot
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.screen.slot.SlotActionType.CLONE
import net.minecraft.screen.slot.SlotActionType.PICKUP
import net.minecraft.screen.slot.SlotActionType.QUICK_MOVE
import net.minecraft.screen.slot.SlotActionType.SWAP
import net.minecraft.screen.slot.SlotActionType.THROW
import net.minecraft.server.network.ServerPlayerEntity

@Suppress("UnstableApiUsage")
open class RequestScreenHandler(
    syncId: Int,
    val playerInventory: PlayerInventory,
    private val storage: NetworkStorage,
) : CraftingScreenHandler(syncId, playerInventory),
    MasterBlockEntity.Watcher,
    BlockEntityWatcher<RequestBlockEntity>,
    RecipeGridAligner<Ingredient> {

    companion object {

        private val whitespaceRegex = Regex("\\s+")

    }

    val player: PlayerEntity = playerInventory.player

    private val filledViews = arrayListOf<ItemView>()

    private val trackedViews = ArrayList<ItemView>(54)
    val itemViews = ArrayList<ItemView>(54)

    private var lastSortData = SortData(SortMode.NAME, "")
    private var scheduledSortData: SortData? = null

    var viewedHeight = 0

    var maxScroll = 0
    private var lastScroll = 0

    private var request: RequestBlockEntity? = null
    private var master: MasterBlockEntity? = null

    private val visualStorages = hashMapOf<FilteredItemStorage, List<ItemView>>()

    var totalSlotSize = 0
    var filledSlotSize = 0

    init {
        for (i in 0 until 54) {
            itemViews.add(ItemStack.EMPTY.toView())
        }
    }

    /** Client side **/
    constructor(syncId: Int, playerInventory: PlayerInventory) : this(
        syncId, playerInventory, NetworkStorage(arrayListOf())
    )

    /** Server side **/
    @Suppress("LeakingThis")
    constructor(
        syncId: Int,
        playerInventory: PlayerInventory,
        storage: NetworkStorage,
        request: RequestBlockEntity?,
        master: MasterBlockEntity
    ) : this(syncId, playerInventory, storage) {
        this.request = request
        this.master = master

        val uniqueDifferentiators = HashSet<Any>()
        storage.parts.forEach { part ->
            if (uniqueDifferentiators.add(part.differentiator)) {
                visualStorages[part] = part
                    .map { it.toView() }
            }
        }

        for (i in 0 until 54) {
            trackedViews.add(ItemStack.EMPTY.toView())
        }

        addListener(object : ScreenHandlerListener {
            override fun onPropertyUpdate(handler: ScreenHandler, property: Int, value: Int) {}

            override fun onSlotUpdate(handler: ScreenHandler, slotId: Int, stack: ItemStack) {
                s2c(player, ScreenHandlerSlotUpdateS2CPacket(syncId, nextRevision(), slotId, stack))
            }
        })
    }

    fun scheduleSort(mode: SortMode, filter: String) {
        scheduledSortData = SortData(mode, filter)
    }

    fun scroll(amount: Int) {
        val scroll = amount.coerceIn(0, maxScroll)

        for (i in 0 until viewedHeight * 9) {
            val stack = filledViews.getOrElse(i + 9 * scroll) { ItemView.EMPTY }
            itemViews[i].update(stack)
        }

        lastScroll = scroll
    }

    /** server only **/
    fun multiSlotAction(i: Int, data: Int, type: SlotActionType) {
        val view = itemViews[i]
        val variant = view.toVariant()
        var cursor = cursorStack

        if (cursor.isEmpty) {
            if (type == CLONE) {
                if (player.abilities.creativeMode && cursor.isEmpty) cursor = view.toStack(view.item.maxCount)
            } else {
                if (type == THROW) {
                    val all = data == 1
                    Transaction.openOuter().use { transaction ->
                        val extracted = storage
                            .extract(variant, if (all) variant.item.maxCount.toLong() else 1, transaction)
                        if (extracted > 0) {
                            player.storage.drop(variant, extracted, transaction)
                        }
                        transaction.commit()
                    }
                } else if (type != SWAP || !view.isItemAndTagEqual(playerInventory.getStack(data))) {
                    when (type) {
                        SWAP -> Transaction.openOuter().use { transaction ->
                            val slot = player.storage.getSlot(data)
                            val extracted = storage.extract(variant, slot.capacity - slot.amount, transaction)
                            slot.insert(variant, extracted, transaction)
                            transaction.commit()
                        }

                        QUICK_MOVE -> Transaction.openOuter().use { transaction ->
                            val stock = storage.simulateExtract(variant, variant.item.maxCount.toLong(), transaction)
                            val inserted = player.storage.offer(variant, stock, transaction)
                            storage.extract(variant, inserted, transaction)
                            transaction.commit()
                        }

                        else -> if (!variant.isBlank) Transaction.openOuter().use { transaction ->
                            val max = min(view.count.toLong(), variant.item.maxCount.toLong())
                            val request = if (data == 1) (max + 1) / 2 else max
                            val extracted = storage.extract(variant, request, transaction)
                            cursorStorage.insert(variant, extracted, transaction)
                            cursor = cursorStorage.resource.toStack(cursorStorage.amount.toInt())
                            transaction.commit()
                        }
                    }
                }
            }
        } else if (type == PICKUP) {
            cursor = moveStackToNetwork(cursor)
        }

        updateCursor(cursor)
    }

    fun craftingResultSlotClick(button: Int, quickMove: Boolean) {
        if (button !in 0..2) return

        var cursor = cursorStack
        val resultStack = result.getStack(0)

        if (resultStack.isEmpty) return

        if (button == 2) {
            if (player.abilities.creativeMode && cursor.isEmpty) cursor = resultStack.copy().apply { count = maxCount }
        } else {
            while (true) {
                val merged = cursor.merge(resultStack)
                if (!merged.second.isEmpty || merged.allEmpty()) break

                cursor = merged.first
                resultStack.onCraft(player.world, player, resultStack.count)

                val remainingStacks = player.world.fastRecipeManager
                    .getRemainingStacks(RecipeType.CRAFTING, input, player.world)

                var finished = false
                for (i in remainingStacks.indices) {
                    val remainingStack = remainingStacks[i]
                    val inputStack = input.getStack(i)
                    if (!inputStack.isEmpty) {
                        if (inputStack.count != 1) {
                            inputStack.decrement(1)
                            player.moveOrNetworkOrDrop(remainingStack, true)
                        } else {
                            val variant = ItemVariant.of(inputStack)
                            var extracted = false
                            Transaction.openOuter().use { transaction ->
                                if (storage.extract(variant, 1, transaction) == 1L) {
                                    extracted = true
                                }
                                transaction.commit()
                            }
                            if (extracted) player.moveOrNetworkOrDrop(remainingStack, true)
                            else {
                                inputStack.decrement(1)
                                if (inputStack.isEmpty) input.setStack(i, remainingStack)
                                else player.moveOrNetworkOrDrop(remainingStack, true)
                                finished = true
                                continue
                            }
                        }
                    }
                }
                result.unlockLastRecipe(player)
                result.setStack(0, ItemStack.EMPTY)
                onContentChanged(input)
                if (!quickMove || finished) break
            }
            if (quickMove) {
                player.moveOrNetworkOrDrop(cursor, true)
            }
        }
        updateCursor(cursor)
    }

    fun applyRecipe(recipe: Recipe<*>) {
        if (recipe.type == RecipeType.CRAFTING) {
            clearCraftingGrid()
            sendContentUpdates()
            alignRecipeToGrid(3, 3, -1, recipe, recipe.ingredients.iterator(), 0)
        }
    }

    fun clearCraftingGrid(toPlayerInventory: Boolean = false) {
        for (i in 1..9) slots[i].apply {
            if (toPlayerInventory) insertItem(stack, 10, 46, false)
            stack = moveStackToNetwork(stack)
        }
    }

    fun move() {
        var cursor = cursorStack
        slots.forEach {
            if (it.inventory is PlayerInventory
                && it.index >= 9
                && it.canTakeItems(player)
                && (cursor.isEmpty || cursor.isItemEqual(it.stack))
            ) {
                it.stack = moveStackToNetwork(it.stack)
            }
        }

        if (!cursor.isEmpty) {
            cursor = moveStackToNetwork(cursor)
        }

        updateCursor(cursor)
    }

    fun restock() {
        var cursor = cursorStack
        if (cursor.isEmpty) slots.filter { it.inventory is PlayerInventory }.forEach {
            it.stack = it.stack.restock()
        } else {
            cursor = cursor.restock()
        }

        updateCursor(cursor)
    }

    open fun resize(viewedHeight: Int, craft: Boolean) {
        val coerced = viewedHeight.coerceIn(3, 6)
        val h = coerced * 18 + 23

        val craftH = if (craft) 67 else 0

        slots.clear()

        addSlotOnly(CraftingResultSlot(playerInventory.player, this.input, this.result, 0, -999999, -999999 + h))

        for (m in 0 until 3) for (l in 0 until 3) addSlotOnly(
            if (craft) Slot(input, l + m * 3, 30 + l * 18, 8 + m * 18 + h)
            else LockedSlot(input, l + m * 3)
        )

        for (m in 0 until 3) for (l in 0 until 9) addSlotOnly(
            Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 9 + craftH + m * 18 + h)
        )

        for (m in 0 until 9) addSlotOnly(
            Slot(playerInventory, m, 8 + m * 18, 67 + craftH + h)
        )

        this.viewedHeight = coerced
    }

    private fun addSlotOnly(slot: Slot): Slot {
        slot.id = slots.size
        slots.add(slot)
        return slot
    }

    private fun updateCursor(stack: ItemStack) {
        cursorStack = stack
        s2c(player, UPDATE_CURSOR) {
            stack(stack)
        }
    }

    private fun moveStackToNetwork(stack: ItemStack): ItemStack {
        if (stack.isEmpty) return stack
        val variant = ItemVariant.of(stack)
        var count = stack.count.toLong()

        Transaction.openOuter().use { transaction ->
            count -= storage.insert(variant, count, transaction)
            transaction.commit()
        }

        return variant.toStack(count.toInt())
    }

    private fun moveStackToPlayerOrNetwork(player: PlayerEntity, stack: ItemStack): ItemStack {
        if (stack.isEmpty) return stack

        Transaction.openOuter().use { transaction ->
            val variant = ItemVariant.of(stack)
            val count = stack.count.toLong()
            val offered = player.storage.offer(variant, count, transaction)
            if (offered > 0L) {
                stack.decrement(offered.toInt())
                transaction.commit()
            }
        }

        return moveStackToNetwork(stack)
    }

    private fun PlayerEntity.moveOrNetworkOrDrop(stack: ItemStack, retainOwnership: Boolean) {
        dropItem(moveStackToPlayerOrNetwork(this, stack), retainOwnership)
        stack.count = 0
    }

    private fun ItemStack.restock(max: Int = 64): ItemStack {
        if (isEmpty) return ItemStack.EMPTY

        val stack = copy()
        val variant = ItemVariant.of(stack)
        val space = (min(stack.maxCount, max) - stack.count).toLong()

        Transaction.openOuter().use { transaction ->
            val extracted = storage.extract(variant, space, transaction).toInt()
            stack.count += extracted
            transaction.commit()
        }

        return stack
    }

    private fun onRemoved(key: String) {
        if (player is ServerPlayerEntity) {
            s2c(player, CloseScreenS2CPacket(syncId))
            player.actionBar("container.slotlink.request.$key")
        }
    }

    private fun sort(sortData: SortData) {
        sortData.mode.sort(filledViews)

        if (lastSortData != sortData) scroll(0) else scroll(lastScroll)
        lastSortData = sortData

        s2c(player, UPDATE_SLOT_NUMBERS) {
            int(syncId)
            int(totalSlotSize)
            int(filledSlotSize)
        }
    }

    override fun onSlotClick(i: Int, j: Int, actionType: SlotActionType, playerEntity: PlayerEntity) {
        if (playerEntity !is ServerPlayerEntity) return
        super.onSlotClick(i, j, actionType, playerEntity)
        s2c(playerEntity, UPDATE_CURSOR) {
            stack(cursorStack)
        }
    }

    override fun quickMove(player: PlayerEntity, index: Int): ItemStack {
        val inventory = slots[index].inventory
        var stack = ItemStack.EMPTY
        when (inventory) {
            is CraftingResultInventory -> {
                stack = super.quickMove(player, index)
            }

            is CraftingInventory -> {
                super.quickMove(player, index)
                stack = moveStackToNetwork(slots[index].stack)
            }

            is PlayerInventory -> {
                stack = moveStackToNetwork(slots[index].stack)
                slots[index].stack = stack
                stack = super.quickMove(player, index)
            }
        }
        return stack
    }

    override fun acceptAlignedInput(inputs: Iterator<Ingredient>, slot: Int, amount: Int, gridX: Int, gridY: Int) {
        val ingredient = inputs.next()
        if (ingredient.isEmpty) return

        val matchingVariant = ingredient.matchingStacks.map { ItemVariant.of(it) }

        Transaction.openOuter().use { transaction ->
            for (variant in matchingVariant) {
                val extracted = storage.extract(variant, 1L, transaction)
                if (extracted > 0L) {
                    input.setStack(slot, variant.toStack())
                    transaction.commit()
                    return
                }
            }
        }

        val stack = slots
            .firstOrNull { it.inventory is PlayerInventory && it.canTakeItems(player) && ingredient.test(it.stack) }
            ?.takeStack(1) ?: return

        input.setStack(slot, stack)
    }

    override fun onContentChanged(inventory: Inventory) {
        if (inventory is CraftingInventory) if (player is ServerPlayerEntity) {
            var stack = ItemStack.EMPTY
            val optional: Optional<CraftingRecipe> =
                player.server.fastRecipeManager.getFirstMatch(RecipeType.CRAFTING, input, player.world)
            if (optional.isPresent) {
                val craftingRecipe = optional.get()
                if (result.shouldCraftRecipe(player.world, player, craftingRecipe)) {
                    stack = craftingRecipe.craft(input, player.world.registryManager)
                }
            }
            result.setStack(0, stack)
            s2c(player, ScreenHandlerSlotUpdateS2CPacket(syncId, nextRevision(), 0, stack))
        }
    }

    override fun sendContentUpdates() {
        super.sendContentUpdates()

        if (player !is ServerPlayerEntity) return

        var resort = false

        visualStorages.forEach { entry ->
            val storage = entry.key
            val caches = entry.value

            for ((i, view) in storage.withIndex()) {
                val cacheView = caches[i]
                if (!cacheView.isItemAndTagEqual(view)) {
                    if (cacheView.isEmpty && !view.isEmpty) {
                        filledSlotSize++
                    } else if (!cacheView.isEmpty && view.isEmpty) {
                        filledSlotSize--
                    }

                    val beforeId = filledViews.indexOfFirst { cacheView.isItemAndTagEqual(it) }
                    if (beforeId >= 0) {
                        val beforeMatch = filledViews[beforeId]
                        beforeMatch.count -= cacheView.count
                        if (beforeMatch.isEmpty) {
                            filledViews.removeAt(beforeId)
                        }
                    }

                    if (!view.isEmpty && lastSortData.filters.all { it.match(view) }) {
                        val afterMatch = filledViews.firstOrNull { it.isItemAndTagEqual(view) }
                        if (afterMatch == null) {
                            filledViews.add(view.toView())
                        } else {
                            afterMatch.count += view.amount.toInt()
                        }
                    }

                    resort = true
                    cacheView.update(view.resource.item, view.resource.nbt?.copy(), view.amount.toInt())
                } else if (cacheView.count != view.amount.toInt()) {
                    val filled = filledViews.firstOrNull { cacheView.isItemAndTagEqual(it) }
                    if (filled != null) {
                        filled.count -= cacheView.count - view.amount.toInt()
                        cacheView.update(view.resource.item, view.resource.nbt?.copy(), view.amount.toInt())
                        resort = true
                    }
                }

            }
        }


        if (resort) sort(lastSortData)

        scheduledSortData?.let { sortData ->
            scheduledSortData = null
            totalSlotSize = 0
            filledSlotSize = 0
            filledViews.clear()

            visualStorages.keys.forEach { storage ->
                storage.forEach { view ->
                    totalSlotSize++
                    if (!view.isEmpty) {
                        filledSlotSize++

                        if (sortData.filters.all { it.match(view) }) {
                            val match = filledViews.firstOrNull { it.isItemAndTagEqual(view) }
                            if (match == null) {
                                filledViews.add(view.toView())
                            } else {
                                match.count += view.amount.toInt()
                            }
                        }
                    }
                }
            }

            sort(sortData)
        }

        itemViews.forEachIndexed { i, after ->
            val before = trackedViews[i]
            if (before != after) {
                s2c(player, UPDATE_VIEWED_STACK) {
                    int(syncId)
                    int(i)
                    item(after.item)
                    nbt(after.nbt)
                    int(after.count)
                }
                before.update(after)
            }
        }

        val max = ceil((filledViews.size / 9f) - viewedHeight).toInt().coerceAtLeast(0)
        if (maxScroll != max) {
            s2c(player, UPDATE_MAX_SCROLL) {
                int(syncId)
                int(max)
            }
            maxScroll = max
            scroll(0)
        }
    }

    override fun canUse(player: PlayerEntity?) = true

    override fun getType(): ScreenHandlerType<*> = Screens.REQUEST

    override fun onClosed(player: PlayerEntity?) {
        if (player !is ServerPlayerEntity) return

        if (!cursorStack.isEmpty) if (player.isAlive && !player.isDisconnected) {
            player.moveOrNetworkOrDrop(cursorStack, false)
        } else {
            player.dropItem(moveStackToNetwork(cursorStack), false)
        }

        // try to move crafting input to network first
        for (i in 0 until input.size()) {
            input.setStack(i, moveStackToNetwork(input.getStack(i)))
        }
        // then move to player inventory, and drop if fail
        dropInventory(player, input)

        request?.watchers?.remove(this)
        request?.markDirty()
        master?.watchers?.remove(this)
        master?.unmarkForcedChunks()
    }

    override fun onMasterRemoved() = onRemoved("brokenMaster")

    override fun onRemoved() = onRemoved("brokenSelf")

    private inner class SortData(
        val mode: SortMode,
        filter: String,
    ) {

        val filters by lazy { filter.trim().split(whitespaceRegex).map { Filter(it) } }

    }

    private inner class Filter(string: String) {

        val first = string.getOrElse(0) { 'w' }
        val term = when (first) {
            '@', '#' -> string.drop(1)
            else -> string
        }

        @Suppress("DEPRECATION")
        fun match(view: StorageView<ItemVariant>): Boolean = term.isBlank() || when (first) {
            '@' -> Registries.ITEM.getId(view.resource.item).toString().contains(term, true)
            '#' -> Registries.ITEM
                .streamTags()
                .anyMatch { it.id.toString().contains(term, true) && view.resource.item.registryEntry.isIn(it) }

            else -> view.resource.toStack().name.string.contains(term, true)
        }

    }

    @Suppress("unused")
    enum class SortMode(
        private val id: String,
        val sort: (ArrayList<ItemView>) -> Any
    ) {

        NAME("name", { it -> it.sortBy { it.singleStack.name.string } }),
        NAME_DESC("name_desc", { it -> it.sortByDescending { it.singleStack.item.name.string } }),

        ID("id", { it -> it.sortBy { Registries.ITEM.getId(it.item).toString() } }),
        ID_DESC("id_desc", { it -> it.sortByDescending { Registries.ITEM.getId(it.item).toString() } }),

        COUNT("count", { it -> it.sortBy { it.count } }),
        COUNT_DESC("count_desc", { it -> it.sortByDescending { it.count } });

        companion object {

            val values = values()

        }

        fun next(): SortMode {
            return values[(ordinal + 1) % values.size]
        }

        override fun toString() = id

    }

}
