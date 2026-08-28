package com.nhoryzon.mc.farmersdelight.papo.data;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tracks positions of ticking custom blocks (crops, compost, baskets, stoves) per chunk
 * so background tasks can find them without scanning whole chunks.
 * Entries are packed {@code (localX << 8 | localZ << 4 | y} longs stored in chunk PDC.
 */
public final class ChunkIndex {

    private final Plugin plugin;
    private final NamespacedKey key;
    /**
     * Deserialized entries per chunk; the ticker reads the same chunk's index 4-5 times
     * per pulse, so the PDC parse is done once. Invalidated on every write. Callers
     * must treat the returned list as read-only.
     */
    private final Map<Chunk, List<Location>> entriesCache =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public ChunkIndex(Plugin plugin, String type) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "idx." + type);
    }

    private static long pack(Block block) {
        int x = block.getX() & 15;
        int z = block.getZ() & 15;
        return ((long) x << 20) | ((long) z << 16) | (block.getY() & 0xFFFF);
    }

    private static Location unpack(Chunk chunk, long packed) {
        int x = (int) ((packed >> 20) & 15);
        int z = (int) ((packed >> 16) & 15);
        int y = (int) (packed & 0xFFFF);
        if ((y & 0x8000) != 0) y |= 0xFFFF0000; // sign extend
        return new Location(chunk.getWorld(), (chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z);
    }

    public void add(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] arr = pdc.get(key, PersistentDataType.LONG_ARRAY);
        long packed = pack(block);
        if (arr == null) {
            pdc.set(key, PersistentDataType.LONG_ARRAY, new long[]{packed});
            entriesCache.remove(block.getChunk());
            return;
        }
        for (long v : arr) {
            if (v == packed) return;
        }
        long[] next = new long[arr.length + 1];
        System.arraycopy(arr, 0, next, 0, arr.length);
        next[arr.length] = packed;
        pdc.set(key, PersistentDataType.LONG_ARRAY, next);
        entriesCache.remove(block.getChunk());
    }

    public boolean remove(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] arr = pdc.get(key, PersistentDataType.LONG_ARRAY);
        if (arr == null) return false;
        long packed = pack(block);
        long[] next = new long[arr.length - 1];
        int j = 0;
        boolean found = false;
        for (long v : arr) {
            if (v == packed && !found) {
                found = true;
                continue;
            }
            if (j < next.length) next[j++] = v;
        }
        if (found) {
            pdc.set(key, PersistentDataType.LONG_ARRAY, next);
            entriesCache.remove(block.getChunk());
        }
        return found;
    }

    public boolean contains(Block block) {
        long[] arr = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        if (arr == null) return false;
        long packed = pack(block);
        for (long v : arr) {
            if (v == packed) return true;
        }
        return false;
    }

    /** Read-only view; cached per chunk until the next write to that chunk's index. */
    public List<Location> entries(Chunk chunk) {
        List<Location> cached = entriesCache.get(chunk);
        if (cached != null) return cached;
        long[] arr = chunk.getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        List<Location> out = new ArrayList<>();
        if (arr != null) {
            for (long v : arr) {
                out.add(unpack(chunk, v));
            }
        }
        entriesCache.put(chunk, out);
        return out;
    }
}
