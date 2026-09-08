package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import com.cappleapple.ritualsnotrolls.knowledge.KnowledgeData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DefinitionTest {
  private final ResourceLocation sharp = ResourceLocation.parse("minecraft:sharpness");

  @Test
  void knowledgeIdentityIsIndependentOfPower() {
    var a = new KnowledgeData(sharp, List.of("diamond"), true);
    var b =
        KnowledgeData.CODEC
            .parse(
                JsonOps.INSTANCE, KnowledgeData.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow())
            .getOrThrow();
    assertEquals(a, b);
  }

  @Test
  void knowledgeIsNotLevels() {
    var d = new KnowledgeData(sharp, List.of("diamond"), true);
    assertEquals(List.of("diamond"), d.entries());
  }

  @Test
  void duplicateDataIsCanonicalized() {
    assertEquals(1, new KnowledgeData(sharp, List.of("diamond", "diamond"), true).entries().size());
  }

  @Test
  void autoAddBelongsToBook() {
    var a = new KnowledgeData(sharp, List.of("diamond"), true);
    assertTrue(a.autoAdd());
    assertFalse(a.toggle().autoAdd());
    assertTrue(a.autoAdd());
  }

  @Test
  void removalPreservesOthers() {
    var a = new KnowledgeData(sharp, List.of("diamond", "flint"), true);
    assertEquals(List.of("flint"), a.remove("diamond").entries());
  }

  @Test
  void catalystBonusesAreAdditive() {
    assertEquals(1.1, RitualRules.DEFAULT.xpMultiplier(1), 1e-9);
    assertEquals(1.2, RitualRules.DEFAULT.xpMultiplier(2), 1e-9);
    assertEquals(1.3, RitualRules.DEFAULT.xpMultiplier(3), 1e-9);
    assertEquals(160, RitualRules.DEFAULT.xpCost(1));
    assertEquals(550, RitualRules.DEFAULT.xpCost(2));
    assertEquals(1395, RitualRules.DEFAULT.xpCost(3));
  }

  @Test
  void catalystRulesAreDatapackReplaceable() {
    var r = new RitualRules(2, 5, .2, Map.of(), 120);
    assertEquals(1.4, r.xpMultiplier(2), 1e-9);
    assertEquals(160, r.xpCost(2));
  }

  @Test
  void affinityRejectsAmbiguousSelectors() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Affinity("x", Optional.of(sharp), Optional.of(sharp), 2, 1));
  }

  @Test
  void affinityRejectsNonfinitePower() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Affinity("x", Optional.of(sharp), Optional.empty(), Double.NaN, 1));
  }

  @Test
  void extendedLevelsDecode() {
    var d = decode("{\"1\":15,\"6\":320,\"100\":10000}");
    assertEquals(100, d.thresholds().lastKey());
    assertEquals(320, d.required(6));
  }

  @Test
  void engineCodecLimitIsExplicit() {
    assertThrows(RuntimeException.class, () -> decode("{\"256\":10}"));
  }

  @Test
  void impossibleThresholdsRejected() {
    assertThrows(RuntimeException.class, () -> decode("{\"1\":15,\"2\":10}"));
  }

  @Test
  void missingLevelIsNotClamped() {
    assertEquals(Double.POSITIVE_INFINITY, decode("{\"1\":15,\"6\":320}").required(5));
  }

  @Test
  void reloadKeepsLogicalKnowledge() {
    var knowledge = new KnowledgeData(sharp, List.of("diamond"), true);
    var old = decode("{\"1\":15}");
    var replacement = decode("{\"1\":30,\"6\":320}");
    assertNotNull(old.affinity(knowledge.entries().getFirst()));
    assertNotNull(replacement.affinity(knowledge.entries().getFirst()));
  }

  @Test
  void particleHexColorIsOptionalAndRoundTrips() {
    var base = decode("{\"1\":15}");
    assertEquals(-1, base.particleRgb());
    var json =
        RitualDefinition.CODEC.encodeStart(JsonOps.INSTANCE, base).getOrThrow().getAsJsonObject();
    json.addProperty("particle", "minecraft:flame");
    json.addProperty("particle_color", "aBc123");
    var colored = RitualDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    assertEquals(0xABC123, colored.particleRgb());
    assertEquals(Optional.of("#ABC123"), colored.particleColor());
    assertEquals(
        colored,
        RitualDefinition.CODEC
            .parse(
                JsonOps.INSTANCE,
                RitualDefinition.CODEC.encodeStart(JsonOps.INSTANCE, colored).getOrThrow())
            .getOrThrow());
  }

  @Test
  void invalidParticleColorsAreRejected() {
    var base = decode("{\"1\":15}");
    for (var color : List.of("red", "#FFFF", "#GG0000", "#12345678", "1234567")) {
      var json =
          RitualDefinition.CODEC.encodeStart(JsonOps.INSTANCE, base).getOrThrow().getAsJsonObject();
      json.addProperty("particle_color", color);
      assertThrows(
          RuntimeException.class,
          () -> RitualDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
  }

  @Test
  void magicalRoutesBendAndArriveAtTheirDestination() {
    var from = new net.minecraft.world.phys.Vec3(0, 0, 0);
    var to = new net.minecraft.world.phys.Vec3(8, 0, 0);
    var flight =
        new com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions.Flight(
            to, new net.minecraft.world.phys.Vec3(0, 2, 1), 30, 120, false, 0, -1);
    assertEquals(
        from, com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(from, flight, 0));
    assertTrue(
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(from, flight, 15)
                .distanceTo(from.lerp(to, .5))
            > 2);
    assertTrue(
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(from, flight, 30)
                .distanceTo(to)
            < 1e-9);
  }

  @Test
  void arrivalsOrbitAndPulseBeforeTheRitualEnd() {
    var center = new net.minecraft.world.phys.Vec3(4, 1, 4);
    var flight =
        new com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions.Flight(
            center, new net.minecraft.world.phys.Vec3(0, 2, 0), 20, 120, true, 1, -1);
    var a =
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(
            net.minecraft.world.phys.Vec3.ZERO, flight, 45);
    var b =
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(
            net.minecraft.world.phys.Vec3.ZERO, flight, 55);
    assertTrue(a.distanceTo(b) > .5);
    assertTrue(
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.pulseScale(10)
            < com.cappleapple.ritualsnotrolls.ritual.RitualFlight.pulseScale(20));
    double finalRadius =
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(
                net.minecraft.world.phys.Vec3.ZERO, flight, 120)
            .distanceTo(center);
    assertTrue(finalRadius > .02 && finalRadius < .3);
    assertTrue(
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.pulseScale(8)
            < com.cappleapple.ritualsnotrolls.ritual.RitualFlight.pulseScale(0));
  }

  @Test
  void ringsUseSeveralRadiiAndNearVerticalPlanes() {
    Set<Double> radii = new HashSet<>(), heights = new HashSet<>();
    int vertical = 0;
    for (int i = 0; i < 9; i++) {
      var r = com.cappleapple.ritualsnotrolls.ritual.RitualFlight.ring(i);
      radii.add(r.radius());
      heights.add(r.height());
      assertEquals(1, r.u().length(), 1e-9);
      assertEquals(1, r.v().length(), 1e-9);
      assertEquals(0, r.u().dot(r.v()), 1e-9);
      if (Math.abs(r.v().y) > .95) vertical++;
    }
    assertEquals(3, radii.size());
    assertEquals(3, heights.size());
    assertEquals(3, vertical);
  }

  @Test
  void burstExpandsIntoEveryOctantAndFadesToTransparent() {
    Set<Integer> octants = new HashSet<>();
    var zero = net.minecraft.world.phys.Vec3.ZERO;
    for (int i = 0; i < 96; i++) {
      var d = com.cappleapple.ritualsnotrolls.ritual.RitualFlight.burstDirection(i, 96);
      assertEquals(1, d.length(), 1e-9);
      octants.add((d.x > 0 ? 1 : 0) | (d.y > 0 ? 2 : 0) | (d.z > 0 ? 4 : 0));
      var f =
          new com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions.Flight(
              zero, d, 1, 40, false, 2, -1, 0, true);
      assertTrue(
          com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(zero, f, 20).length()
              > com.cappleapple.ritualsnotrolls.ritual.RitualFlight.position(zero, f, 5).length());
    }
    assertEquals(8, octants.size());
    assertEquals(1, com.cappleapple.ritualsnotrolls.ritual.RitualFlight.burstAlpha(0, 40));
    assertTrue(
        com.cappleapple.ritualsnotrolls.ritual.RitualFlight.burstAlpha(20, 40)
            < com.cappleapple.ritualsnotrolls.ritual.RitualFlight.burstAlpha(5, 40));
    assertEquals(0, com.cappleapple.ritualsnotrolls.ritual.RitualFlight.burstAlpha(40, 40));
  }

  @Test
  void flightCodecPreservesLayerAndBurstParameters() {
    var f =
        new com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions.Flight(
            new net.minecraft.world.phys.Vec3(1, 2, 3),
            new net.minecraft.world.phys.Vec3(0, 1, 0),
            1,
            40,
            false,
            2,
            -1,
            8,
            true);
    var codec = com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions.Flight.CODEC;
    assertEquals(
        f,
        codec
            .parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, f).getOrThrow())
            .getOrThrow());
  }

  private RitualDefinition decode(String levels) {
    return RitualDefinition.CODEC
        .parse(
            JsonOps.INSTANCE,
            JsonParser.parseString(
                "{\"enchantment\":\"minecraft:sharpness\",\"materials\":[{\"id\":\"diamond\",\"item\":\"minecraft:diamond\",\"power\":30}],\"levels\":"
                    + levels
                    + "}"))
        .getOrThrow();
  }
}
