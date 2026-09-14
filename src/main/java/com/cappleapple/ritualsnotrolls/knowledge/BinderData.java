package com.cappleapple.ritualsnotrolls.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Immutable page storage; quantities are separate from normal item stack size limits. */
public record BinderData(List<Entry> entries, boolean autoCollect, boolean filterDuplicates) {
  public static final int MAX_CAPACITY = 1_048_576;
  public static final BinderData EMPTY = new BinderData(List.of(), true, true);
  public static final Codec<BinderData> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      Entry.CODEC.listOf().fieldOf("entries").forGetter(BinderData::entries),
                      Codec.BOOL
                          .optionalFieldOf("auto_collect", true)
                          .forGetter(BinderData::autoCollect),
                      Codec.BOOL
                          .optionalFieldOf("filter_duplicates", true)
                          .forGetter(BinderData::filterDuplicates))
                  .apply(i, BinderData::new));
  public static final StreamCodec<RegistryFriendlyByteBuf, BinderData> STREAM =
      StreamCodec.of(
          (buf, data) -> {
            buf.writeVarInt(data.entries.size());
            for (Entry entry : data.entries) {
              ItemStack.STREAM_CODEC.encode(buf, entry.page());
              buf.writeVarInt(entry.count());
            }
            buf.writeBoolean(data.autoCollect);
            buf.writeBoolean(data.filterDuplicates);
          },
          buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_CAPACITY)
              throw new IllegalArgumentException("Invalid binder entry count");
            var entries = new java.util.ArrayList<Entry>(size);
            long total = 0;
            for (int index = 0; index < size; index++) {
              ItemStack page = ItemStack.STREAM_CODEC.decode(buf);
              int count = buf.readVarInt();
              total += count;
              if (total > MAX_CAPACITY)
                throw new IllegalArgumentException("Binder capacity exceeded");
              entries.add(new Entry(page, count));
            }
            return new BinderData(entries, buf.readBoolean(), buf.readBoolean());
          });

  public BinderData {
    entries = List.copyOf(entries);
    long total = entries.stream().mapToLong(Entry::count).sum();
    if (total > MAX_CAPACITY) throw new IllegalArgumentException("Binder capacity exceeded");
  }

  public int total() {
    return entries.stream().mapToInt(Entry::count).sum();
  }

  public BinderData withAutoCollect(boolean enabled) {
    return new BinderData(entries, enabled, filterDuplicates);
  }

  public BinderData withFilterDuplicates(boolean enabled) {
    return new BinderData(entries, autoCollect, enabled);
  }

  public record Entry(ItemStack page, int count) {
    public static final Codec<Entry> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        ItemStack.CODEC.fieldOf("page").forGetter(Entry::page),
                        Codec.intRange(1, MAX_CAPACITY).fieldOf("count").forGetter(Entry::count))
                    .apply(i, Entry::new));

    public Entry {
      if (!BinderStorage.isPage(page) || count < 1 || count > MAX_CAPACITY)
        throw new IllegalArgumentException("Invalid binder page entry");
      page = page.copyWithCount(1);
    }

    @Override
    public ItemStack page() {
      return page.copy();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Entry entry
          && count == entry.count
          && ItemStack.isSameItemSameComponents(page, entry.page);
    }

    @Override
    public int hashCode() {
      return Objects.hash(page.getItem(), page.getComponents(), count);
    }
  }
}
