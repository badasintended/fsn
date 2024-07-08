package badasintended.slotlink.mixin;

import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.CraftingScreenHandler;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingScreenHandler.class)
public interface CraftingScreenHandlerAccessor {

    @NotNull
    @Accessor
    RecipeInputInventory getInput();

    @NotNull
    @Accessor
    CraftingResultInventory getResult();

}
