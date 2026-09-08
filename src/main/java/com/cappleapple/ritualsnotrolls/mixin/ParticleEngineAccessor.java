package com.cappleapple.ritualsnotrolls.mixin;

import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Construct a native visual without adding an uncontrolled second particle to the engine. */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
  @Invoker("makeParticle")
  <T extends ParticleOptions> Particle ritualsnotrolls$makeParticle(
      T options, double x, double y, double z, double dx, double dy, double dz);
}
