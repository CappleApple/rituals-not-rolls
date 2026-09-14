package com.cappleapple.ritualsnotrolls.data;

import static org.junit.jupiter.api.Assertions.*;

import com.cappleapple.ritualsnotrolls.ritual.*;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ParticleSpaceTest {
  private static final BlockPos TABLE = new BlockPos(28000000, 64, -28000000);
  private static final UUID SPACE = UUID.fromString("81f3bd2d-f5cb-44ec-b57d-bc445c6903f0");

  private RitualParticleOptions particle(String kind) {
    boolean burst = kind.equals("burst");
    var center = Vec3.atCenterOf(TABLE).add(0, 1.2, 0);
    var route =
        RitualSpline.through(List.of(center.add(4, 0, 0), center.add(2, 0, 2), center), true);
    var flight =
        new RitualParticleOptions.Flight(
            center,
            burst ? new Vec3(.1, .2, .3) : RitualFlight.bend(route.start(), center),
            route.duration(),
            200,
            !burst,
            .25f,
            burst ? -1 : 41,
            4,
            burst,
            1.2f,
            kind.equals("spline") ? Optional.of(route) : Optional.empty(),
            RitualInstability.STABLE);
    return new RitualParticleOptions(ResourceLocation.withDefaultNamespace("enchant"), 0x123456)
        .flying(flight);
  }

  @Test
  void codecsPreservePlotAnchorAndExactOriginForSimpleFlightsSplinesAndBursts() {
    for (var kind : List.of("simple", "spline", "burst"))
      for (boolean moving : List.of(false, true)) {
        var particle = particle(kind);
        var origin = Vec3.atCenterOf(TABLE).add(4.125, 1.2, -.375);
        if (moving) particle = particle.inSpace(TABLE, origin, SPACE);
        var codec = RitualParticleOptions.CODEC.codec();
        var json = codec.encodeStart(JsonOps.INSTANCE, particle).getOrThrow();
        assertEquals(particle, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(moving, json.getAsJsonObject().has("space_anchor"));
        assertEquals(moving, json.getAsJsonObject().has("space_origin"));
        assertEquals(moving, json.getAsJsonObject().has("space_id"));

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
          RitualParticleOptions.STREAM.encode(buffer, particle);
          var decoded = RitualParticleOptions.STREAM.decode(buffer);
          assertEquals(particle, decoded);
          assertEquals(moving ? Optional.of(origin) : Optional.empty(), decoded.spaceOrigin());
          assertEquals(moving ? Optional.of(SPACE) : Optional.empty(), decoded.spaceId());
          assertEquals(0, buffer.readableBytes());
        } finally {
          buffer.release();
        }
      }
  }

  @Test
  void stationaryParticleJsonKeepsItsExistingFormat() {
    var options =
        RitualParticleOptions.CODEC
            .codec()
            .parse(
                JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(
                    "{\"effect\":\"minecraft:enchant\",\"rgb\":1193046}"))
            .getOrThrow();
    assertTrue(options.spaceAnchor().isEmpty());
    assertTrue(options.spaceOrigin().isEmpty());
    assertTrue(options.spaceId().isEmpty());
    assertTrue(options.flight().isEmpty());
    assertEquals(0x123456, options.rgb());
  }

  @Test
  void anchorIsCapturedAndSurvivesAttachingFlightGeometry() {
    var mutable = TABLE.mutable();
    var localOrigin = Vec3.atCenterOf(TABLE).add(3, .7, -2);
    var base =
        new RitualParticleOptions(ResourceLocation.withDefaultNamespace("end_rod"), -1)
            .inSpace(mutable, localOrigin, SPACE);
    mutable.move(200, 0, 200);
    var withFlight = base.flying(particle("simple").flight().orElseThrow());
    assertEquals(Optional.of(TABLE), base.spaceAnchor());
    assertEquals(base.spaceAnchor(), withFlight.spaceAnchor());
    assertEquals(Optional.of(localOrigin), withFlight.spaceOrigin());
    assertEquals(Optional.of(SPACE), withFlight.spaceId());
    assertEquals(base.effect(), withFlight.effect());
    assertEquals(base.rgb(), withFlight.rgb());
  }
}
