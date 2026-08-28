package com.nhoryzon.mc.farmersdelight.papo.ce;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Thin wrapper over the CraftEngine API used by this plugin.
 * All lookups must happen after CraftEngine has (re)loaded content.
 */
public final class CraftEngineHook implements Listener {

    private boolean loaded = false;

    public static CraftEngineHook instance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final CraftEngineHook INSTANCE = new CraftEngineHook();
    }

    private CraftEngineHook() {
    }

    @EventHandler
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        this.loaded = true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void markLoaded() {
        this.loaded = true;
    }

    public static BukkitCraftEngine plugin() {
        return BukkitCraftEngine.instance();
    }

    /* ---------------- items ---------------- */

    @Nullable
    public static BukkitItemDefinition item(Key id) {
        return CraftEngineItems.byId(id);
    }

    @Nullable
    public static ItemStack buildItem(Key id) {
        BukkitItemDefinition def = CraftEngineItems.byId(id);
        if (def == null) return null;
        try {
            // the no-arg build uses an empty context; a null player crashes CE 26.7.x
            return def.buildBukkitItem();
        } catch (Throwable ignored) {
            return def.buildBukkitItem((Player) null);
        }
    }

    @Nullable
    public static ItemStack buildItem(Key id, int count) {
        ItemStack stack = buildItem(id);
        if (stack != null) {
            stack.setAmount(count);
        }
        return stack;
    }

    @Nullable
    public static Key customItemId(ItemStack stack) {
        return CraftEngineItems.getCustomItemId(stack);
    }

    public static boolean isCustomItem(ItemStack stack, Key id) {
        if (stack == null || stack.getType().isAir()) return false;
        Key found = CraftEngineItems.getCustomItemId(stack);
        return id.equals(found);
    }

    /* ---------------- blocks ---------------- */

    @Nullable
    public static ImmutableBlockState customBlockState(Block block) {
        return CraftEngineBlocks.getCustomBlockState(block);
    }

    public static boolean placeBlock(Location location, Key blockId, boolean playSound) {
        return CraftEngineBlocks.place(location, blockId, playSound);
    }

    public static boolean placeState(Location location, ImmutableBlockState state, boolean playSound) {
        return CraftEngineBlocks.place(location, state, playSound);
    }

    public static boolean removeBlock(Block block, boolean dropLoot) {
        return CraftEngineBlocks.remove(block, null, false, dropLoot, true);
    }

    @Nullable
    public static BlockDefinition customBlock(Block block) {
        ImmutableBlockState state = customBlockState(block);
        return state == null ? null : state.owner().value();
    }

    public static Map<Key, BlockDefinition> loadedBlocks() {
        return CraftEngineBlocks.loadedBlocks();
    }

    /* ---------------- furniture ---------------- */

    @Nullable
    public static BukkitFurniture placeFurniture(Location location, Key furnitureId) {
        return CraftEngineFurniture.place(location, furnitureId);
    }

    @Nullable
    public static BukkitFurniture furniture(Entity entity) {
        return CraftEngineFurniture.getLoadedFurnitureByMetaEntity(entity);
    }

    public static boolean isFurniture(Entity entity) {
        return CraftEngineFurniture.isFurniture(entity);
    }

    public static void removeFurniture(BukkitFurniture furniture, boolean dropLoot, boolean playSound) {
        CraftEngineFurniture.remove(furniture, (Player) null, dropLoot, playSound);
    }

    public static Map<Key, ?> loadedFurniture() {
        return CraftEngineFurniture.loadedFurniture();
    }

    public static Key key(String id) {
        return Key.of(id);
    }

    /* ---------------- reload ---------------- */

    /**
     * Reloads CE content and REGENERATES the resource pack. Critical: a plain
     * config reload does not rebuild the pack; without regeneration clients keep
     * stale item model definitions and custom furniture renders invisible.
     */
    public static void reloadContent() {
        BukkitCraftEngine ce = plugin();
        var async = (java.util.concurrent.Executor) task ->
                Bukkit.getScheduler().runTaskAsynchronously(ce.javaPlugin(), task);
        ce.reloadPlugin(async,
                task -> Bukkit.getScheduler().runTask(ce.javaPlugin(), task), true)
                .thenAcceptAsync(result -> {
                    if (result.success()) {
                        try {
                            ce.packManager().generateResourcePack();
                            ce.packManager().uploadResourcePack();
                        } catch (Throwable t) {
                            ce.logger().warn("FD pack: failed to regenerate/upload resource pack", t);
                        }
                    } else {
                        ce.logger().warn("FD pack: CraftEngine reload reported failure");
                    }
                }, async);
    }
}
