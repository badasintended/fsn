package badasintended.slotlink.init

import badasintended.slotlink.item.LimitedRemoteItem
import badasintended.slotlink.item.ModItem
import badasintended.slotlink.item.MultiDimRemoteItem
import badasintended.slotlink.item.UnlimitedRemoteItem
import badasintended.slotlink.util.modId
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registries.ITEM
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.text.Text

@Suppress("MemberVisibilityCanBePrivate", "unused")
object Items : Initializer {

    val GROUP: RegistryKey<ItemGroup> = RegistryKey.of(RegistryKeys.ITEM_GROUP, modId("group"))

    val ITEMS = arrayListOf<ModItem>()

    val MULTI_DIM_REMOTE = MultiDimRemoteItem()
    val UNLIMITED_REMOTE = UnlimitedRemoteItem()
    val LIMITED_REMOTE = LimitedRemoteItem()

    override fun main() {
        r(MULTI_DIM_REMOTE, UNLIMITED_REMOTE, LIMITED_REMOTE)

        Registry.register(Registries.ITEM_GROUP, GROUP, FabricItemGroup.builder()
            .displayName(Text.literal("Slotlink"))
            .icon { ItemStack(Blocks.MASTER) }
            .entries { _, entries ->
                Blocks.BLOCKS.forEach { entries.add(ItemStack(it)) }
                ITEMS.forEach { entries.add(ItemStack(it)) }
            }
            .build()!!)
    }

    private fun r(vararg items: ModItem) {
        items.forEach {
            Registry.register(ITEM, it.id, it)
            ITEMS.add(it)
        }
    }

}
