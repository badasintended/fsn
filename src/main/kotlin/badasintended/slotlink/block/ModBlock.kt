package badasintended.slotlink.block

import badasintended.slotlink.Mod
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.tool.attribute.v1.FabricToolTags
import net.minecraft.block.Block
import net.minecraft.block.Material
import net.minecraft.client.item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.world.BlockView

abstract class ModBlock(id: String, settings: Settings = SETTINGS) : Block(settings) {

    companion object {
        val SETTINGS: Settings = FabricBlockSettings
            .of(Material.STONE)
            .breakByHand(true)
            .breakByTool(FabricToolTags.PICKAXES)
            .hardness(5f)
    }

    val id = Mod.id(id)

    override fun buildTooltip(stack: ItemStack, view: BlockView?, tooltip: MutableList<Text>, options: TooltipContext) {
        tooltip.add(LiteralText("§7").append(TranslatableText("${translationKey}.tooltip")))
    }

}