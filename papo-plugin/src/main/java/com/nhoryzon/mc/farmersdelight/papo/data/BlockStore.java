package com.nhoryzon.mc.farmersdelight.papo.data;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-position block data stored in the containing chunk's PersistentDataContainer.
 * Keys look like {@code fd.<x>.<y>.<z>.<field>} (relative to the chunk).
 */
public final class BlockStore {

    private final Plugin plugin;

    public BlockStore(Plugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(Block block, String field) {
        int x = block.getX() & 15;
        int z = block.getZ() & 15;
        return new NamespacedKey(plugin, "fd." + x + "." + block.getY() + "." + z + "." + field);
    }

    public PersistentDataContainer chunkPdc(Block block) {
        Chunk chunk = block.getChunk();
        return chunk.getPersistentDataContainer();
    }

    public Integer getInt(Block block, String field) {
        return chunkPdc(block).get(key(block, field), PersistentDataType.INTEGER);
    }

    /** int primitive variant: a missing field reads as {@code def} instead of null. */
    public int getInt(Block block, String field, int def) {
        Integer value = getInt(block, field);
        return value == null ? def : value;
    }

    public void setInt(Block block, String field, int value) {
        chunkPdc(block).set(key(block, field), PersistentDataType.INTEGER, value);
    }

    public String getString(Block block, String field) {
        return chunkPdc(block).get(key(block, field), PersistentDataType.STRING);
    }

    public void setString(Block block, String field, String value) {
        chunkPdc(block).set(key(block, field), PersistentDataType.STRING, value);
    }

    public ItemStack[] getItems(Block block, String field) {
        byte[] bytes = chunkPdc(block).get(key(block, field), PersistentDataType.BYTE_ARRAY);
        if (bytes == null) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int n = in.readInt();
            List<org.bukkit.inventory.ItemStack> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int len = in.readInt();
                if (len < 0) {
                    list.add(null);
                    continue;
                }
                byte[] data = new byte[len];
                in.readFully(data);
                list.add(ItemStack.deserializeBytes(data));
            }
            return list.toArray(new ItemStack[0]);
        } catch (IOException e) {
            return null;
        }
    }

    public void setItems(Block block, String field, ItemStack[] items) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        try {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    out.writeInt(-1);
                    continue;
                }
                byte[] data = item.serializeAsBytes();
                out.writeInt(data.length);
                out.write(data);
            }
        } catch (IOException ignored) {
        }
        chunkPdc(block).set(key(block, field), PersistentDataType.BYTE_ARRAY, buffer.toByteArray());
    }

    public ItemStack getItem(Block block, String field) {
        ItemStack[] arr = getItems(block, field);
        if (arr == null || arr.length == 0) return null;
        return arr[0];
    }

    public void setItem(Block block, String field, ItemStack item) {
        setItems(block, field, new ItemStack[]{item});
    }

    public void clear(Block block, String field) {
        chunkPdc(block).remove(key(block, field));
    }

    public void clearAll(Block block, List<String> fields) {
        PersistentDataContainer pdc = chunkPdc(block);
        for (String field : fields) {
            pdc.remove(key(block, field));
        }
    }
}
