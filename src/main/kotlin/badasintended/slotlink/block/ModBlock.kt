package badasintended.slotlink.block

import badasintended.slotlink.util.modId
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.MapColor

abstract class ModBlock(id: String, settings: Settings = SETTINGS) : BlockWithEntity(settings) {

    companion object {

        val SETTINGS: Settings = FabricBlockSettings
            .create()
            .mapColor(MapColor.WHITE)
            .hardness(5f)

    }

    val id = modId(id)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getRenderType(state: BlockState?): BlockRenderType {
        return BlockRenderType.MODEL
    }

}
