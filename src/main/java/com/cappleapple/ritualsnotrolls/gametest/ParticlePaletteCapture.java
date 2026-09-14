package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.client.RitualParticleProvider;
import com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Native particle/provider QA and labeled screenshots, excluded from the production artifact. */
@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class ParticlePaletteCapture {
  private record Preset(String enchantment, ResourceLocation effect, int color) {}

  private static final int PAGE_SIZE = 12;
  private static final RitualParticleProvider PROVIDER = new RitualParticleProvider();
  private static List<Preset> presets = List.of();
  private static int ticks, stage, wait, batch, passed, originalFov, originalScale;
  private static boolean originalHidden, settingsSaved;
  private static ParticleStatus originalParticles;
  private static double originalGamma;

  private static boolean enabled() {
    return Boolean.getBoolean("ritualsnotrolls.particlePalette");
  }

  private static void next(int value) {
    stage = value;
    wait = 0;
  }

  private static void loadPresets(Minecraft mc) {
    var found = new TreeMap<String, Preset>();
    // Read raw server-data resources from the client's loaded packs. Optional enchantment
    // definitions must be exercised even when their owning mod is absent from this QA client.
    mc.getResourceManager()
        .listPacks()
        .forEach(
            pack -> {
              for (String namespace : pack.getNamespaces(PackType.SERVER_DATA)) {
                pack.listResources(
                    PackType.SERVER_DATA,
                    namespace,
                    "ritual_enchanting/enchantments",
                    (id, input) -> {
                      if (!id.getPath().endsWith(".json")) return;
                      try (var reader =
                          new InputStreamReader(input.get(), StandardCharsets.UTF_8)) {
                        var json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("enabled") && !json.get("enabled").getAsBoolean()) return;
                        String enchantment = json.get("enchantment").getAsString();
                        var effect = ResourceLocation.parse(json.get("particle").getAsString());
                        int color =
                            Integer.parseInt(
                                json.get("particle_color").getAsString().substring(1), 16);
                        found.put(enchantment, new Preset(enchantment, effect, color));
                      } catch (Exception failure) {
                        throw new IllegalStateException(
                            "Cannot load palette definition " + id, failure);
                      }
                    });
              }
            });
    presets = List.copyOf(found.values());
    require(presets.size() == 68, "Expected all 68 active bundled presets, got " + presets.size());
    RitualsNotRolls.LOGGER.info(
        "PARTICLE_PALETTE_LOADED: {} presets / {} distinct effects",
        presets.size(),
        presets.stream().map(Preset::effect).distinct().count());
  }

  private static Vec3 center(int cell) {
    return new Vec3(34 + (cell % 4) * 4, 92.7 - (cell / 4) * 2.7, 0);
  }

  private static RitualParticleOptions options(Preset preset, Vec3 center, float phase) {
    return new RitualParticleOptions(preset.effect(), preset.color())
        .flying(
            new RitualParticleOptions.Flight(
                center, new Vec3(.15, .45, 0), 24, 120, true, phase, -1, 6, false, .65f));
  }

  private static Particle make(Preset preset, Vec3 center, float phase) {
    var start = center.add(-1, -.55, 0);
    return PROVIDER.createParticle(
        options(preset, center, phase),
        Minecraft.getInstance().level,
        start.x,
        start.y,
        start.z,
        0,
        0,
        0);
  }

  private static void verify(Preset preset, int cell) throws Exception {
    var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(preset.effect()).orElse(null);
    require(
        type instanceof SimpleParticleType,
        preset.enchantment() + " is not a simple native effect: " + preset.effect());
    require(
        !preset.effect().equals(ResourceLocation.withDefaultNamespace("enchant")),
        "Rune preset " + preset.enchantment());
    var center = center(cell);
    var start = center.add(-1, -.55, 0);
    var longFlight =
        new RitualParticleOptions(preset.effect(), preset.color())
            .flying(
                new RitualParticleOptions.Flight(
                    center, new Vec3(.15, .45, 0), 24, 600, true, 0, -1, 6, false, .65f));
    var particle =
        PROVIDER.createParticle(
            longFlight, Minecraft.getInstance().level, start.x, start.y, start.z, 0, 0, 0);
    require(particle != null, "Provider returned null for " + preset);
    var field = particle.getClass().getDeclaredField("visual");
    field.setAccessible(true);
    var visual = (Particle) field.get(particle);
    var nativeParticle =
        ((com.cappleapple.ritualsnotrolls.mixin.ParticleEngineAccessor)
                Minecraft.getInstance().particleEngine)
            .ritualsnotrolls$makeParticle(
                (SimpleParticleType) type, start.x, start.y, start.z, 0, 0, 0);
    require(
        nativeParticle != null && visual.getClass().equals(nativeParticle.getClass()),
        "Native provider mismatch for " + preset);
    nativeParticle.remove();
    require(particle.getRenderType() != ParticleRenderType.NO_RENDER, "NO_RENDER for " + preset);
    verifyAges(preset, particle, visual, new int[] {0, 1, 8, 24, 48, 80, 110, 240, 400, 550});
    var burst =
        new RitualParticleOptions(preset.effect(), preset.color())
            .flying(
                new RitualParticleOptions.Flight(
                    center, new Vec3(1, .3, 0), 1, 32, false, 0, -1, 6, true, .65f));
    var burstParticle =
        PROVIDER.createParticle(
            burst, Minecraft.getInstance().level, center.x, center.y, center.z, 0, 0, 0);
    require(burstParticle != null, "Missing burst " + preset);
    verifyAges(
        preset,
        burstParticle,
        (Particle) field.get(burstParticle),
        new int[] {0, 1, 8, 16, 24, 28});
    passed++;
    RitualsNotRolls.LOGGER.info(
        "PARTICLE_PALETTE_PRESET_PASS: {} | {} | #{} | {} |"
            + " orbit_ages=0,1,8,24,48,80,110,240,400,550 burst_ages=0,1,8,16,24,28",
        preset.enchantment(),
        preset.effect(),
        String.format("%06X", preset.color()),
        visual.getClass().getSimpleName());
  }

  private static void verifyAges(Preset preset, Particle particle, Particle visual, int[] ages) {
    var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
    int previous = 0, visibleSamples = 0;
    for (int age : ages) {
      for (; previous < age; previous++) particle.tick();
      var probe = new QuadProbe();
      particle.render(probe, camera, .5f);
      require(
          probe.vertices >= 4 && probe.vertices % 4 == 0,
          "Empty quad for " + preset + " at age " + age);
      // VaultConnectionProvider uses LifetimeAlpha(0, .6, .25, 1): its native
      // sprite deliberately stays transparent for the first quarter of its life.
      boolean nativeFadeIn =
          preset.effect().equals(ResourceLocation.withDefaultNamespace("vault_connection"))
              && age + 1 <= visual.getLifetime() * .25;
      require(
          probe.alpha > 0 || nativeFadeIn,
          "Invisible quad outside native fade-in for " + preset + " at age " + age);
      if (probe.alpha > 0) visibleSamples++;
      require(
          probe.maxX > probe.minX && probe.maxY > probe.minY,
          "Degenerate quad for " + preset + " at age " + age);
      var pos = particle.getPos();
      require(
          Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z),
          "Nonfinite position " + preset);
    }
    require(visibleSamples > 0, "No visible interior samples for " + preset);
    particle.remove();
  }

  private static void verifyAll() {
    var failures = new ArrayList<String>();
    for (int index = 0; index < presets.size(); index++) {
      var preset = presets.get(index);
      try {
        verify(preset, index % PAGE_SIZE);
      } catch (Exception failure) {
        failures.add(preset.enchantment() + ": " + failure.getMessage());
        RitualsNotRolls.LOGGER.error(
            "PARTICLE_PALETTE_PRESET_FAIL: {}", preset.enchantment(), failure);
      }
    }
    require(failures.isEmpty(), "Palette render probes failed: " + failures);
    RitualsNotRolls.LOGGER.info("PARTICLE_PALETTE_ALL_PROBES_PASS: {} presets", passed);
  }

  private static void emitBatch(Minecraft mc) {
    for (int cell = 0; cell < PAGE_SIZE && batch * PAGE_SIZE + cell < presets.size(); cell++) {
      var preset = presets.get(batch * PAGE_SIZE + cell);
      for (int j = 0; j < 2; j++) {
        var particle = make(preset, center(cell), (float) (wait * .22 + j * Math.PI));
        if (particle != null) mc.particleEngine.add(particle);
      }
    }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!enabled() || stage == 99) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    try {
      if (ticks > 1800) throw new IllegalStateException("Palette timed out at stage " + stage);
      if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 30) {
        originalFov = mc.options.fov().get();
        originalScale = mc.options.guiScale().get();
        originalHidden = mc.options.hideGui;
        originalParticles = mc.options.particles().get();
        originalGamma = mc.options.gamma().get();
        settingsSaved = true;
        mc.options.fov().set(50);
        mc.options.guiScale().set(2);
        mc.options.hideGui = true;
        mc.options.particles().set(ParticleStatus.ALL);
        mc.options.gamma().set(1d);
        mc.resizeDisplay();
        ConnectScreen.startConnecting(
            mc.screen,
            mc,
            ServerAddress.parseString("127.0.0.1:25585"),
            new ServerData("Native particle palette", "127.0.0.1:25585", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 25) {
        loadPresets(mc);
        mc.player.connection.sendCommand("ritualpalette");
        next(2);
      } else if (stage == 2 && wait > 50) {
        require(
            Math.abs(mc.player.getX() - 40) < .1 && Math.abs(mc.player.getZ() + 10) < .1,
            "Server did not establish palette camera");
        verifyAll();
        next(3);
      } else if (stage == 3) {
        emitBatch(mc);
        if (wait == 88) {
          mc.getToasts().clear();
          Screenshot.grab(
              mc.gameDirectory,
              "particle-palette-" + (batch + 1) + ".png",
              mc.getMainRenderTarget(),
              c -> {});
          RitualsNotRolls.LOGGER.info(
              "PARTICLE_PALETTE_CAPTURED: {} presets {}-{}",
              batch + 1,
              batch * PAGE_SIZE + 1,
              Math.min((batch + 1) * PAGE_SIZE, presets.size()));
          next(4);
        }
      } else if (stage == 4 && wait > 125) {
        if ((++batch) * PAGE_SIZE < presets.size()) next(3);
        else {
          require(passed == presets.size(), "Missing preset checks");
          RitualsNotRolls.LOGGER.info(
              "PARTICLE_PALETTE_PASS: all {} presets; {} screenshots; native sprites and finite"
                  + " visible quads through orbit age 550 and burst age 28",
              passed,
              batch);
          finish(mc);
        }
      }
    } catch (Throwable failure) {
      RitualsNotRolls.LOGGER.error("PARTICLE_PALETTE_FAIL", failure);
      finish(mc);
    }
  }

  @SubscribeEvent
  public static void overlay(RenderGuiEvent.Post event) {
    if (!enabled() || (stage != 3 && stage != 4)) return;
    var mc = Minecraft.getInstance();
    var g = event.getGuiGraphics();
    int width = g.guiWidth(), height = g.guiHeight();
    double scale = height / (20 * Math.tan(Math.toRadians(25)));
    g.fill(0, 0, width, 27, 0xE018202A);
    g.drawCenteredString(
        mc.font,
        "Ritual particle palette "
            + (batch + 1)
            + "/"
            + ((presets.size() + PAGE_SIZE - 1) / PAGE_SIZE),
        width / 2,
        5,
        0xFFFFFF);
    g.drawCenteredString(
        mc.font,
        "Native flying particles | same ritual motion, individual shape and tint",
        width / 2,
        16,
        0xC4CCD8);
    for (int cell = 0; cell < PAGE_SIZE && batch * PAGE_SIZE + cell < presets.size(); cell++) {
      var preset = presets.get(batch * PAGE_SIZE + cell);
      var at = center(cell);
      int x = (int) Math.round(width / 2d - (at.x - 40) * scale);
      int y = (int) Math.round(height / 2d - (at.y - 90) * scale);
      int half = (int) (scale * 1.85);
      g.fill(x - half, y - 60, x + half, y - 31, 0xD018202A);
      String name = preset.enchantment();
      if (mc.font.width(name) > half * 2 - 4) {
        g.drawCenteredString(
            mc.font, name.substring(0, name.indexOf(':') + 1), x, y - 58, 0xFFFFFF);
        g.drawCenteredString(mc.font, name.substring(name.indexOf(':') + 1), x, y - 49, 0xFFFFFF);
      } else g.drawCenteredString(mc.font, name, x, y - 53, 0xFFFFFF);
      g.drawCenteredString(
          mc.font,
          preset.effect().getPath() + " #" + String.format("%06X", preset.color()),
          x,
          y - 39,
          0xFF000000 | preset.color());
    }
  }

  private static void finish(Minecraft mc) {
    if (settingsSaved) {
      mc.options.fov().set(originalFov);
      mc.options.guiScale().set(originalScale);
      mc.options.hideGui = originalHidden;
      mc.options.particles().set(originalParticles);
      mc.options.gamma().set(originalGamma);
      mc.resizeDisplay();
    }
    next(99);
    mc.stop();
  }

  private static void require(boolean value, String reason) {
    if (!value) throw new IllegalStateException(reason);
  }

  private static final class QuadProbe implements VertexConsumer {
    int vertices, alpha;
    float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
    float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      require(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "Nonfinite vertex");
      vertices++;
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minY = Math.min(minY, y);
      maxY = Math.max(maxY, y);
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      alpha = Math.max(alpha, a);
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      require(Float.isFinite(u) && Float.isFinite(v), "Nonfinite UV");
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      return this;
    }
  }
}
