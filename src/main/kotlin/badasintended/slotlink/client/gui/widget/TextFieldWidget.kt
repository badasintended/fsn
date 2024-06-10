package badasintended.slotlink.client.gui.widget

import badasintended.slotlink.client.util.client
import badasintended.slotlink.util.focusedTicks
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

@Environment(EnvType.CLIENT)
class TextFieldWidget(bgX: Int, bgY: Int, bgW: Int, bgH: Int, text: Text) :
    TextFieldWidget(client.textRenderer, bgX + 2, bgY + 2, bgW - 12, bgH - 3, text),
    CharGrabber,
    TooltipRenderer {

    var grab = false
        set(value) {
            field = value
            focusedTicks = 0
        }

    val tooltip = arrayListOf<Text>()

    init {
        setDrawsBackground(false)
        setEditableColor(0xffffff)
    }

    override fun renderTooltip(context: DrawContext, mouseX: Int, mouseY: Int) {
        if (visible && !isActive) context.drawTooltip(client.textRenderer, tooltip, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (hovered) {
            grab = true
            if (isVisible && button == 1) text = ""
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun isActive(): Boolean {
        return grab
    }

    override fun isFocused(): Boolean {
        return grab
    }

    override fun onChar(chr: Char, modifiers: Int): Boolean {
        return charTyped(chr, modifiers)
    }

}
