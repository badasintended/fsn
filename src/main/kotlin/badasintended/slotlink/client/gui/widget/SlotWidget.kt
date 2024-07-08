package badasintended.slotlink.client.gui.widget

import badasintended.slotlink.client.util.client
import badasintended.slotlink.client.util.wrap
import badasintended.slotlink.compat.recipe.RecipeViewer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text

@Environment(EnvType.CLIENT)
abstract class SlotWidget<SH : ScreenHandler>(
    x: Int, y: Int, s: Int,
    protected val handler: SH,
    private val stackGetter: () -> ItemStack
) : NoSoundWidget(x, y, s, s, ScreenTexts.EMPTY),
    TooltipRenderer {

    val stack get() = stackGetter.invoke()

    private var stackX = x - 8 + width / 2
    private val stackY = y - 8 + height / 2

    fun offsetX(offset: Int) {
        stackX += offset
    }

    abstract fun onClick(button: Int)

    protected open fun appendTooltip(tooltip: MutableList<Text>) {}

    protected open fun renderOverlay(context: DrawContext, stack: ItemStack) {
        client.apply {
            context.drawItemInSlot(textRenderer, stack, stackX, stackY)
        }
    }

    override fun renderTooltip(context: DrawContext, mouseX: Int, mouseY: Int) {
        context.matrices.wrap {
            context.matrices.translate(0.0, 0.0, +256.0)
            val x = stackX
            val y = stackY
            context.fill(x, y, x + 16, y + 16, -2130706433 /*0x80ffffff fuck*/)
            if (!stack.isEmpty && handler.cursorStack.isEmpty && RecipeViewer.instance?.isDraggingStack != true)
                client.apply {
                    val tooltips = stack.getTooltip(
                        player,
                        TooltipContext.Default(options.advancedItemTooltips, player?.isCreative ?: false)
                    )
                    appendTooltip(tooltips)
                    context.drawTooltip(textRenderer, tooltips, mouseX, mouseY)
                }
        }
    }

    final override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (!visible) return
        context.drawItem(stack, stackX, stackY)
        renderOverlay(context, stack)
    }

    final override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val player = client.player ?: return false
        if (hovered && visible && !player.isSpectator) {
            onClick(button)
            return true
        }
        return false
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder?) {}

}
