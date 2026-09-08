package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.*;
import com.cappleapple.ritualsnotrolls.client.*;
import com.cappleapple.ritualsnotrolls.compat.*;
import com.cappleapple.ritualsnotrolls.ritual.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.*;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = RitualsNotRolls.ID, value = Dist.CLIENT)
public final class RefinementClientSmoke {
  static int ticks, stage, wait, first = -1, second = -1;
  static boolean stable, shaky, falling, whole, burst, tail, released, ringCaptured;
  static Particle fallingParticle;
  static Vec3 fallStart;
  static int fallTime;
  static int alpha = -1;
  static boolean faded, noiseCaptured;
  static int scatterDirections;

  static Minecraft mc() {
    return Minecraft.getInstance();
  }

  static void check(boolean ok, String message) {
    if (!ok) throw new IllegalStateException(message);
    RitualsNotRolls.LOGGER.info("REFINEMENT CLIENT PASS: {}", message);
  }

  static void next(int value) {
    stage = value;
    wait = 0;
    stable = shaky = falling = whole = burst = tail = released = faded = false;
    fallingParticle = null;
    scatterDirections = 0;
    noiseCaptured = false;
    alpha = -1;
  }

  static Object field(Object object, Class<?> type, String name)
      throws ReflectiveOperationException {
    var f = type.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(object);
  }

  static ItemStack held(BlockPos pos) throws ReflectiveOperationException {
    var be = mc().level.getBlockEntity(pos);
    return be instanceof net.minecraft.world.Container c
        ? c.getItem(0)
        : (ItemStack) be.getClass().getMethod("getHeldItem").invoke(be);
  }

  static void capture(String name) {
    Screenshot.grab(
        mc().gameDirectory,
        name + ".png",
        mc().getMainRenderTarget(),
        c -> RitualsNotRolls.LOGGER.info("REFINEMENT screenshot {}", name));
  }

  static List<ItemEntity> targets() {
    return mc().level.getEntitiesOfClass(ItemEntity.class, new AABB(-2, 64, -2, 3, 69, 3)).stream()
        .filter(e -> e.getItem().is(Items.DIAMOND_SWORD))
        .toList();
  }

  static void sounds() throws ReflectiveOperationException {
    var manager = mc().getSoundManager();
    var engine = field(manager, manager.getClass(), "soundEngine");
    var calculate = SoundEngine.class.getDeclaredMethod("calculatePitch", SoundInstance.class);
    calculate.setAccessible(true);
    Set<Integer> pitches = new HashSet<>();
    for (String name :
        List.of(
            "capture",
            "knowledge",
            "material",
            "experience",
            "complete",
            "consumption",
            "disenchant",
            "failure")) {
      for (int n = 0; n < (name.equals("failure") ? 100 : 1); n++) {
        var sound = SimpleSoundInstance.forUI(RitualsNotRolls.SOUNDS.get(name).get(), 1, 1);
        sound.resolve(manager);
        float pitch = (float) calculate.invoke(engine, sound);
        if (name.equals("failure")) {
          check(pitch >= .199 && pitch <= .601, "failure pitch within configured range " + pitch);
          pitches.add(Math.round(pitch * 10));
        }
        if (name.equals("consumption"))
          check(
              Math.abs(pitch - .625) < .001,
              "Allay event pitch .5 multiplies vanilla asset pitch 1.25");
        if (name.equals("disenchant"))
          check(Math.abs(pitch - .6) < .001, "Beacon disenchant pitch .6");
        check(sound.getVolume() > 0, "Resolved sound " + name);
      }
    }
    check(
        pitches.equals(Set.of(2, 3, 4, 5, 6)),
        "All five failure pitches resolve, including below vanilla clamp");
  }

  static void inspect() throws ReflectiveOperationException {
    var groups = (Map<?, ?>) field(mc().particleEngine, ParticleEngine.class, "particles");
    if (targets().stream().anyMatch(e -> !e.isNoGravity())) released = true;
    for (var group : groups.values())
      for (var value : (Collection<?>) group) {
        var p = (Particle) value;
        if (!p.getClass().getName().endsWith("RitualParticleProvider$Flying")) continue;
        var f = (RitualParticleOptions.Flight) field(p, p.getClass(), "flight");
        int age = (int) field(p, Particle.class, "age");
        if (f.burst()) burst = true;
        if (f.route().isPresent() && f.route().get().points().size() >= 7) whole = true;
        if (stage == 5 && released && !f.burst() && !f.orbit() && age < f.travel()) tail = true;
        if (stage == 3
            && !ringCaptured
            && f.orbit()
            && !f.instability().fails()
            && age >= f.travel() + 24) {
          ringCaptured = true;
          capture("continuous-spline-orbit");
        }
        if (!f.instability().fails()) continue;
        if (f.instability().amplitude(age) == 0) stable = true;
        if (f.instability().amplitude(age) > .2) {
          shaky = true;
          if (!noiseCaptured && f.instability().age() + age < f.instability().failAt()) {
            noiseCaptured = true;
            capture("failure-noisy-trail-" + stage);
          }
        }
        double sinceFailure = f.instability().age() + age - f.instability().failAt();
        if (sinceFailure > 0 && sinceFailure <= 3) {
          var start = (Vec3) field(p, p.getClass(), "start");
          double lead = (double) field(p, p.getClass(), "travelLead");
          var origin =
              RitualFlight.position(
                  start, f, f.instability().failAt() - f.instability().age(), lead);
          var delta = p.getPos().subtract(origin);
          if (delta.x > .02) scatterDirections |= 1;
          if (delta.x < -.02) scatterDirections |= 2;
          if (delta.y > .02) scatterDirections |= 4;
          if (delta.y < -.02) scatterDirections |= 8;
          if (delta.z > .02) scatterDirections |= 16;
          if (delta.z < -.02) scatterDirections |= 32;
        }
        if (f.instability().age() + age > f.instability().failAt() + 3) {
          falling = true;
          if (fallingParticle == null) {
            fallingParticle = p;
            fallStart = p.getPos();
            fallTime = ticks;
            capture("failure-collapse-" + stage);
          }
        }
      }
    if (fallingParticle != null && fallingParticle.isAlive()) {
      var probe = new ClientActionSmoke.AlphaProbe();
      fallingParticle.render(probe, mc().gameRenderer.getMainCamera(), 0);
      if (alpha < 0) alpha = probe.maximum;
      if (ticks - fallTime >= 25 && !faded) {
        check(
            fallingParticle.getPos().y < fallStart.y - 2 && probe.maximum < alpha,
            "Scattered particles fall under gravity with fading vertex opacity");
        faded = true;
      }
    }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("ritualsnotrolls.refinementSmoke")) return;
    ticks++;
    wait++;
    try {
      if (stage == 2 && wait == 25) {
        mc().options.hideGui = true;
        mc().options.fov().set(50);
        mc().getToasts().clear();
        mc().player.getAbilities().flying = true;
        mc().player.onUpdateAbilities();
        mc().player.connection.sendCommand("tp @s 0.5 68 -7 0 25");
      }
      if (ticks > 2600)
        throw new IllegalStateException("Refinement client timed out at stage " + stage);
      if (stage >= 3 && stage <= 5 && mc().level != null) inspect();
      if (stage == 0 && mc().screen != null && mc().getOverlay() == null && ticks > 40) {
        mc().options.pauseOnLostFocus = false;
        mc().options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        ConnectScreen.startConnecting(
            mc().screen,
            mc(),
            ServerAddress.parseString("127.0.0.1:25589"),
            new ServerData("Refinement QA", "127.0.0.1:25589", ServerData.Type.OTHER),
            false,
            null);
        next(1);
      } else if (stage == 1 && mc().player != null && mc().screen == null && wait > 60) {
        mc().player.connection.sendCommand("ritualrefine setup");
        next(2);
      } else if (stage == 2 && wait > 60) {
        for (var pos : List.of(RefinementFixture.SUPP, RefinementFixture.IRON)) {
          var be = mc().level.getBlockEntity(pos);
          var modifiers = ForeignPedestals.modifiers(be);
          check(
              !held(pos).isEmpty()
                  && !modifiers.getStackInSlot(0).isEmpty()
                  && !modifiers.getStackInSlot(1).isEmpty(),
              "Native held item and both modifiers synchronized " + pos);
          var probe = new ClientActionSmoke.AlphaProbe();
          ForeignPedestalRenderer.render(
              be, new com.mojang.blaze3d.vertex.PoseStack(), type -> probe);
          check(probe.vertices > 96, "Both modifiers render on native pedestal faces " + pos);
        }
        sounds();
        capture("native-pedestal-modifiers");
        mc().player.connection.sendCommand("ritualrefine start");
        next(3);
      } else if (stage == 3) {
        if (first < 0 && held(RefinementFixture.SUPP).isEmpty()) first = ticks;
        if (second < 0 && held(RefinementFixture.IRON).isEmpty()) second = ticks;
        if (wait == 150) capture("continuous-chain-rings");
        if (wait > 500) {
          check(
              first >= 0 && second > first, "Native materials disappear in particle arrival order");
          check(
              whole && stable && shaky && falling && faded && burst && scatterDirections == 63,
              "Complete spline, stable start, smooth noise, all-direction failure scatter and"
                  + " successful burst coexist");
          check(
              targets().stream()
                  .anyMatch(
                      e -> !e.isNoGravity() && !RitualMath.enchantments(e.getItem()).isEmpty()),
              "Mixed ritual completes successful enchantment");
          check(
              ((com.cappleapple.ritualsnotrolls.pedestal.PedestalEntity)
                      mc().level.getBlockEntity(RefinementFixture.WEAK))
                  .items
                  .getStackInSlot(0)
                  .is(Items.GOLD_NUGGET),
              "Failed chain retains consumption-marked item");
          for (var pos : List.of(RefinementFixture.SUPP, RefinementFixture.IRON))
            check(
                !ForeignPedestals.modifiers(mc().level.getBlockEntity(pos))
                    .getStackInSlot(0)
                    .isEmpty(),
                "Modifier survives native held-item sync");
          mc().player.connection.sendCommand("ritualrefine failure");
          next(4);
        }
      } else if (stage == 4 && wait > 350) {
        check(
            stable && shaky && falling && faded && !burst && scatterDirections == 63,
            "Failed-only chain scatters up, down and every lateral direction, falls and fades"
                + " without success burst");
        check(
            targets().stream()
                .anyMatch(e -> !e.isNoGravity() && RitualMath.enchantments(e.getItem()).isEmpty()),
            "Failed-only ritual releases unchanged tool");
        mc().player.connection.sendCommand("ritualrefine subtract");
        next(5);
      } else if (stage == 5 && wait > 400) {
        check(released && tail && !burst, "Pure disenchantment drains after release without burst");
        capture("native-disenchantment-complete");
        RitualsNotRolls.LOGGER.info("REFINEMENT_CLIENT_SMOKE_COMPLETE");
        next(6);
        mc().stop();
      }
    } catch (Exception e) {
      RitualsNotRolls.LOGGER.error("REFINEMENT_CLIENT_SMOKE_FAILED stage=" + stage, e);
      mc().stop();
    }
  }
}
