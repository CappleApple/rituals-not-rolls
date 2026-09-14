package com.cappleapple.ritualsnotrolls.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native visuals on ritual flight paths must not collide independently of their path. */
@Mixin(Particle.class)
public interface ParticleAccessor {
  @Accessor("hasPhysics")
  void ritualsnotrolls$setHasPhysics(boolean hasPhysics);
}
