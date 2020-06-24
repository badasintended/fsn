package badasintended.slotlink.block

import badasintended.slotlink.block.entity.RequestBlockEntity
import badasintended.slotlink.common.openScreen
import badasintended.slotlink.common.tag2Pos
import badasintended.slotlink.common.writeRequestData
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.world.World

class RequestBlock : ChildBlock("request") {

    override fun createBlockEntity(view: BlockView): BlockEntity = RequestBlockEntity()

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand,
        hit: BlockHitResult
    ): ActionResult {
        if (!world.isClient) {
            val blockEntity = world.getBlockEntity(pos)!!
            val nbt = blockEntity.toTag(CompoundTag())

            openScreen("request", player) { buf ->
                val hasMaster = nbt.getBoolean("hasMaster")
                buf.writeBlockPos(pos)
                buf.writeInt(nbt.getInt("lastSort"))
                if (hasMaster) {
                    val masterPos = tag2Pos(nbt.getCompound("masterPos"))
                    writeRequestData(buf, world.server, world.dimension.type, masterPos)
                    /*
                    var totalInventory = 0
                    val inventoryPos = HashSet<BlockPos>()

                    val masterNbt = world.getBlockEntity(masterPos)!!.toTag(CompoundTag())
                    val linkCables = masterNbt.getList("linkCables", NbtType.COMPOUND)
                    linkCables.forEach { linkCablePosTag ->
                        linkCablePosTag as CompoundTag
                        val linkCablePos = tag2Pos(linkCablePosTag)
                        val linkCableBlock = world.getBlockState(linkCablePos).block

                        if (linkCableBlock is LinkCableBlock) {
                            val linkCableNbt = world.getBlockEntity(linkCablePos)!!.toTag(CompoundTag())
                            val linkedPos = tag2Pos(linkCableNbt.getCompound("linkedPos"))
                            val linkedBlock = world.getBlockState(linkedPos).block

                            if (linkedBlock.hasBlockEntity()) {
                                val linkedBlockEntity = world.getBlockEntity(linkedPos)
                                if (hasInventory(linkedBlockEntity)) {
                                    totalInventory++
                                    inventoryPos.add(linkedPos)
                                }
                            }
                        }
                    }

                    buf.writeInt(totalInventory)
                    inventoryPos.forEach { buf.writeBlockPos(it) }

                     */
                }
            }
        }
        return ActionResult.SUCCESS
    }

}
