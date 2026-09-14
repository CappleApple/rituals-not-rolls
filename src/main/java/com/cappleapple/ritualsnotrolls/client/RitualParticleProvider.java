package com.cappleapple.ritualsnotrolls.client;

import com.cappleapple.ritualsnotrolls.mixin.ParticleAccessor;
import com.cappleapple.ritualsnotrolls.mixin.ParticleEngineAccessor;
import com.cappleapple.ritualsnotrolls.ritual.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Delegates shape/motion/sprite selection to the requested Minecraft/mod particle provider. */
public final class RitualParticleProvider implements ParticleProvider<RitualParticleOptions> {
  @Override
  public Particle createParticle(
      RitualParticleOptions options,
      ClientLevel level,
      double x,
      double y,
      double z,
      double dx,
      double dy,
      double dz) {
    var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(options.effect()).orElse(null);
    int rgb = options.rgb();
    float r = rgb < 0 ? 1 : ((rgb >> 16) & 255) / 255f,
        g = rgb < 0 ? 1 : ((rgb >> 8) & 255) / 255f,
        b = rgb < 0 ? 1 : (rgb & 255) / 255f;
    ParticleOptions effect =
        type instanceof SimpleParticleType simple
            ? simple
            : options.effect().equals(ResourceLocation.withDefaultNamespace("dust"))
                ? new DustParticleOptions(new Vector3f(r, g, b), 1)
                : ParticleTypes.ENCHANT;
    var particle =
        ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine)
            .ritualsnotrolls$makeParticle(effect, x, y, z, dx, dy, dz);
    if (particle == null) return null;
    if (rgb >= 0) particle.setColor(r, g, b);
    return options.flight().isEmpty()
        ? particle
        : new Flying(
            level,
            new Vec3(x, y, z),
            particle,
            options,
            r,
            g,
            b,
            effect.getType() != ParticleTypes.ENCHANT);
  }

  private static final class Flying extends Particle {
    private final Particle visual;
    private final Vec3 start;
    private final RitualParticleOptions options;
    private final RitualParticleOptions.Flight flight;
    private final float red, green, blue;
    private final double travelLead;
    private final boolean constrainedVisual;
    private final boolean leafVisual;
    private final boolean fullBright;

    Flying(
        ClientLevel level,
        Vec3 start,
        Particle visual,
        RitualParticleOptions options,
        float red,
        float green,
        float blue,
        boolean fullBright) {
      super(level, start.x, start.y, start.z);
      this.start = start;
      this.visual = visual;
      this.options = options;
      flight = options.flight().orElseThrow();
      // Spread packets emitted together over the two-tick interval, along one shared curve.
      travelLead = flight.burst() ? 0 : random.nextDouble() * 2;
      this.red = red;
      this.green = green;
      this.blue = blue;
      this.fullBright = fullBright;
      lifetime =
          flight.burst()
              ? flight.remaining()
              : flight.orbit()
                  ? flight.remaining()
                  : Math.min(
                      Math.max(1, (int) Math.ceil(flight.travel() - travelLead)),
                      flight.remaining());
      if (flight.instability().fails()) lifetime = flight.remaining();
      float scale = flightScale(options.effect());
      if (scale != 1) visual.scale(scale);
      leafVisual = options.effect().equals(ResourceLocation.withDefaultNamespace("cherry_leaves"));
      constrainedVisual = scale != 1 || isFlightFragment(options.effect());
      if (constrainedVisual) ((ParticleAccessor) visual).ritualsnotrolls$setHasPhysics(false);
      // Leaves count down from 300 internally; values above 300 make their wind curve NaN.
      visual.setLifetime(leafVisual ? 300 : lifetime + 2);
      hasPhysics = false;
    }

    /** Keep unusually large native shapes legible within a ritual chain. */
    private static float flightScale(ResourceLocation effect) {
      if (!effect.getNamespace().equals("minecraft")) return 1;
      return switch (effect.getPath()) {
        case "sweep_attack", "gust" -> .16f;
        case "sonic_boom" -> .12f;
        case "explosion" -> .09f;
        case "campfire_cosy_smoke" -> .4f;
        case "cloud", "poof" -> .45f;
        case "heart" -> .7f;
        case "nautilus" -> 2.5f;
        case "vault_connection" -> 3f;
        case "soul", "sculk_soul", "infested" -> 1.6f;
        default -> 1;
      };
    }

    private static boolean isFlightFragment(ResourceLocation effect) {
      if (!effect.getNamespace().equals("minecraft")) return false;
      return switch (effect.getPath()) {
        case "item_cobweb", "item_slime", "item_snowball", "cherry_leaves", "splash" -> true;
        default -> false;
      };
    }

    private Vec3 position(float partial) {
      var source = flight.sourceEntity() < 0 ? null : level.getEntity(flight.sourceEntity());
      Vec3 origin =
          source == null ? start : source.getPosition(partial).add(0, source.getBbHeight() * .6, 0);
      return RitualFlight.position(origin, flight, age + partial, travelLead);
    }

    @Override
    public void tick() {
      xo = x;
      yo = y;
      zo = z;
      if (++age >= lifetime) {
        remove();
        return;
      }
      var p = position(0);
      if (constrainedVisual) visual.setPos(p.x, p.y, p.z);
      if (leafVisual) {
        visual.setLifetime(300 - (int) (300L * (age - 1) / (lifetime + 2L)));
      }
      visual.tick();
      if (constrainedVisual) visual.setPos(p.x, p.y, p.z);
      setPos(p.x, p.y, p.z);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partial) {
      var p = position(partial);
      visual.setPos(p.x, p.y, p.z);
      if (options.rgb() >= 0) visual.setColor(red, green, blue);
      // Native sprite/shape/age animation, positioned at the exact smoothly interpolated curve.
      boolean fading = flight.burst() || flight.instability().fails();
      float opacity =
          flight.burst()
              ? RitualFlight.burstAlpha(age + partial, lifetime)
              : flight.instability().fails()
                  ? 1
                      - (float)
                          Math.clamp(
                              (flight.instability().age()
                                      + age
                                      + partial
                                      - flight.instability().failAt())
                                  / 40,
                              0,
                              1)
                  : 1;
      visual.render(
          fading || fullBright ? new Fade(buffer, opacity, fullBright) : buffer, camera, 1);
    }

    @Override
    public ParticleRenderType getRenderType() {
      var type = visual.getRenderType();
      return (flight.burst() || flight.instability().fails())
              && (type == ParticleRenderType.PARTICLE_SHEET_OPAQUE
                  || type == ParticleRenderType.PARTICLE_SHEET_LIT)
          ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
          : type;
    }
  }

  /** Preserve native UVs and tint while lighting ritual visuals and applying their final fade. */
  private record Fade(VertexConsumer delegate, float opacity, boolean fullBright)
      implements VertexConsumer {
    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      delegate.addVertex(x, y, z);
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      delegate.setColor(r, g, b, Math.round(a * opacity));
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      delegate.setUv(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      delegate.setUv1(u, v);
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      delegate.setUv2(fullBright ? 240 : u, fullBright ? 240 : v);
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      delegate.setNormal(x, y, z);
      return this;
    }
  }
}
