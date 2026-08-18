package com.nhoryzon.mc.farmersdelight.papo.entity;

import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a live registry of Farmer's Delight furniture (cooking pots, cutting boards,
 * skillets, ...) so background tickers can find them. Furniture is identified by the
 * {@code craftengine:furniture_id} PDC tag on its base display entity.
 */
public final class FurnitureTracker implements Listener {

    public record Entry(Key furnitureId, BukkitFurniture furniture) {
    }

    private final Plugin plugin;
    private final Map<UUID, Entry> tracked = new ConcurrentHashMap<>();

    public FurnitureTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (var world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        tracked.clear();
    }

    public Map<UUID, Entry> tracked() {
        return tracked;
    }

    public void track(Entity baseEntity) {
        BukkitFurniture furniture = CraftEngineFurniture(baseEntity);
        if (furniture != null) {
            tracked.put(baseEntity.getUniqueId(), new Entry(furniture.id(), furniture));
        }
    }

    private BukkitFurniture CraftEngineFurniture(Entity entity) {
        return CraftEngineHook.furniture(entity);
    }

    public void untrack(Entity baseEntity) {
        tracked.remove(baseEntity.getUniqueId());
    }

    private void scanChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            String id = entity.getPersistentDataContainer()
                    .get(BukkitFurnitureManager.FURNITURE_KEY, PersistentDataType.STRING);
            if (id != null && id.startsWith(FD.MOD_ID + ":")) {
                track(entity);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            tracked.remove(entity.getUniqueId());
        }
    }

    /** Resolves furniture of a given id at (or overlapping) a block position. */
    public Entry at(org.bukkit.Location location, Key furnitureId) {
        for (Entry entry : tracked.values()) {
            if (!entry.furnitureId().equals(furnitureId)) continue;
            org.bukkit.Location loc = entry.furniture().location();
            if (loc.getBlockX() == location.getBlockX()
                    && loc.getBlockY() == location.getBlockY()
                    && loc.getBlockZ() == location.getBlockZ()) {
                return entry;
            }
        }
        return null;
    }
}
