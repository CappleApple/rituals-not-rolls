package com.cappleapple.ritualsnotrolls.gametest;

import com.cappleapple.ritualsnotrolls.RitualsNotRolls;
import mezz.jei.api.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JeiClientProbe implements IModPlugin {
  public static IJeiRuntime runtime;

  public ResourceLocation getPluginUid() {
    return RitualsNotRolls.id("client_probe");
  }

  public void onRuntimeAvailable(IJeiRuntime value) {
    runtime = value;
  }
}
