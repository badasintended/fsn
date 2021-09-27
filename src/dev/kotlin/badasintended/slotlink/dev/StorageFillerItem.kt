package badasintended.slotlink.dev

import badasintended.slotlink.item.ModItem
import kotlin.random.Random
import kotlin.random.asJavaRandom
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.text.LiteralText
import net.minecraft.util.ActionResult
import net.minecraft.util.math.Direction
import net.minecraft.util.registry.Registry

object StorageFillerItem : Item(ModItem.SETTINGS) {

    private val random = Random.asJavaRandom()

    override fun hasGlint(stack: ItemStack?): Boolean {
        return true
    }

    @Suppress("DEPRECATION", "UnstableApiUsage")
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val world = context.world
        val player = context.player ?: return ActionResult.FAIL

        if (world.isClient) return ActionResult.SUCCESS

        val pos = context.blockPos
        val storage = ItemStorage.SIDED.find(world, pos, Direction.UP)

        if (storage != null) Transaction.openOuter().use { transaction ->
            while (true) {
                val item = Registry.ITEM.getRandom(random)
                if (storage.insert(ItemVariant.of(item), item.maxCount.toLong(), transaction) == 0L) break
            }
            transaction.commit()
            player.sendMessage(LiteralText("Filled (${pos.x}, ${pos.y}, ${pos.z})"), true)
        }

        return ActionResult.SUCCESS
    }

}