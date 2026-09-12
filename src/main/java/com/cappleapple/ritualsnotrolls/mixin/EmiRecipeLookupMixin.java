package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.compat.viewer.EmiKnowledgePlugin;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EMI has no dynamic lookup provider API; keep this optional adapter confined to its manager. */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiRecipes$Manager", remap = false)
public abstract class EmiRecipeLookupMixin {
  @Inject(method = "getRecipesByInput", at = @At("RETURN"), cancellable = true)
  private void ritualsnotrolls$uses(EmiStack stack, CallbackInfoReturnable<List<EmiRecipe>> cir) {
    cir.setReturnValue(EmiKnowledgePlugin.lookup(cir.getReturnValue(), stack, true));
  }

  @Inject(method = "getRecipesByOutput", at = @At("RETURN"), cancellable = true)
  private void ritualsnotrolls$recipes(
      EmiStack stack, CallbackInfoReturnable<List<EmiRecipe>> cir) {
    cir.setReturnValue(EmiKnowledgePlugin.lookup(cir.getReturnValue(), stack, false));
  }
}
