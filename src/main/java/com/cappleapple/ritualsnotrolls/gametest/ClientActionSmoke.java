package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import com.cappleapple.ritualsnotrolls.client.*;
import com.cappleapple.ritualsnotrolls.knowledge.Knowledge;
import com.cappleapple.ritualsnotrolls.menu.RitualMenu;
import com.cappleapple.ritualsnotrolls.ritual.RitualMath;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Real client/server menu actions, resource reload and a physically thrown multi-enchantment
 * ritual. The opt-in fixture/test driver is excluded from the production JAR.
 */
@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class ClientActionSmoke {
  private static int ticks, stage, wait;
  private static boolean reloaded;
  private static double minimumY = Double.POSITIVE_INFINITY, maximumY = Double.NEGATIVE_INFINITY;
  private static int floatingSamples;
  private static ItemEntity capturedTarget;
  private static net.minecraft.client.particle.Particle orbitParticle;
  private static net.minecraft.world.phys.Vec3 orbitCenter, orbitStart;
  private static final java.util.Set<net.minecraft.world.phys.Vec3> xpDestinations =
      new java.util.HashSet<>();
  private static boolean curved, pulseChecked, layeredCaptured, inverseSeen;
  private static boolean disenchantReleased, tailAfterRelease, removalBurst;
  private static final java.util.Map<Integer, Integer>
      firstEffects = new java.util.LinkedHashMap<>(),
      firstArrivals = new java.util.LinkedHashMap<>();
  private static final java.util.Map<Integer, Float> powerRadii = new java.util.HashMap<>();
  private static net.minecraft.client.particle.Particle burstParticle;
  private static int burstStarted, earlyAlpha = -1, activeBursts;
  private static boolean fadeChecked;
  private static boolean trailsDraining, trailsDrained, retainedOldRing;
  private static int lastIncomingBirth = -1, lastXpBirth = -1, lastMaterialBirth = -1;
  private static boolean xpTrailDrained, xpRadiusChecked;
  private static int clearanceSamples;
  private static final java.util.Set<Integer> observedRings = new java.util.HashSet<>(),
      burstOctants = new java.util.HashSet<>();

  static final class AlphaProbe implements com.mojang.blaze3d.vertex.VertexConsumer {
    int maximum, vertices;
    double minX = Double.POSITIVE_INFINITY,
        maxX = Double.NEGATIVE_INFINITY,
        minZ = Double.POSITIVE_INFINITY,
        maxZ = Double.NEGATIVE_INFINITY;

    @Override
    public AlphaProbe addVertex(float x, float y, float z) {
      vertices++;
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minZ = Math.min(minZ, z);
      maxZ = Math.max(maxZ, z);
      return this;
    }

    @Override
    public AlphaProbe setColor(int r, int g, int b, int a) {
      maximum = Math.max(maximum, a);
      return this;
    }

    @Override
    public AlphaProbe setUv(float u, float v) {
      return this;
    }

    @Override
    public AlphaProbe setUv1(int u, int v) {
      return this;
    }

    @Override
    public AlphaProbe setUv2(int u, int v) {
      return this;
    }

    @Override
    public AlphaProbe setNormal(float x, float y, float z) {
      return this;
    }
  }

  // Inspect actual client particles in this opt-in development fixture, excluded from the JAR.
  private static void inspectFlights() {
    try {
      var mc = Minecraft.getInstance();
      if (stage == 24 && !disenchantReleased) {
        disenchantReleased =
            mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(-3, 63, -3, 3, 68, 3)).stream()
                .anyMatch(
                    e ->
                        e.getItem().is(Items.DIAMOND_SWORD)
                            && !e.isNoGravity()
                            && RitualMath.enchantments(e.getItem()).entrySet().stream()
                                .anyMatch(
                                    entry ->
                                        entry
                                                .getKey()
                                                .unwrapKey()
                                                .orElseThrow()
                                                .location()
                                                .getPath()
                                                .equals("sharpness")
                                            && entry.getIntValue() == 3));
      }
      var field = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
      field.setAccessible(true);
      var groups = (java.util.Map<?, ?>) field.get(mc.particleEngine);
      activeBursts = 0;
      int incoming = 0, xpIncoming = 0, xpCircling = 0, remaining = Integer.MAX_VALUE;
      var circling = new java.util.HashSet<Integer>();
      for (var group : groups.values())
        for (var value : (java.util.Collection<?>) group) {
          var p = (net.minecraft.client.particle.Particle) value;
          if (!p.getClass().getName().endsWith("RitualParticleProvider$Flying")) continue;
          var info = p.getClass().getDeclaredField("flight");
          info.setAccessible(true);
          var f = (com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions.Flight) info.get(p);
          var optionsField = p.getClass().getDeclaredField("options");
          optionsField.setAccessible(true);
          var options =
              (com.cappleapple.ritualsnotrolls.ritual.RitualParticleOptions) optionsField.get(p);
          var ageField = net.minecraft.client.particle.Particle.class.getDeclaredField("age");
          ageField.setAccessible(true);
          int age = ageField.getInt(p);
          if (stage <= 13 && p.isAlive() && f.orbit() && !f.burst() && !f.instability().fails()) {
            lastIncomingBirth = Math.max(lastIncomingBirth, ticks - age);
            remaining = Math.min(remaining, f.remaining() - age);
            if (options.rgb() == 0x98FF50) {
              lastXpBirth = Math.max(lastXpBirth, ticks - age);
              if (!xpRadiusChecked) {
                check(
                    Math.abs(
                                f.radius()
                                    - com.cappleapple.ritualsnotrolls.ritual.RitualAnimation
                                        .experienceRadius(550))
                            < 1e-6
                        && f.radius() < .6,
                    "two catalysts use the reduced XP growth factor");
                xpRadiusChecked = true;
              }
              if (age < f.travel()) xpIncoming++;
              else xpCircling++;
            } else lastMaterialBirth = Math.max(lastMaterialBirth, ticks - age);
            if (age < f.travel()) incoming++;
            else {
              circling.add(options.rgb());
              if (p.getPos().y < 65.14)
                throw new IllegalStateException("Ring intersects table: " + p.getPos());
              clearanceSamples++;
              if (age > f.travel() + 120) retainedOldRing = true;
            }
          }
          if (stage == 24) {
            if (f.burst()) removalBurst = true;
            if (disenchantReleased
                && !f.burst()
                && !f.orbit()
                && age < f.travel()
                && !tailAfterRelease) {
              tailAfterRelease = true;
              capture("disenchantment-draining");
            }
          }
          if (!f.burst() && options.rgb() != 0x98FF50) {
            firstEffects.putIfAbsent(options.rgb(), ticks);
            if (f.orbit()) powerRadii.put(options.rgb(), f.radius());
            if (f.orbit() && age >= f.travel()) {
              firstArrivals.putIfAbsent(options.rgb(), ticks);
              if (!pulseChecked && f.remaining() - age <= 10 && firstArrivals.size() == 4) {
                double radius = p.getPos().distanceTo(f.destination());
                check(
                    radius < f.radius() * .45 + .16,
                    "actual power-scaled rings pulse inward (radius=" + radius + ")");
                pulseChecked = true;
                capture("ritual-pulse");
              }
            }
          }
          if (f.orbit()) observedRings.add(f.ring());
          if (f.burst()) {
            activeBursts++;
            if (burstParticle == null) {
              burstParticle = p;
              burstStarted = ticks;
            }
            var delta = p.getPos().subtract(f.destination());
            if (delta.length() > .5)
              burstOctants.add(
                  (delta.x > 0 ? 1 : 0) | (delta.y > 0 ? 2 : 0) | (delta.z > 0 ? 4 : 0));
            continue;
          }
          if (f.sourceEntity() == mc.player.getId())
            xpDestinations.add(f.route().map(c -> c.points().get(2)).orElse(f.destination()));
          var origin = p.getClass().getDeclaredField("start");
          origin.setAccessible(true);
          var from = (net.minecraft.world.phys.Vec3) origin.get(p);
          if (stage == 24
              && !f.orbit()
              && from.distanceTo(new net.minecraft.world.phys.Vec3(.5, 65.7, .5)) < .01
              && f.route().map(c -> c.points().get(2).x < -2).orElse(f.destination().x < -2))
            inverseSeen = true;
          var delta = f.destination().subtract(from);
          double t =
              Math.clamp(
                  p.getPos().subtract(from).dot(delta) / Math.max(.001, delta.lengthSqr()), 0, 1);
          if (p.getPos().distanceTo(from.lerp(f.destination(), t)) > .45) curved = true;
          if (orbitParticle == null
              && stage == 12
              && wait > 25
              && f.orbit()
              && p.getPos().distanceTo(f.destination()) < f.radius() + .2) {
            orbitParticle = p;
            orbitCenter = f.destination();
            orbitStart = p.getPos();
          }
        }
      if (stage == 13
          && !xpTrailDrained
          && xpIncoming == 0
          && xpCircling > 0
          && lastMaterialBirth > lastXpBirth + 10
          && ticks - lastXpBirth > 8) {
        xpTrailDrained = true;
        check(
            true,
            "XP source finishes early while its ring persists and enchantment chains continue");
        capture("ritual-xp-ring-retained");
      }
      if (stage == 13 && firstArrivals.size() == 4 && ticks - lastIncomingBirth > 8) {
        if (!trailsDraining && incoming > 0) {
          trailsDraining = true;
          capture("ritual-trails-draining");
          check(
              true,
              "all incoming sources have stopped while existing particles continue their routes");
        }
        if (!trailsDrained && incoming == 0 && circling.size() == 5 && remaining > 28) {
          trailsDrained = true;
          check(
              capturedTarget.isNoGravity(),
              "target remains captured while every trail has joined the five rings");
          check(
              retainedOldRing,
              "earliest ring particles survive beyond the former 120-tick orbit cap");
          capture("ritual-trails-drained");
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Particle inspection failed", e);
    }
  }

  private static void verifyPagePivot() {
    var mc = Minecraft.getInstance();
    var page =
        Knowledge.page(
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("sharpness"), "diamond");
    for (int angle : new int[] {0, 45, 90, 180, 270}) {
      var probe = new AlphaProbe();
      var pose = new com.mojang.blaze3d.vertex.PoseStack();
      pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
      mc.getItemRenderer()
          .renderStatic(
              page,
              net.minecraft.world.item.ItemDisplayContext.GROUND,
              15728880,
              net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
              pose,
              type -> probe,
              mc.level,
              0);
      check(
          probe.vertices > 24
              && Math.abs(probe.minX + probe.maxX) < .004
              && Math.abs(probe.minZ + probe.maxZ) < .004,
          "actual page vertices stay centered while spinning at " + angle + " degrees");
      if (angle == 0) check(probe.maxZ - probe.minZ >= .031, "page renders a real 3D edge");
    }
  }

  private static void next(int value) {
    stage = value;
    wait = 0;
  }

  private static void check(boolean ok, String message) {
    if (!ok) throw new IllegalStateException("Client QA failed: " + message);
    RitualsNotRolls.LOGGER.info("Client QA passed: {}", message);
  }

  private static void capture(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        name + ".png",
        mc.getMainRenderTarget(),
        c -> RitualsNotRolls.LOGGER.info("Client QA: {}", c.getString()));
  }

  private static void click(int x, int y, int button) {
    var mc = Minecraft.getInstance();
    int iw = mc.screen instanceof BookScreen ? 192 : 380,
        ih = mc.screen instanceof BookScreen ? 240 : 252;
    double fit =
        Math.min(
            1,
            Math.min(
                mc.getWindow().getGuiScaledWidth() / (double) (iw + 12),
                mc.getWindow().getGuiScaledHeight() / (double) (ih + 12)));
    int width = (int) Math.ceil(mc.getWindow().getGuiScaledWidth() / fit),
        height = (int) Math.ceil(mc.getWindow().getGuiScaledHeight() / fit);
    double px = ((width - iw) / 2 + x) * fit, py = ((height - ih) / 2 + y) * fit;
    mc.setLastInputType(net.minecraft.client.InputType.MOUSE);
    mc.screen.mouseClicked(px, py, button);
    mc.screen.mouseReleased(px, py, button);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.clientActionSmoke")) return;
    var mc = Minecraft.getInstance();
    ticks++;
    wait++;
    if (mc.screen instanceof FittedScreen<?> && wait == 1)
      org.lwjgl.glfw.GLFW.glfwSetCursorPos(
          mc.getWindow().getWindow(),
          mc.getWindow().getWidth() - 6,
          mc.getWindow().getHeight() - 6);
    if ((stage == 11 || stage == 12 || stage == 13 || stage == 24) && mc.level != null)
      inspectFlights();
    if (stage == 13 && burstParticle != null) {
      int elapsed = ticks - burstStarted;
      if (elapsed >= 5 && earlyAlpha < 0) {
        var probe = new AlphaProbe();
        burstParticle.render(probe, mc.gameRenderer.getMainCamera(), 0);
        earlyAlpha = probe.maximum;
        check(earlyAlpha > 100, "completion burst renders visible vertices");
        capture("ritual-burst");
      }
      if (elapsed >= 20 && !fadeChecked) {
        var probe = new AlphaProbe();
        burstParticle.render(probe, mc.gameRenderer.getMainCamera(), 0);
        check(
            probe.maximum > 0 && probe.maximum < earlyAlpha,
            "burst vertex opacity fades (" + earlyAlpha + " -> " + probe.maximum + ")");
        check(
            burstOctants.size() == 8,
            "actual burst particles travel into all eight spatial octants");
        capture("ritual-burst-fading");
        fadeChecked = true;
      }
    }
    if (stage == 13 && !layeredCaptured && firstArrivals.size() == 4) {
      layeredCaptured = true;
      check(
          new java.util.HashSet<>(powerRadii.values()).size() >= 3,
          "different imbuement powers produce different synchronized ring radii: " + powerRadii);
      var order = new java.util.ArrayList<>(firstEffects.keySet());
      for (int i = 1; i < order.size(); i++)
        check(
            firstEffects.get(order.get(i)) >= firstArrivals.get(order.get(i - 1)),
            "next enchantment starts after the prior ring arrives");
      check(
          firstEffects.get(order.getLast()) - firstEffects.get(order.getFirst()) > 80,
          "multi-enchantment animation starts are staggered");
      capture("automatic-ritual-layered");
    }
    if (stage == 13 && wait > 2000) throw new IllegalStateException("Client ritual timed out");
    if (stage == 12 && mc.level != null) {
      var floating =
          mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(-3, 64, -3, 3, 68, 3)).stream()
              .filter(e -> e.getItem().is(Items.NETHERITE_SWORD))
              .findFirst();
      if (floating.isPresent()) {
        var item = floating.get();
        capturedTarget = item;
        if (!item.isNoGravity())
          throw new IllegalStateException("Client captured item still has gravity");
        minimumY = Math.min(minimumY, item.getY());
        maximumY = Math.max(maximumY, item.getY());
        floatingSamples++;
      }
    }
    if (stage == 0 && mc.screen != null && mc.getOverlay() == null && ticks > 40) {
      mc.options.hideGui = false;
      mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
      mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
      ConnectScreen.startConnecting(
          mc.screen,
          mc,
          ServerAddress.parseString("127.0.0.1:25585"),
          new ServerData("Ritual QA", "127.0.0.1:25585", ServerData.Type.OTHER),
          false,
          null);
      next(1);
    } else if (stage == 1 && mc.player != null && mc.screen == null && wait > 80) {
      mc.player.connection.sendCommand("ritualtest setup");
      mc.player.connection.sendCommand("ritualtest book");
      next(2);
    } else if (stage == 2 && mc.screen instanceof BookScreen && wait > 60) {
      capture("knowledge-book");
      click(45, 204, 0);
      next(3);
    } else if (stage == 3 && wait > 15) {
      check(
          !Knowledge.data(
                  ((com.cappleapple.ritualsnotrolls.menu.BookMenu) mc.player.containerMenu).book())
              .autoAdd(),
          "per-book Auto-Add off synchronized");
      click(45, 204, 0);
      next(4);
    } else if (stage == 4 && wait > 15) {
      check(
          Knowledge.data(
                  ((com.cappleapple.ritualsnotrolls.menu.BookMenu) mc.player.containerMenu).book())
              .autoAdd(),
          "per-book Auto-Add on synchronized");
      click(150, 61, 0);
      next(5);
    } else if (stage == 5 && wait > 15) {
      check(
          Knowledge.data(
                      ((com.cappleapple.ritualsnotrolls.menu.BookMenu) mc.player.containerMenu)
                          .book())
                  .entries()
                  .size()
              == 6,
          "tear-out packet removed one entry");
      click(143, 204, 0);
      next(6);
    } else if (stage == 6 && wait > 15) {
      check(
          Knowledge.data(
                      ((com.cappleapple.ritualsnotrolls.menu.BookMenu) mc.player.containerMenu)
                          .book())
                  .entries()
                  .size()
              == 7,
          "gather packet restored loose page");
      mc.reloadResourcePacks().thenRun(() -> reloaded = true);
      next(7);
    } else if (stage == 7 && reloaded && wait > 60) {
      capture("knowledge-book-reloaded");
      check(true, "resource reload completed");
      mc.player.connection.sendCommand("ritualtest ritual");
      next(8);
    } else if (stage == 8 && mc.screen instanceof RitualScreen && wait > 70) {
      var menu = (RitualMenu) mc.player.containerMenu;
      check(menu.slots.isEmpty(), "reference UI has no target or inventory slots");
      check(
          menu.clientState.getList("knowledge", 10).size() == 6,
          "table lists only six nearby known enchantments");
      check(
          menu.clientState.getInt("xp_catalysts") == 2
              && menu.clientState.getLong("xp_cost") == 550
              && Math.abs(menu.clientState.getDouble("xp_multiplier") - 1.2) < 1e-9,
          "two placed catalysts show 550 XP and +20 percent");
      var directions = new java.util.HashSet<net.minecraft.core.Direction>();
      for (int i = 0; i < 8; i++) {
        var pos =
            new net.minecraft.core.BlockPos(
                -3 + (i % 4) * 2, i == 2 || i == 7 ? 66 : 64, i < 4 ? 3 : -3);
        directions.add(
            mc.level
                .getBlockState(pos)
                .getValue(com.cappleapple.ritualsnotrolls.pedestal.PedestalBlock.FACING));
      }
      check(directions.size() == 6, "all six pedestal orientations synchronized to the client");
      click(348, 207, 0);
      next(15);
    } else if (stage == 15 && wait > 10) {
      capture("reference-page-forward");
      click(323, 207, 0);
      next(16);
    } else if (stage == 16 && wait > 10) {
      capture("reference-page-back");
      capture("knowledge-reference");
      mc.player.closeContainer();
      next(9);
    } else if (stage == 9 && mc.screen == null && wait > 15) {
      mc.player.getInventory().selected = 1;
      mc.player.connection.send(new ServerboundSetCarriedItemPacket(1));
      mc.player.drop(false);
      next(10);
    } else if (stage == 10 && wait > 15) {
      mc.player.connection.sendCommand("tp @s 0.5 67 -7 0 25");
      next(11);
    } else if (stage == 11 && wait > 25) {
      capture("automatic-ritual-early");
      next(12);
    } else if (stage == 12 && wait > 50) {
      check(
          floatingSamples >= 35 && maximumY - minimumY < .03,
          "client target stays at a stable height for 50 ticks (range="
              + (maximumY - minimumY)
              + ")");
      check(
          curved && xpDestinations.size() == 2,
          "curved flights include player input to both displayed XP catalysts");
      check(
          orbitParticle != null
              && orbitParticle.isAlive()
              && orbitParticle.getPos().distanceTo(orbitStart) > .2,
          "arrived particles move around the target in an orbit");
      check(!observedRings.isEmpty(), "initial enchantment ring reached the client");
      capture("automatic-ritual-late");
      next(13);
    } else if (stage == 13 && burstParticle != null && ticks - burstStarted > 65) {
      check(
          fadeChecked
              && activeBursts == 0
              && pulseChecked
              && layeredCaptured
              && trailsDraining
              && trailsDrained
              && xpTrailDrained
              && clearanceSamples > 100,
          "completion burst fades and expires after the enchantment");
      var items = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(-3, 63, -3, 3, 68, 3));
      RitualsNotRolls.LOGGER.info(
          "Client QA target trace: alive={} removed={} pos={} stack={} nearby={}",
          capturedTarget.isAlive(),
          capturedTarget.getRemovalReason(),
          capturedTarget.position(),
          capturedTarget.getItem().save(mc.level.registryAccess()),
          items.stream().map(e -> e.position() + " " + e.getItem()).toList());
      check(
          items.stream()
                  .anyMatch(
                      e ->
                          e.getItem().is(Items.NETHERITE_SWORD)
                              && RitualMath.enchantments(e.getItem()).size() >= 3)
              || mc.player.getInventory().items.stream()
                  .anyMatch(
                      stack ->
                          stack.is(Items.NETHERITE_SWORD)
                              && RitualMath.enchantments(stack).size() >= 3),
          "unarmed thrown target automatically enchanted on actual client");
      check(
          items.stream()
              .filter(e -> e.getItem().is(Items.NETHERITE_SWORD))
              .noneMatch(ItemEntity::isNoGravity),
          "released result has normal client gravity");
      long xp =
          com.cappleapple.ritualsnotrolls.data.RitualRules.experienceAtLevel(
                  mc.player.experienceLevel)
              + Math.round(mc.player.experienceProgress * mc.player.getXpNeededForNextLevel());
      check(xp == 845, "two XP catalysts debit exactly 550 of the starting 1395 XP");
      items.stream()
          .filter(e -> e.getItem().is(Items.NETHERITE_SWORD))
          .findFirst()
          .ifPresent(
              e ->
                  RitualsNotRolls.LOGGER.info(
                      "Client QA result: {}", e.getItem().save(mc.level.registryAccess())));
      capture("automatic-ritual-complete");
      mc.player.connection.sendCommand("tp @s -4.5 64.2 0.5 -35 8");
      next(17);
    } else if (stage == 17 && wait > 30) {
      capture("oriented-pedestal-rims");
      mc.player.connection.sendCommand("tp @s 2.5 64.1 6.5 160 -8");
      next(18);
    } else if (stage == 18 && wait > 30) {
      capture("ceiling-pedestal-rim");
      verifyPagePivot();
      mc.player.connection.sendCommand("ritualtest pages");
      next(19);
    } else if (stage == 19 && wait > 40) {
      capture("knowledge-page-centered");
      next(20);
    } else if (stage == 20 && wait > 40) {
      capture("knowledge-page-rotated");
      mc.player.connection.sendCommand("ritualtest subtract");
      next(21);
    } else if (stage == 21 && wait > 35) {
      var pos = new net.minecraft.core.BlockPos(-3, 64, 0);
      var be =
          (com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity) mc.level.getBlockEntity(pos);
      check(
          be.items.getStackInSlot(1).is(RitualsNotRolls.CONSUMPTION_CATALYST)
              && be.items.getStackInSlot(2).is(RitualsNotRolls.SUBTRACTION_CATALYST),
          "both pedestal modifiers synchronize to the client");
      mc.player.connection.sendCommand("ritualtest ritual");
      next(22);
    } else if (stage == 22 && wait > 25 && mc.screen instanceof RitualScreen) {
      var menu = (RitualMenu) mc.player.containerMenu;
      check(
          menu.clientState.getList("knowledge", 10).getCompound(0).getDouble("power") == -145,
          "reference shows -145 potential power with the return visit");
      capture("subtraction-reference");
      mc.player.closeContainer();
      next(23);
    } else if (stage == 23 && wait > 15 && mc.screen == null) {
      mc.player.getInventory().selected = 1;
      mc.player.connection.send(new ServerboundSetCarriedItemPacket(1));
      mc.player.drop(false);
      next(24);
    } else if (stage == 24 && wait == 15) {
      mc.player.connection.sendCommand("tp @s 0.5 67 -7 0 25");
    } else if (stage == 24 && wait == 60) {
      check(inverseSeen, "actual subtraction flights travel from target to pedestal");
      capture("subtraction-inverse-flow");
    } else if (stage == 24 && wait > 240 && disenchantReleased) {
      var sharp =
          mc.level
              .registryAccess()
              .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
              .getHolder(net.minecraft.resources.ResourceLocation.withDefaultNamespace("sharpness"))
              .orElseThrow();
      check(
          mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(-3, 63, -3, 3, 68, 3)).stream()
                  .anyMatch(
                      e ->
                          e.getItem().is(Items.DIAMOND_SWORD)
                              && RitualMath.enchantments(e.getItem()).getLevel(sharp) == 3)
              || mc.player.getInventory().items.stream()
                  .anyMatch(
                      stack ->
                          stack.is(Items.DIAMOND_SWORD)
                              && RitualMath.enchantments(stack).getLevel(sharp) == 3),
          "actual subtraction changes Sharpness V to III");
      check(
          disenchantReleased && tailAfterRelease && !removalBurst,
          "disenchantment releases the item, lets reverse flights finish, and has no burst");
      var be =
          (com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity)
              mc.level.getBlockEntity(new net.minecraft.core.BlockPos(-3, 64, 0));
      check(
          be.items.getStackInSlot(0).isEmpty()
              && !be.items.getStackInSlot(1).isEmpty()
              && !be.items.getStackInSlot(2).isEmpty(),
          "subtraction consumes the marked material and retains both modifiers");
      mc.player.connection.sendCommand("tp @s -4.5 64.5 -3 -27 10");
      next(25);
    } else if (stage == 25 && wait > 30) {
      capture("multiple-pedestal-modifiers");
      RitualsNotRolls.LOGGER.info("CLIENT_ACTION_SMOKE_COMPLETE");
      next(14);
      mc.stop();
    }
    if (ticks % 20 == 0 && Files.exists(mc.gameDirectory.toPath().resolve("stop-smoke"))) mc.stop();
  }
}
