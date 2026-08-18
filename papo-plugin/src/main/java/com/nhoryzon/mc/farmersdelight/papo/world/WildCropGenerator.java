package com.nhoryzon.mc.farmersdelight.papo.world;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Places wild FD crops when new chunks load, following the mod's biome rules:
 * beaches for cabbages/beetroots, swamps+jungle for wild rice, temperature bands for the rest.
 * A PDC marker ensures each chunk is only decorated once.
 */
public final class WildCropGenerator implements Listener {

    private final Plugin plugin;
    private final NamespacedKey decorated;

    public WildCropGenerator(Plugin plugin) {
        this.plugin = plugin;
        this.decorated = new NamespacedKey(plugin, "wild_decorated");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (event.isNewChunk() && !chunk.getPersistentDataContainer().has(decorated, PersistentDataType.BYTE)) {
            chunk.getPersistentDataContainer().set(decorated, PersistentDataType.BYTE, (byte) 1);
            decorate(chunk);
        }
    }

    private void decorate(Chunk chunk) {
        if (chunk.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
        Random random = ThreadLocalRandom.current();
        // rarity gate (mod: ~1 patch per 30 chunks)
        if (random.nextInt(30) != 0) return;

        int baseX = (chunk.getX() << 4) + random.nextInt(16);
        int baseZ = (chunk.getZ() << 4) + random.nextInt(16);
        Biome biome = chunk.getWorld().getBiome(baseX, 64, baseZ);
        String crop = pickCrop(biome, random);
        if (crop == null) return;

        placePatch(chunk.getWorld(), baseX, baseZ, crop, random);
    }

    private void placePatch(org.bukkit.World world, int x, int z, String crop, Random random) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!tryPlace(world, x, y, z, crop)) return;
        int extra = random.nextInt(4);
        for (int i = 0; i < extra; i++) {
            int ox = x + random.nextInt(3) - 1;
            int oz = z + random.nextInt(3) - 1;
            int oy = world.getHighestBlockYAt(ox, oz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            tryPlace(world, ox, oy, oz, crop);
        }
    }

    private boolean tryPlace(org.bukkit.World world, int x, int y, int z, String crop) {
        if (y <= world.getMinHeight()) return false;
        Block surface = world.getBlockAt(x, y, z);
        Block ground = surface.getRelative(BlockFace.DOWN);
        Material groundType = ground.getType();
        if (surface.getType() == Material.WATER) {
            return "wild_rice".equals(crop)
                    && CraftEngineHook.placeBlock(surface.getLocation(), Key.of(FD.MOD_ID, crop), false);
        }
        if (surface.getType() != Material.AIR) return false;
        if (groundType != Material.GRASS_BLOCK && groundType != Material.SAND
                && groundType != Material.DIRT && groundType != Material.MUD
                && groundType != Material.PODZOL && groundType != Material.MOSS_BLOCK) {
            return false;
        }
        return CraftEngineHook.placeBlock(surface.getLocation(), Key.of(FD.MOD_ID, crop), false);
    }

    private String pickCrop(Biome biome, Random random) {
        if (biome == org.bukkit.block.Biome.BEACH || biome == org.bukkit.block.Biome.SNOWY_BEACH) {
            return random.nextBoolean() ? "wild_cabbages" : "wild_beetroots";
        }
        if (biome == org.bukkit.block.Biome.SWAMP || biome == org.bukkit.block.Biome.MANGROVE_SWAMP) {
            return "wild_rice";
        }
        if (biome == org.bukkit.block.Biome.JUNGLE || biome == org.bukkit.block.Biome.BAMBOO_JUNGLE
                || biome == org.bukkit.block.Biome.SPARSE_JUNGLE) {
            return random.nextBoolean() ? "wild_rice" : "wild_tomatoes";
        }
        if (biome == org.bukkit.block.Biome.DESERT) {
            return "wild_tomatoes";
        }
        if (biome == org.bukkit.block.Biome.SAVANNA || biome == org.bukkit.block.Biome.SAVANNA_PLATEAU
                || biome == org.bukkit.block.Biome.WINDSWEPT_SAVANNA) {
            return random.nextBoolean() ? "wild_carrots" : "wild_onions";
        }
        if (biome == org.bukkit.block.Biome.TAIGA || biome == org.bukkit.block.Biome.SNOWY_TAIGA
                || biome == org.bukkit.block.Biome.OLD_GROWTH_PINE_TAIGA
                || biome == org.bukkit.block.Biome.OLD_GROWTH_SPRUCE_TAIGA) {
            return "wild_potatoes";
        }
        if (biome == org.bukkit.block.Biome.PLAINS || biome == org.bukkit.block.Biome.SUNFLOWER_PLAINS
                || biome == org.bukkit.block.Biome.MEADOW) {
            return random.nextBoolean() ? "wild_carrots" : "wild_onions";
        }
        if (biome == org.bukkit.block.Biome.FOREST || biome == org.bukkit.block.Biome.BIRCH_FOREST
                || biome == org.bukkit.block.Biome.DARK_FOREST
                || biome == org.bukkit.block.Biome.OLD_GROWTH_BIRCH_FOREST) {
            return random.nextBoolean() ? "wild_potatoes" : "wild_carrots";
        }
        return null;
    }
}
