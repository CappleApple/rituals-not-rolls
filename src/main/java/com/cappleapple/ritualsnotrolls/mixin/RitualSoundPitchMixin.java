package com.cappleapple.ritualsnotrolls.mixin;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla clamps pitch to 0.5; ritual resource entries intentionally include lower pitches. */
@Mixin(SoundEngine.class)
public abstract class RitualSoundPitchMixin {
  @Inject(method = "calculatePitch", at = @At("HEAD"), cancellable = true)
  private void ritualsnotrolls$resourcePitch(
      SoundInstance sound, CallbackInfoReturnable<Float> cir) {
    if (sound.getLocation().getNamespace().equals(RitualsNotRolls.ID))
      cir.setReturnValue(Mth.clamp(sound.getPitch(), .01f, 4f));
  }
}
