package com.cappleapple.ritualsnotrolls.ritual;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.particles.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Native particle appearance plus an optional server-defined curved flight and finishing orbit. */
public record RitualParticleOptions(ResourceLocation effect, int rgb, Optional<Flight> flight)
    implements ParticleOptions {
  public record Flight(
      Vec3 destination,
      Vec3 bend,
      int travel,
      int remaining,
      boolean orbit,
      float phase,
      int sourceEntity,
      int ring,
      boolean burst,
      float radius,
      Optional<RitualSpline.Curve> route,
      RitualInstability instability) {
    public Flight(
        Vec3 destination,
        Vec3 bend,
        int travel,
        int remaining,
        boolean orbit,
        float phase,
        int sourceEntity,
        int ring,
        boolean burst,
        float radius) {
      this(
          destination,
          bend,
          travel,
          remaining,
          orbit,
          phase,
          sourceEntity,
          ring,
          burst,
          radius,
          Optional.empty(),
          RitualInstability.STABLE);
    }

    public Flight(
        Vec3 destination,
        Vec3 bend,
        int travel,
        int remaining,
        boolean orbit,
        float phase,
        int sourceEntity,
        int ring,
        boolean burst) {
      this(destination, bend, travel, remaining, orbit, phase, sourceEntity, ring, burst, 0);
    }

    public Flight(
        Vec3 destination,
        Vec3 bend,
        int travel,
        int remaining,
        boolean orbit,
        float phase,
        int sourceEntity) {
      this(destination, bend, travel, remaining, orbit, phase, sourceEntity, 0, false);
    }

    public static final Codec<Flight> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Vec3.CODEC.fieldOf("destination").forGetter(Flight::destination),
                        Vec3.CODEC.fieldOf("bend").forGetter(Flight::bend),
                        Codec.intRange(1, 12000).fieldOf("travel").forGetter(Flight::travel),
                        Codec.intRange(1, 12000).fieldOf("remaining").forGetter(Flight::remaining),
                        Codec.BOOL.fieldOf("orbit").forGetter(Flight::orbit),
                        Codec.FLOAT.fieldOf("phase").forGetter(Flight::phase),
                        Codec.INT.fieldOf("source_entity").forGetter(Flight::sourceEntity),
                        Codec.intRange(0, 8).optionalFieldOf("ring", 0).forGetter(Flight::ring),
                        Codec.BOOL.optionalFieldOf("burst", false).forGetter(Flight::burst),
                        Codec.floatRange(0, 3)
                            .optionalFieldOf("radius", 0f)
                            .forGetter(Flight::radius),
                        RitualSpline.Curve.CODEC.optionalFieldOf("route").forGetter(Flight::route),
                        RitualInstability.CODEC
                            .optionalFieldOf("instability", RitualInstability.STABLE)
                            .forGetter(Flight::instability))
                    .apply(i, Flight::new));
  }

  public RitualParticleOptions(ResourceLocation effect, int rgb) {
    this(effect, rgb, Optional.empty());
  }

  public RitualParticleOptions flying(Flight route) {
    return new RitualParticleOptions(effect, rgb, Optional.of(route));
  }

  public static final MapCodec<RitualParticleOptions> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      ResourceLocation.CODEC
                          .fieldOf("effect")
                          .forGetter(RitualParticleOptions::effect),
                      Codec.intRange(-1, 0xffffff)
                          .optionalFieldOf("rgb", -1)
                          .forGetter(RitualParticleOptions::rgb),
                      Flight.CODEC
                          .optionalFieldOf("flight")
                          .forGetter(RitualParticleOptions::flight))
                  .apply(i, RitualParticleOptions::new));

  private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) {
    b.writeDouble(v.x);
    b.writeDouble(v.y);
    b.writeDouble(v.z);
  }

  private static Vec3 readVec(RegistryFriendlyByteBuf b) {
    return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
  }

  public static final StreamCodec<RegistryFriendlyByteBuf, RitualParticleOptions> STREAM =
      StreamCodec.of(
          (b, p) -> {
            b.writeResourceLocation(p.effect);
            b.writeInt(p.rgb);
            b.writeBoolean(p.flight.isPresent());
            p.flight.ifPresent(
                f -> {
                  writeVec(b, f.destination);
                  writeVec(b, f.bend);
                  b.writeVarInt(f.travel);
                  b.writeVarInt(f.remaining);
                  b.writeBoolean(f.orbit);
                  b.writeFloat(f.phase);
                  b.writeInt(f.sourceEntity);
                  b.writeByte(f.ring);
                  b.writeBoolean(f.burst);
                  b.writeFloat(f.radius);
                  b.writeBoolean(f.route.isPresent());
                  f.route.ifPresent(
                      route -> {
                        b.writeVarInt(route.points().size());
                        for (int i = 0; i < route.points().size(); i++) {
                          writeVec(b, route.points().get(i));
                          b.writeVarInt(route.ticks().get(i));
                        }
                        b.writeBoolean(route.playerSource());
                      });
                  b.writeFloat(f.instability.weakness());
                  b.writeVarInt(f.instability.age());
                  b.writeVarInt(f.instability.shakeAt());
                  b.writeVarInt(f.instability.ramp());
                  b.writeVarInt(f.instability.failAt());
                });
          },
          b -> {
            var effect = b.readResourceLocation();
            int rgb = b.readInt();
            return new RitualParticleOptions(
                effect,
                rgb,
                b.readBoolean()
                    ? Optional.of(
                        new Flight(
                            readVec(b),
                            readVec(b),
                            b.readVarInt(),
                            b.readVarInt(),
                            b.readBoolean(),
                            b.readFloat(),
                            b.readInt(),
                            b.readUnsignedByte(),
                            b.readBoolean(),
                            b.readFloat(),
                            readRoute(b),
                            new RitualInstability(
                                b.readFloat(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt())))
                    : Optional.empty());
          });

  private static Optional<RitualSpline.Curve> readRoute(RegistryFriendlyByteBuf b) {
    if (!b.readBoolean()) return Optional.empty();
    int count = b.readVarInt();
    if (count < 2 || count > 2048) throw new IllegalArgumentException("Invalid route size");
    var points = new java.util.ArrayList<Vec3>();
    var times = new java.util.ArrayList<Integer>();
    for (int i = 0; i < count; i++) {
      points.add(readVec(b));
      times.add(b.readVarInt());
    }
    return Optional.of(new RitualSpline.Curve(points, times, b.readBoolean()));
  }

  @Override
  public ParticleType<?> getType() {
    return RitualsNotRolls.RITUAL_PARTICLE.get();
  }
}
