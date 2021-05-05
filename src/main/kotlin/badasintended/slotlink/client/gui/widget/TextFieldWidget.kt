package badasintended.slotlink.client.gui.widget

import badasintended.slotlink.client.util.bindGuiTexture
import badasintended.slotlink.client.util.client
import badasintended.slotlink.client.util.drawNinePatch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.LiteralText
import net.minecraft.text.Text

@Environment(EnvType.CLIENT)
class TextFieldWidget(
    private val bgX: Int,
    private val bgY: Int,
    private val bgW: Int,
    private val bgH: Int, text: Text
) : TextFieldWidget(client.textRenderer, bgX + 3, bgY + 3, bgW - 5, bgH - 5, text) {

    var placeholder: Text = LiteralText.EMPTY

    val tooltip = arrayListOf<Text>()

    init {
        setHasBorder(false)
        setEditableColor(0xffffff)
    }

    override fun renderButton(matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float) {
        if (!visible) return
        bindGuiTexture()

        drawNinePatch(matrices, bgX, bgY, bgW, bgH, 16f, 0f, 1, 14)

        if (text.isBlank() && !isActive) drawTextWithShadow(matrices, client.textRenderer, placeholder, x, y, 0xffffff)

        super.renderButton(matrices, mouseX, mouseY, delta)
    }

    override fun renderToolTip(matrices: MatrixStack, mouseX: Int, mouseY: Int) {
        if (visible && !isActive) client.currentScreen?.renderTooltip(matrices, tooltip, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (hovered && isVisible && button == 1) text = ""
        return super.mouseClicked(mouseX, mouseY, button)
    }

}
