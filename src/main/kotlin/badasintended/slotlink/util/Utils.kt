package badasintended.slotlink.util

import badasintended.slotlink.Slotlink
import io.netty.buffer.Unpooled
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.Packet
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import org.slf4j.LoggerFactory

typealias BlockEntityBuilder = (BlockPos, BlockState) -> BlockEntity

fun BlockPos.toArray(): IntArray {
    return intArrayOf(x, y, z)
}

fun IntArray.toPos(): BlockPos {
    return BlockPos(get(0), get(1), get(2))
}

fun PlayerEntity.actionBar(key: String, vararg args: Any) {
    sendMessage(Text.translatable(key, *args), true)
}

fun buf(): PacketByteBuf {
    return PacketByteBuf(Unpooled.buffer())
}

/**
 * Generates [VoxelShape] based on the position that shows on [Blockbench](https://blockbench.net).
 * No thinking required!
 */
fun bbCuboid(xPos: Int, yPos: Int, zPos: Int, xSize: Int, ySize: Int, zSize: Int): VoxelShape {
    val xMin = xPos / 16.0
    val yMin = yPos / 16.0
    val zMin = zPos / 16.0
    val xMax = (xPos + xSize) / 16.0
    val yMax = (yPos + ySize) / 16.0
    val zMax = (zPos + zSize) / 16.0
    return VoxelShapes.cuboid(xMin, yMin, zMin, xMax, yMax, zMax)
}

fun Direction.next(): Direction {
    return Direction.byId(id + 1)
}

fun PacketByteBuf.writeFilter(filter: List<ObjBoolPair<ItemStack>>) {
    filter.forEach {
        writeItemStack(it.first)
        writeBoolean(it.second)
    }
}

fun PacketByteBuf.readFilter(size: Int = 9): MutableList<ObjBoolPair<ItemStack>> {
    val list = arrayListOf<ObjBoolPair<ItemStack>>()
    for (i in 0 until size) {
        list.add(readItemStack() to readBoolean())
    }
    return list
}

fun modId(path: String) = Identifier(Slotlink.ID, path)

@Suppress("unused")
val log = LoggerFactory.getLogger(Slotlink.ID)!!

inline fun s2c(player: PlayerEntity, id: Identifier, buf: PacketByteBuf.() -> Unit) {
    player as ServerPlayerEntity
    ServerPlayNetworking.send(player, id, buf().apply(buf))
}

fun s2c(player: PlayerEntity, packet: Packet<*>) {
    player as ServerPlayerEntity
    ServerPlayNetworking.getSender(player).sendPacket(packet)
}

val ignoredTag: TagKey<Block> = TagKey.of(RegistryKeys.BLOCK, modId("ignored"))

fun ItemStack.isItemAndTagEqual(other: ItemStack): Boolean {
    return ItemStack.canCombine(this, other)
}

fun ItemStack.merge(from: ItemStack): Pair<ItemStack, ItemStack> {
    val f = from.copy()
    val t = this.copy()

    if (isEmpty) return f to ItemStack.EMPTY
    if (!isItemAndTagEqual(f) || count >= maxCount || f.isEmpty) return t to f

    val max = (maxCount - count).coerceAtLeast(0)
    val added = min(max, f.count)

    t.increment(added)
    f.decrement(added)

    return t to f
}

fun Pair<ItemStack, ItemStack>.allEmpty() = first.isEmpty && second.isEmpty

var ObjIntPair<Inventory>.stack: ItemStack
    get() = first.getStack(second)
    set(value) = first.setStack(second, value)


fun Int.toFormattedString(): String = when {
    this < 1000 -> "$this"
    else -> {
        val exp = (ln(this.toDouble()) / ln(1000.0)).toInt()
        String.format("%.1f%c", this / 1000.0.pow(exp.toDouble()), "KMGTPE"[exp - 1])
    }
}

inline fun modLoaded(modid: String, action: () -> Unit) {
    if (FabricLoader.getInstance().isModLoaded(modid)) action()
}
