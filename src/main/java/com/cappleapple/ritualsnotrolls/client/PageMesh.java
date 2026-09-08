package com.cappleapple.ritualsnotrolls.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** A centered, one-pixel-thick item silhouette, including transparent texture cutouts. */
public final class PageMesh {
  public static final float BACK = 7.5f / 16, FRONT = 8.5f / 16;

  public record Vertex(float x, float y, float z, float u, float v) {}

  public record Face(List<Vertex> vertices, float nx, float ny, float nz) {}

  private static final Map<ResourceLocation, List<Face>> CACHE = new HashMap<>();

  public static void clearCache() {
    CACHE.clear();
  }

  private static List<Face> load(ResourceLocation texture) {
    try (var stream = Minecraft.getInstance().getResourceManager().open(texture);
        var image = NativeImage.read(stream)) {
      return generate(
          image.getWidth(), image.getHeight(), (x, y) -> (image.getPixelRGBA(x, y) >>> 24) > 0);
    } catch (IOException e) {
      return generate(1, 1, (x, y) -> true);
    }
  }

  public static void render(
      ResourceLocation texture, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
    for (var face : CACHE.computeIfAbsent(texture, PageMesh::load))
      for (var v : face.vertices())
        buffer
            .addVertex(pose.last().pose(), v.x(), v.y(), v.z())
            .setColor(255, 255, 255, 255)
            .setUv(v.u(), v.v())
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose.last(), face.nx(), face.ny(), face.nz());
  }

  public static List<Face> generate(int width, int height, BiPredicate<Integer, Integer> opaque) {
    List<Face> faces = new ArrayList<>();
    faces.add(
        new Face(
            List.of(
                new Vertex(0, 0, FRONT, 0, 1),
                new Vertex(1, 0, FRONT, 1, 1),
                new Vertex(1, 1, FRONT, 1, 0),
                new Vertex(0, 1, FRONT, 0, 0)),
            0,
            0,
            1));
    faces.add(
        new Face(
            List.of(
                new Vertex(1, 0, BACK, 1, 1),
                new Vertex(0, 0, BACK, 0, 1),
                new Vertex(0, 1, BACK, 0, 0),
                new Vertex(1, 1, BACK, 1, 0)),
            0,
            0,
            -1));
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        if (!opaque.test(x, y)) continue;
        float left = x / (float) width,
            right = (x + 1f) / width,
            top = 1 - y / (float) height,
            bottom = 1 - (y + 1f) / height;
        float u = (x + .5f) / width, v = (y + .5f) / height;
        if (x == 0 || !opaque.test(x - 1, y)) edge(faces, left, bottom, left, top, u, v, -1, 0);
        if (x == width - 1 || !opaque.test(x + 1, y))
          edge(faces, right, top, right, bottom, u, v, 1, 0);
        if (y == 0 || !opaque.test(x, y - 1)) edge(faces, left, top, right, top, u, v, 0, 1);
        if (y == height - 1 || !opaque.test(x, y + 1))
          edge(faces, right, bottom, left, bottom, u, v, 0, -1);
      }
    return List.copyOf(faces);
  }

  private static void edge(
      List<Face> faces,
      float x1,
      float y1,
      float x2,
      float y2,
      float u,
      float v,
      float nx,
      float ny) {
    faces.add(
        new Face(
            List.of(
                new Vertex(x1, y1, BACK, u, v),
                new Vertex(x1, y1, FRONT, u, v),
                new Vertex(x2, y2, FRONT, u, v),
                new Vertex(x2, y2, BACK, u, v)),
            nx,
            ny,
            0));
  }
}
