package com.nhoryzon.mc.farmersdelight.papo.logic;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.data.BlockStore;
import com.nhoryzon.mc.farmersdelight.papo.data.ChunkIndex;
import com.nhoryzon.mc.farmersdelight.papo.entity.FurnitureTracker;
import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Central background task driving every ticking mechanic: cooking pots, stoves,
 * skillets, baskets, organic compost and crop growth. Runs twice a second.
 */
public final class GameTicker {

    public static final int SLOT_INPUTS = 6;
    public static final int SLOT_MEAL = 6;
    public static final int SLOT_CONTAINER = 7;
    public static final int SLOT_OUTPUT = 8;

    private final FarmersDelightPlugin plugin;
    private final BukkitTask task;

    public final ChunkIndex stoveIndex;
    public final ChunkIndex basketIndex;
    public final ChunkIndex compostIndex;
    public final ChunkIndex cropIndex;
    public final ChunkIndex soilIndex;

    public GameTicker(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
        this.stoveIndex = new ChunkIndex(plugin, "stove");
        this.basketIndex = new ChunkIndex(plugin, "basket");
        this.compostIndex = new ChunkIndex(plugin, "compost");
        this.cropIndex = new ChunkIndex(plugin, "crop");
        this.soilIndex = new ChunkIndex(plugin, "soil");
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private FarmersDelightPlugin plugin() {
        return plugin;
    }

    public void shutdown() {
        task.cancel();
    }

    /* ===================== PDC helpers on furniture entities ===================== */

    public static PersistentDataContainer data(BukkitFurniture furniture) {
        return furniture.baseEntity().getPersistentDataContainer();
    }

    private static java.lang.reflect.Field STORAGE_INV_FIELD;

    static {
        try {
            STORAGE_INV_FIELD = net.momirealms.craftengine.bukkit.entity.furniture.behavior.
                    SimpleStorageFurnitureBehaviorTemplate.SimpleStorageFurnitureController.class
                    .getDeclaredField("inventory");
            STORAGE_INV_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            STORAGE_INV_FIELD = null;
        }
    }

    /** CE simple_storage_furniture inventory of this furniture (single storage backend). */
    public static org.bukkit.inventory.Inventory ceFurnitureInventory(BukkitFurniture furniture) {
        if (STORAGE_INV_FIELD == null || furniture == null) return null;
        try {
            if (furniture.controller instanceof net.momirealms.craftengine.bukkit.entity.furniture.behavior
                    .SimpleStorageFurnitureBehaviorTemplate.SimpleStorageFurnitureController controller) {
                return (org.bukkit.inventory.Inventory) STORAGE_INV_FIELD.get(controller);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static ItemStack[] inv(BukkitFurniture furniture) {
        org.bukkit.inventory.Inventory ce = ceFurnitureInventory(furniture);
        if (ce != null) {
            ItemStack[] out = new ItemStack[9];
            ItemStack[] contents = ce.getContents();
            for (int i = 0; i < 9 && i < contents.length; i++) {
                ItemStack c = contents[i];
                out[i] = (c == null || c.getType().isAir()) ? null : c;
            }
            return out;
        }
        ItemStack[] out = new ItemStack[9];
        byte[] bytes = data(furniture).get(invKey(), PersistentDataType.BYTE_ARRAY);
        if (bytes == null) return out;
        for (ItemStack stack : ItemStack.deserializeItemsFromBytes(bytes)) {
            for (int i = 0; i < 9; i++) {
                if (out[i] == null) {
                    if (stack != null && !stack.getType().isAir()) out[i] = stack;
                    break;
                }
            }
        }
        return out;
    }

    public static void saveInv(BukkitFurniture furniture, ItemStack[] items) {
        org.bukkit.inventory.Inventory ce = ceFurnitureInventory(furniture);
        if (ce != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack item = (items != null && i < items.length && items[i] != null
                        && !items[i].getType().isAir()) ? items[i] : null;
                ce.setItem(i, item);
            }
            return;
        }
        java.util.List<ItemStack> list = new java.util.ArrayList<>();
        for (ItemStack stack : items) {
            list.add(stack == null ? new ItemStack(Material.AIR) : stack);
        }
        data(furniture).set(invKey(), PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(list));
    }

    private static NamespacedKey invKey() {
        return key("inv");
    }

    private static NamespacedKey key(String k) {
        return new NamespacedKey(FarmersDelightPlugin.get(), "fd." + k);
    }

    public static NamespacedKey fdKey(String k) {
        return key(k);
    }

    /* ===================== main tick ===================== */

    private void tick() {
        // --- furniture based mechanics
        Iterator<Map.Entry<UUID, FurnitureTracker.Entry>> it =
                plugin.furnitureTracker().tracked().entrySet().iterator();
        while (it.hasNext()) {
            FurnitureTracker.Entry entry = it.next().getValue();
            if (!entry.furniture().isValid()) {
                it.remove();
                continue;
            }
            if (entry.furnitureId().equals(FD.COOKING_POT)) {
                tickPot(entry.furniture());
            } else if (entry.furnitureId().equals(FD.SKILLET)) {
                tickSkillet(entry.furniture());
            }
        }
        // --- block based mechanics
        for (World world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                for (Location loc : stoveIndex.entries(chunk)) tickStove(loc.getBlock());
                for (Location loc : basketIndex.entries(chunk)) tickBasket(loc.getBlock());
                for (Location loc : compostIndex.entries(chunk)) tickCompost(loc.getBlock());
                if ((chunk.getX() + chunk.getZ() + (int) (world.getTime() / 10)) % 4 == 0) {
                    // stagger crop growth across chunks (each chunk processed ~2x/s effective 1x/s)
                    for (Location loc : cropIndex.entries(chunk)) tickCrop(loc.getBlock());
                    for (Location loc : soilIndex.entries(chunk)) tickSoil(loc.getBlock());
                }
            }
        }
    }

    /* ===================== cooking pot ===================== */

    private void tickPot(BukkitFurniture pot) {
        Block block = pot.location().getBlock();
        boolean heated = isHeated(block.getLocation());
        ItemStack[] inv = inv(pot);
        PersistentDataContainer pdc = data(pot);

        boolean hasInput = false;
        for (int i = 0; i < SLOT_INPUTS; i++) {
            if (inv[i] != null && !inv[i].getType().isAir()) {
                hasInput = true;
                break;
            }
        }

        FDRecipes.CookingRecipe recipe = null;
        ItemStack[] inputs = java.util.Arrays.copyOfRange(inv, 0, SLOT_INPUTS);
        if (hasInput) {
            recipe = plugin.recipes().matchCooking(inputs);
        }

        int cook = pdc.get(key("cook"), PersistentDataType.INTEGER) == null ? 0
                : pdc.get(key("cook"), PersistentDataType.INTEGER);

        if (heated && recipe != null && canCookInto(inv)) {
            int total = recipe.cookTime();
            cook += 10;
            pdc.set(key("cook"), PersistentDataType.INTEGER, cook);
            pdc.set(key("cooktotal"), PersistentDataType.INTEGER, total);
            if (cook % 60 == 0) {
                block.getWorld().playSound(block.getLocation(), FD.SND_BOIL_SOUP,
                        SoundCategory.BLOCKS, 0.6f, 1.0f);
                block.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        block.getLocation().add(0.5, 0.7, 0.5), 4, 0.2, 0.05, 0.2, 0.0);
                block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                        block.getLocation().add(0.5, 0.8, 0.5), 1, 0.05, 0.05, 0.05, 0.005);
            }
            if (cook >= total) {
                // craft the meal
                pdc.set(key("cook"), PersistentDataType.INTEGER, 0);
                ItemStack result = CraftEngineHook.buildItem(Key.of(recipe.result()));
                if (result != null) {
                    result.setAmount(recipe.resultCount());
                    if (inv[SLOT_MEAL] == null || inv[SLOT_MEAL].getType().isAir()) {
                        inv[SLOT_MEAL] = result;
                    } else if (inv[SLOT_MEAL].isSimilar(result)
                            && inv[SLOT_MEAL].getAmount() + result.getAmount() <= result.getMaxStackSize()) {
                        inv[SLOT_MEAL].setAmount(inv[SLOT_MEAL].getAmount() + result.getAmount());
                    }
                }
                pdc.set(key("container"), PersistentDataType.STRING,
                        recipe.container() != null ? recipe.container() : "minecraft:bowl");
                addExperience(pdc, recipe, 1);
                for (int i = 0; i < SLOT_INPUTS; i++) {
                    if (inv[i] != null && !inv[i].getType().isAir()) {
                        inv[i].setAmount(inv[i].getAmount() - 1);
                        if (inv[i].getAmount() <= 0) inv[i] = null;
                    }
                }
                saveInv(pot, inv);
            }
        } else if (cook > 0) {
            cook = Math.max(0, cook - 20);
            pdc.set(key("cook"), PersistentDataType.INTEGER, cook);
        }

        // move prepared meal to output when no container needed, or fill containers
        inv = inv(pot);
        ItemStack meal = inv[SLOT_MEAL];
        if (meal != null && !meal.getType().isAir()) {
            String containerId = pdc.get(key("container"), PersistentDataType.STRING);
            boolean needsContainer = containerId != null && !containerId.equals("minecraft:air");
            ItemStack containerStack = inv[SLOT_CONTAINER];
            if (!needsContainer) {
                if (inv[SLOT_OUTPUT] == null || inv[SLOT_OUTPUT].getType().isAir()) {
                    inv[SLOT_OUTPUT] = meal;
                    inv[SLOT_MEAL] = null;
                    saveInv(pot, inv);
                }
            } else if (containerStack != null && !containerStack.getType().isAir()
                    && idOf(containerStack).equals(containerId)
                    && (inv[SLOT_OUTPUT] == null || inv[SLOT_OUTPUT].getType().isAir())) {
                ItemStack out = meal.clone();
                out.setAmount(1);
                inv[SLOT_OUTPUT] = out;
                meal.setAmount(meal.getAmount() - 1);
                if (meal.getAmount() <= 0) inv[SLOT_MEAL] = null;
                containerStack.setAmount(containerStack.getAmount() - 1);
                if (containerStack.getAmount() <= 0) inv[SLOT_CONTAINER] = null;
                saveInv(pot, inv);
            }
        }
    }

    private boolean canCookInto(ItemStack[] inv) {
        ItemStack meal = inv[SLOT_MEAL];
        return meal == null || meal.getType().isAir();
    }

    private void addExperience(PersistentDataContainer pdc, FDRecipes.CookingRecipe recipe, int times) {
        String exp = pdc.get(key("exp"), PersistentDataType.STRING);
        Map<String, Integer> map = new HashMap<>();
        if (exp != null) {
            for (String part : exp.split(";")) {
                String[] kv = part.split(":");
                if (kv.length == 2) map.put(kv[0], Integer.parseInt(kv[1]));
            }
        }
        map.merge(recipe.id(), times, Integer::sum);
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(';');
            sb.append(k).append(':').append(v);
        });
        pdc.set(key("exp"), PersistentDataType.STRING, sb.toString());
    }

    /* ===================== skillet ===================== */

    private void tickSkillet(BukkitFurniture skillet) {
        Block block = skillet.location().getBlock();
        PersistentDataContainer pdc = data(skillet);
        ItemStack item = skilletItem(skillet);
        if (item == null) return;
        if (!isHeated(block.getLocation())) return;
        int cook = pdc.get(key("cook"), PersistentDataType.INTEGER) == null ? 0
                : pdc.get(key("cook"), PersistentDataType.INTEGER);
        cook += 10;
        pdc.set(key("cook"), PersistentDataType.INTEGER, cook);
        int total = pdc.get(key("cooktotal"), PersistentDataType.INTEGER) == null ? 100
                : pdc.get(key("cooktotal"), PersistentDataType.INTEGER);
        if (cook % 40 == 0) {
            block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    block.getLocation().add(0.5, 0.5, 0.5), 1, 0.05, 0.05, 0.05, 0.005);
        }
        if (cook >= total) {
            pdc.set(key("cook"), PersistentDataType.INTEGER, 0);
            String resultId = pdc.get(key("result"), PersistentDataType.STRING);
            setSkilletItem(skillet, null);
            pdc.remove(key("result"));
            if (resultId != null) {
                ItemStack result = CraftEngineHook.buildItem(Key.of(resultId));
                if (result != null) {
                    ejectToward(block, result, 0.25);
                }
            }
            block.getWorld().playSound(block.getLocation(), FD.SND_SKILLET_ADD_FOOD,
                    SoundCategory.BLOCKS, 0.7f, 1.0f);
        }
    }

    public ItemStack skilletItem(BukkitFurniture skillet) {
        UUID uuid = entityChild(skillet, "itemEntity");
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof org.bukkit.entity.ItemDisplay display) {
                return display.getItemStack();
            }
        }
        return null;
    }

    public void setSkilletItem(BukkitFurniture skillet, ItemStack item) {
        setDisplayChild(skillet, "itemEntity", item, 0.5, 0.45, 0.5, 0.35f);
    }

    /* ===================== stove ===================== */

    public void tickStove(Block stove) {
        var state = CraftEngineHook.customBlockState(stove);
        if (state == null || !state.owner().value().id().equals(FD.STOVE)) {
            stoveIndex.remove(stove);
            return;
        }
        Boolean lit = getBool(state, "lit");
        boolean isLit = lit != null && lit;
        ItemStack[] items = plugin.blockStore().getItems(stove, "grill");
        int[] times = times(plugin.blockStore().getString(stove, "grill_times"));
        int[] totals = times(plugin.blockStore().getString(stove, "grill_totals"));
        boolean dirty = false;
        boolean doneSomething = false;
        for (int i = 0; i < 6; i++) {
            ItemStack it = items == null ? null : items[i];
            if (it == null || it.getType().isAir()) continue;
            if (!isLit) {
                times[i] = Math.max(0, times[i] - 20);
                continue;
            }
            if (times[i] < totals[i]) {
                times[i] += 10;
                doneSomething = true;
            }
            if (times[i] >= totals[i] && totals[i] > 0) {
                ItemStack cooked = campfireResult(it);
                if (cooked != null) {
                    items[i] = null;
                    dirty = true;
                    ejectToward(stove, cooked, 0.35);
                }
            }
        }
        if (doneSomething && Math.random() < 0.3) {
            stove.getWorld().spawnParticle(Particle.SMALL_FLAME,
                    stove.getLocation().add(0.5, 0.6, 0.5), 2, 0.2, 0.05, 0.2, 0.002);
            stove.getWorld().playSound(stove.getLocation(), FD.SND_STOVE_CRACKLE,
                    SoundCategory.BLOCKS, 0.4f, 1.0f);
        }
        if (dirty) {
            plugin.blockStore().setItems(stove, "grill", items);
            refreshStoveDisplays(stove, items);
        }
        plugin.blockStore().setString(stove, "grill_times", joinTimes(times));
        plugin.blockStore().setString(stove, "grill_totals", joinTimes(totals));
    }

    private int[] times(String joined) {
        int[] out = new int[6];
        if (joined == null) return out;
        String[] parts = joined.split(",");
        for (int i = 0; i < 6 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private String joinTimes(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    public void refreshStoveDisplays(Block stove, ItemStack[] items) {
        // six grill offsets mirroring the mod's 2x3 layout
        double[][] offsets = {{0.2, 0.2}, {0.5, 0.2}, {0.8, 0.2}, {0.2, 0.7}, {0.5, 0.7}, {0.8, 0.7}};
        for (int i = 0; i < 6; i++) {
            ItemStack it = items == null ? null : items[i];
            String field = "grill" + i;
            UUID existing = plugin.blockStore().getString(stove, field) == null ? null
                    : UUID.fromString(plugin.blockStore().getString(stove, field));
            if (it == null || it.getType().isAir()) {
                if (existing != null) {
                    Entity e = Bukkit.getEntity(existing);
                    if (e != null) e.remove();
                    plugin.blockStore().clear(stove, field);
                }
                continue;
            }
            if (existing != null && Bukkit.getEntity(existing) instanceof org.bukkit.entity.ItemDisplay d) {
                d.setItemStack(it);
                continue;
            }
            org.bukkit.entity.ItemDisplay display = stove.getWorld().spawn(
                    stove.getLocation().add(offsets[i][0], 1.0, offsets[i][1]),
                    org.bukkit.entity.ItemDisplay.class, dd -> {
                        dd.setItemStack(it);
                        dd.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.HEAD);
                        var t = new org.joml.Quaternionf()
                                .rotationYXZ((float) Math.toRadians(90), 0, 0);
                        dd.setTransformation(new org.bukkit.util.Transformation(
                                new org.joml.Vector3f(), t, new org.joml.Vector3f(0.375f, 0.375f, 0.375f),
                                new org.joml.Quaternionf()));
                    });
            plugin.blockStore().setString(stove, field, display.getUniqueId().toString());
        }
    }

    /* ===================== basket ===================== */

    private static final int BASKET_COOLDOWN = 8;

    private void tickBasket(Block basket) {
        var state = CraftEngineHook.customBlockState(basket);
        if (state == null || !state.owner().value().id().equals(FD.BASKET)) {
            basketIndex.remove(basket);
            return;
        }
        Boolean enabled = getBool(state, "enabled");
        if (enabled != null && !enabled) return;
        org.bukkit.inventory.Inventory inv = ceStorageInventory(basket);
        if (inv == null) return;

        String facing = plugin.blockStore().getString(basket, "facing");
        Vector dir = dirOf(facing);
        Location center = basket.getLocation().add(0.5, 0.5, 0.5).add(dir.clone().multiply(0.75));
        for (Entity entity : basket.getWorld().getNearbyEntities(center, 0.5, 0.5, 0.5)) {
            if (!(entity instanceof Item item)) continue;
            ItemStack stack = item.getItemStack();
            java.util.HashMap<Integer, ItemStack> left = inv.addItem(stack);
            if (left.isEmpty()) {
                item.remove();
            } else {
                item.setItemStack(left.values().iterator().next());
            }
        }
    }

    /** Access the CE simple_storage_block container inventory at a block position. */
    public org.bukkit.inventory.Inventory ceStorageInventory(Block block) {
        try {
            var bukkitWorld = net.momirealms.craftengine.bukkit.api.BukkitAdaptor.adapt(block.getWorld());
            var ceWorld = bukkitWorld.storageWorld();
            var blockPos = new net.momirealms.craftengine.core.world.BlockPos(block.getX(), block.getY(), block.getZ());
            var blockEntity = ceWorld.getBlockEntityAtIfLoaded(blockPos);
            if (blockEntity == null || blockEntity.controller == null) return null;
            if (blockEntity.controller instanceof net.momirealms.craftengine.bukkit.block.entity.SimpleStorageBlockEntityController controller) {
                return controller.inventory();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("ceStorageInventory failed: " + t.getMessage());
        }
        return null;
    }
    private boolean isFull(ItemStack[] inv) {
        for (ItemStack s : inv) {
            if (s == null || s.getType().isAir() || s.getAmount() < s.getMaxStackSize()) return false;
        }
        return true;
    }

    private int addToInventory(ItemStack[] inv, ItemStack stack) {
        int moved = 0;
        for (int i = 0; i < inv.length && stack.getAmount() > 0; i++) {
            ItemStack s = inv[i];
            if (s == null || s.getType().isAir()) {
                int put = Math.min(stack.getAmount(), stack.getMaxStackSize());
                ItemStack copy = stack.clone();
                copy.setAmount(put);
                inv[i] = copy;
                stack.setAmount(stack.getAmount() - put);
                moved += put;
            } else if (s.isSimilar(stack) && s.getAmount() < s.getMaxStackSize()) {
                int put = Math.min(stack.getAmount(), s.getMaxStackSize() - s.getAmount());
                s.setAmount(s.getAmount() + put);
                stack.setAmount(stack.getAmount() - put);
                moved += put;
            }
        }
        return moved;
    }

    /* ===================== compost / crops / soil ===================== */

    private void tickCompost(Block compost) {
        var state = CraftEngineHook.customBlockState(compost);
        if (state == null) {
            compostIndex.remove(compost);
            return;
        }
        Integer level = getInt(state, "composting");
        if (level == null) return;
        if (level < 7) {
            // accumulate slowly when activators/water/light nearby (mirrors mod logic)
            double chance = 0.0125;
            boolean water = false;
            for (Block b : new Block[]{compost.getRelative(0, 0, 1), compost.getRelative(0, 0, -1),
                    compost.getRelative(1, 0, 0), compost.getRelative(-1, 0, 0)}) {
                if (FD.COMPOST_ACTIVATORS.contains(b.getType())) chance += 0.0125;
                if (b.getType() == Material.WATER) water = true;
            }
            if (water) chance += 0.0125;
            if (compost.getLightFromSky() > 10) chance += 0.00625;
            if (Math.random() < chance * 10) {
                setBlockProperty(compost, "composting", level + 1);
            }
        } else if (Math.random() < 0.02) {
            // fully composted -> rich soil
            CraftEngineHook.removeBlock(compost, false);
            CraftEngineHook.placeBlock(compost.getLocation(), FD.RICH_SOIL, false);
            compostIndex.remove(compost);
            soilIndex.add(compost);
        }
    }

    private void tickCrop(Block crop) {
        // growth handled by CropManager (kept here for central scheduling)
        plugin.cropManager().growthTick(crop);
    }

    private void tickSoil(Block soil) {
        plugin.cropManager().soilTick(soil);
    }

    /* ===================== helpers ===================== */

    public boolean isHeated(Location loc) {
        Block below = loc.getBlock().getRelative(0, -1, 0);
        if (FD.HEAT_SOURCES.contains(below.getType())) {
            if (below.getType() == Material.CAMPFIRE || below.getType() == Material.SOUL_CAMPFIRE
                    || below.getType() == Material.FURNACE || below.getType() == Material.BLAST_FURNACE) {
                org.bukkit.block.data.type.Campfire cf = null;
                if (below.getBlockData() instanceof org.bukkit.block.data.type.Campfire c) {
                    return c.isLit();
                }
                if (below.getBlockData() instanceof org.bukkit.block.data.Lightable l) {
                    return l.isLit();
                }
                return true;
            }
            return true;
        }
        var state = CraftEngineHook.customBlockState(below);
        if (state != null && state.owner().value().id().equals(FD.STOVE)) {
            Boolean lit = getBool(state, "lit");
            return lit != null && lit;
        }
        if (FD.HEAT_CONDUCTORS.contains(below.getType())) {
            Block further = below.getRelative(0, -1, 0);
            if (FD.HEAT_SOURCES.contains(further.getType())) return true;
            var st = CraftEngineHook.customBlockState(further);
            if (st != null && st.owner().value().id().equals(FD.STOVE)) {
                Boolean lit = getBool(st, "lit");
                return lit != null && lit;
            }
        }
        return false;
    }

    public ItemStack campfireResult(ItemStack input) {
        // stove/skillet only cook campfire recipes (mod parity)
        ItemStack fallback = null;
        var it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            var recipe = it.next();
            if (recipe instanceof org.bukkit.inventory.CampfireRecipe campfire) {
                if (campfire.getInputChoice().test(input)) {
                    return campfire.getResult().clone();
                }
            } else if (fallback == null
                    && recipe instanceof org.bukkit.inventory.SmokingRecipe smoking) {
                if (smoking.getInputChoice().test(input)) {
                    fallback = smoking.getResult().clone();
                }
            }
        }
        return fallback;
    }

    public int campfireTime(ItemStack input) {
        var it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            var recipe = it.next();
            if (recipe instanceof org.bukkit.inventory.CampfireRecipe campfire) {
                if (campfire.getInputChoice().test(input)) {
                    return campfire.getCookingTime();
                }
            }
        }
        return 600;
    }

    public void ejectToward(Block block, ItemStack item, double speed) {
        Location loc = block.getLocation().add(0.5, 0.8, 0.5);
        Item dropped = block.getWorld().dropItem(loc, item);
        Vector dir = dirOf(plugin.blockStore().getString(block, "facing"));
        if (dir == null) dir = dirOf("east");
        dropped.setVelocity(dir.multiply(speed).setY(0.25));
    }

    public Vector dirOf(String facing) {
        if (facing == null) return new Vector(1, 0, 0);
        return switch (facing) {
            case "north" -> new Vector(0, 0, -1);
            case "south" -> new Vector(0, 0, 1);
            case "west" -> new Vector(-1, 0, 0);
            case "up" -> new Vector(0, 1, 0);
            case "down" -> new Vector(0, -1, 0);
            default -> new Vector(1, 0, 0);
        };
    }

    public UUID entityChild(BukkitFurniture furniture, String field) {
        String s = data(furniture).get(key(field), PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    public void setDisplayChild(BukkitFurniture furniture, String field, ItemStack item,
                                double x, double y, double z, float scale) {
        UUID existing = entityChild(furniture, field);
        if (existing != null) {
            Entity e = Bukkit.getEntity(existing);
            if (e != null) e.remove();
        }
        if (item == null || item.getType().isAir()) {
            data(furniture).remove(key(field));
            return;
        }
        Location loc = furniture.location().clone().add(x - 0.5, y, z - 0.5);
        org.bukkit.entity.ItemDisplay display = loc.getWorld().spawn(loc,
                org.bukkit.entity.ItemDisplay.class, dd -> {
                    dd.setItemStack(item);
                    dd.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                    dd.setTransformation(new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(), new org.joml.Quaternionf(),
                            new org.joml.Vector3f(scale, scale, scale), new org.joml.Quaternionf()));
                });
        data(furniture).set(key(field), PersistentDataType.STRING, display.getUniqueId().toString());
    }

    /* property helpers (typed via CE property API) */

    public Boolean getBool(Block block, String name) {
        var state = CraftEngineHook.customBlockState(block);
        if (state == null) return null;
        var prop = state.<Boolean>getProperty(name);
        return prop == null ? null : state.getNullable(prop);
    }

    public Integer getInt(Block block, String name) {
        var state = CraftEngineHook.customBlockState(block);
        if (state == null) return null;
        var prop = state.<Integer>getProperty(name);
        return prop == null ? null : state.getNullable(prop);
    }

    public Boolean getBool(net.momirealms.craftengine.core.block.ImmutableBlockState state, String name) {
        var prop = state.<Boolean>getProperty(name);
        return prop == null ? null : state.getNullable(prop);
    }

    public Integer getInt(net.momirealms.craftengine.core.block.ImmutableBlockState state, String name) {
        var prop = state.<Integer>getProperty(name);
        return prop == null ? null : state.getNullable(prop);
    }

    public void setBlockProperty(Block block, String property, Object value) {
        var state = CraftEngineHook.customBlockState(block);
        if (state == null) return;
        var prop = state.getProperty(property);
        if (prop == null) return;
        var next = net.momirealms.craftengine.core.block.ImmutableBlockState.with(state, prop, value);
        CraftEngineHook.placeState(block.getLocation(), next, false);
    }

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        Key custom = CraftEngineHook.customItemId(stack);
        return custom != null ? custom.toString() : stack.getType().key().toString();
    }
}
