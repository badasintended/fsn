package badasintended.slotlink.item

import badasintended.slotlink.util.actionBar
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.RegistryKey
import net.minecraft.world.World

open class UnlimitedRemoteItem(id: String = "unlimited_remote") : MultiDimRemoteItem(id) {

    override val level = 2

    override fun use(
        world: World,
        player: PlayerEntity,
        stack: ItemStack,
        remoteSlot: Int,
        masterPos: BlockPos,
        masterDim: RegistryKey<World>
    ) {
        if (world.registryKey != masterDim) {
            player.actionBar("${baseTlKey}.differentDimension")
        } else super.use(world, player, stack, remoteSlot, masterPos, masterDim)
    }

}
