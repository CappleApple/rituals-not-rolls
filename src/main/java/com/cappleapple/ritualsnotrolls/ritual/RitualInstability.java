package com.cappleapple.ritualsnotrolls.ritual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Chain-owned instability clocks are shared by all particles, including those already in flight.
 */
public record RitualInstability(float weakness, int age, int shakeAt, int ramp, int failAt) {
  public static final RitualInstability STABLE = new RitualInstability(0, 0, 0, 1, -1);
  public static final Codec<RitualInstability> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      Codec.floatRange(0, 1)
                          .fieldOf("weakness")
                          .forGetter(RitualInstability::weakness),
                      Codec.INT.fieldOf("age").forGetter(RitualInstability::age),
                      Codec.INT.fieldOf("shake_at").forGetter(RitualInstability::shakeAt),
                      Codec.intRange(1, 12000).fieldOf("ramp").forGetter(RitualInstability::ramp),
                      Codec.INT.fieldOf("fail_at").forGetter(RitualInstability::failAt))
                  .apply(i, RitualInstability::new));

  public boolean fails() {
    return failAt >= 0;
  }

  public double amplitude(double tick) {
    double t = Math.clamp((age + tick - shakeAt) / ramp, 0, 1);
    return t * t * (3 - 2 * t) * (fails() ? .4 : .12 * weakness);
  }

  public static int failureRamp(double fraction) {
    return 24 + (int) Math.round(72 * Math.pow(Math.clamp(fraction, 0, 1), 2));
  }
}
