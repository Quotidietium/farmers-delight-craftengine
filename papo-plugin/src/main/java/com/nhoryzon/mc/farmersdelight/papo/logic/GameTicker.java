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
import java.util.concurrent.ThreadLocalRandom;

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
    public final ChunkIndex potIndex;
    public final ChunkIndex basketIndex;
    public final ChunkIndex compostIndex;
    public final ChunkIndex cropIndex;
    public final ChunkIndex soilIndex;

    public GameTicker(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
        this.stoveIndex = new ChunkIndex(plugin, "stove");
        this.potIndex = new ChunkIndex(plugin, "pot");
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

    /**
     * A/B benchmark switch (-Dfd.legacy-tick=true): restores the pre-R2 hot path
     * (no chunk-loaded early exit, no compost pre-gate, always-reread pot inventory)
     * so the benchmark can measure both variants on the same build.
     */
    static final boolean LEGACY_TICK = Boolean.getBoolean("fd.legacy-tick");

    private void tick() {
        long totalStart = System.nanoTime();
        // --- furniture based mechanics
        long segMark = System.nanoTime();
        Iterator<Map.Entry<UUID, FurnitureTracker.Entry>> it =
                plugin.furnitureTracker().tracked().entrySet().iterator();
        while (it.hasNext()) {
            FurnitureTracker.Entry entry = it.next().getValue();
            if (!entry.furniture().isValid()) {
                it.remove();
                continue;
            }
            Location loc = entry.furniture().location();
            if (!LEGACY_TICK && (!loc.isWorldLoaded()
                    || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4))) {
                continue;
            }
            if (entry.furnitureId().equals(FD.COOKING_POT)) {
                tickPot(entry.furniture());
            } else if (entry.furnitureId().equals(FD.SKILLET)) {
                tickSkillet(entry.furniture());
            }
        }
        segmentNanos[1] += System.nanoTime() - segMark;
        // --- block based mechanics
        for (World world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                segMark = System.nanoTime();
                for (Location loc : potIndex.entries(chunk)) tickPotBlock(loc.getBlock());
                for (Location loc : stoveIndex.entries(chunk)) tickStove(loc.getBlock());
                segmentNanos[2] += System.nanoTime() - segMark;

                segMark = System.nanoTime();
                for (Location loc : basketIndex.entries(chunk)) tickBasket(loc.getBlock());
                segmentNanos[3] += System.nanoTime() - segMark;

                segMark = System.nanoTime();
                for (Location loc : compostIndex.entries(chunk)) tickCompost(loc.getBlock());
                segmentNanos[4] += System.nanoTime() - segMark;

                if ((chunk.getX() + chunk.getZ() + (int) (world.getTime() / 10)) % 4 == 0) {
                    // stagger crop growth across chunks (each chunk processed ~2x/s effective 1x/s)
                    segMark = System.nanoTime();
                    for (Location loc : cropIndex.entries(chunk)) tickCrop(loc.getBlock());
                    for (Location loc : soilIndex.entries(chunk)) tickSoil(loc.getBlock());
                    segmentNanos[5] += System.nanoTime() - segMark;
                }
            }
        }
        segmentNanos[0] += System.nanoTime() - totalStart;
    }

    // zero-allocation segmented sampling (see /bench tick): 0=total 1=furniture
    // 2=stove 3=basket 4=compost 5=crop+soil, cumulative nanos since last sample
    private final long[] segmentNanos = new long[6];

    /** Returns cumulative per-segment nanos since the previous sample and resets them. */
    public long[] sampleSegments() {
        long[] snapshot = segmentNanos.clone();
        java.util.Arrays.fill(segmentNanos, 0);
        return snapshot;
    }

    /* ===================== cooking pot ===================== */

    private void tickPot(BukkitFurniture pot) {
        Block block = pot.location().getBlock();
        boolean heated = isHeated(block.getLocation());
        ItemStack[] inv = inv(pot);
        PersistentDataContainer pdc = data(pot);

        // mod SidedInventory facets: hoppers above feed ingredients, side hoppers feed the
        // container slot, hoppers below pull finished output (vanilla hopper cadence ~8t)
        if (potHoppers(block, inv)) {
            saveInv(pot, inv);
        }

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

        if (heated && recipe != null && canCookInto(inv, recipe)) {
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
                // mod getMealContainer: explicit recipe container, else the result item's own remainder
                String containerId = recipe.container();
                if (containerId == null) {
                    ItemStack remainder = result == null ? null : craftingRemainderOf(result);
                    containerId = remainder == null ? "minecraft:air" : idOf(remainder);
                }
                pdc.set(key("container"), PersistentDataType.STRING, containerId);
                addExperience(pdc, recipe, 1);
                consumeInputs(pot, block, pdc, inv);
                saveInv(pot, inv);
            }
        } else if (cook > 0) {
            cook = Math.max(0, cook - 20);
            pdc.set(key("cook"), PersistentDataType.INTEGER, cook);
        }

        // move prepared meal to output when no container needed, or fill containers;
        // re-read only when hoppers are adjacent (they mutate the CE inventory on their
        // own tick, so our array snapshot would be stale and overwrite their insert)
        if (LEGACY_TICK || adjacentHopper(block)) {
            inv = inv(pot);
        }
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

        // live progress/heat repaint for anyone watching this pot
        com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotGui.refreshIfHolding(pot);
    }

    /** mod canCook: cooking continues while the meal slot is empty or holds the same meal with stack room. */
    private boolean canCookInto(ItemStack[] inv, FDRecipes.CookingRecipe recipe) {
        ItemStack meal = inv[SLOT_MEAL];
        if (meal == null || meal.getType().isAir()) return true;
        ItemStack result = CraftEngineHook.buildItem(Key.of(recipe.result()));
        if (result == null || !meal.isSimilar(result)) return false;
        return meal.getAmount() + recipe.resultCount() <= meal.getMaxStackSize();
    }

    /** Mod item remainders (Item.settings.recipeRemainder); Bukkit has no crafting-remainder API on 1.21.11. */
    public static ItemStack craftingRemainderOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        String id = idOf(stack);
        if ("farmersdelight:milk_bottle".equals(id)) return new ItemStack(Material.GLASS_BOTTLE);
        if ("farmersdelight:tomato_sauce".equals(id)) return new ItemStack(Material.BOWL);
        return switch (stack.getType()) {
            case WATER_BUCKET, LAVA_BUCKET, MILK_BUCKET, POWDER_SNOW_BUCKET, AXOLOTL_BUCKET,
                 COD_BUCKET, PUFFERFISH_BUCKET, SALMON_BUCKET, TROPICAL_FISH_BUCKET -> new ItemStack(Material.BUCKET);
            case MUSHROOM_STEW, RABBIT_STEW, BEETROOT_SOUP, SUSPICIOUS_STEW -> new ItemStack(Material.BOWL);
            case POTION, SPLASH_POTION, LINGERING_POTION, HONEY_BOTTLE -> new ItemStack(Material.GLASS_BOTTLE);
            default -> null;
        };
    }

    /** mod processCooking: consume one of every input, ejecting crafting remainders to the pot's left side. */
    private void consumeInputs(BukkitFurniture pot, Block block, PersistentDataContainer pdc, ItemStack[] inv) {
        String facing = pdc.get(fdKey("facing"), PersistentDataType.STRING);
        Vector f = dirOf(facing == null ? "north" : facing);
        Vector left = new Vector(f.getZ(), 0, -f.getX());
        for (int i = 0; i < SLOT_INPUTS; i++) {
            ItemStack in = inv[i];
            if (in == null || in.getType().isAir()) continue;
            ItemStack remainder = craftingRemainderOf(in);
            if (remainder != null) {
                Location dropLoc = block.getLocation()
                        .add(0.5 + left.getX() * 0.25, 0.7, 0.5 + left.getZ() * 0.25);
                org.bukkit.entity.Item dropped = block.getWorld().dropItem(dropLoc, remainder);
                dropped.setVelocity(new Vector(left.getX() * 0.08, 0.25, left.getZ() * 0.08));
            }
            in.setAmount(in.getAmount() - 1);
            if (in.getAmount() <= 0) inv[i] = null;
        }
    }

    /** Cheap adjacency probe (5 material reads) used to decide a fresh inventory read. */
    private static boolean adjacentHopper(Block block) {
        return block.getRelative(org.bukkit.block.BlockFace.UP).getType() == Material.HOPPER
                || block.getRelative(org.bukkit.block.BlockFace.DOWN).getType() == Material.HOPPER
                || block.getRelative(org.bukkit.block.BlockFace.NORTH).getType() == Material.HOPPER
                || block.getRelative(org.bukkit.block.BlockFace.SOUTH).getType() == Material.HOPPER
                || block.getRelative(org.bukkit.block.BlockFace.EAST).getType() == Material.HOPPER
                || block.getRelative(org.bukkit.block.BlockFace.WEST).getType() == Material.HOPPER;
    }

    /** One hopper transfer step per pulse; returns true when the pot inventory changed. */
    private boolean potHoppers(Block block, ItemStack[] inv) {        boolean changed = false;
        // hopper above -> first stack that fits the ingredient slots
        if (block.getRelative(org.bukkit.block.BlockFace.UP).getType() == Material.HOPPER
                && block.getRelative(org.bukkit.block.BlockFace.UP).getState()
                instanceof org.bukkit.block.Hopper hopper) {
            changed |= pullOne(hopper.getInventory(), (slot, one) -> {
                for (int i = 0; i < SLOT_INPUTS; i++) {
                    ItemStack target = inv[i];
                    if (target == null || target.getType().isAir()) {
                        inv[i] = one;
                        return true;
                    }
                    if (target.isSimilar(one) && target.getAmount() < target.getMaxStackSize()) {
                        target.setAmount(target.getAmount() + 1);
                        return true;
                    }
                }
                return false;
            });
        }
        // side hoppers facing the pot -> container slot only
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            org.bukkit.block.Block side = block.getRelative(face);
            if (side.getType() != Material.HOPPER || !(side.getState() instanceof org.bukkit.block.Hopper hopper)) {
                continue;
            }
            if (!(side.getBlockData() instanceof org.bukkit.block.data.Directional directional)
                    || !side.getRelative(directional.getFacing()).equals(block)) {
                continue;
            }
            changed |= pullOne(hopper.getInventory(), (slot, one) -> {
                ItemStack target = inv[SLOT_CONTAINER];
                if (target == null || target.getType().isAir()) {
                    inv[SLOT_CONTAINER] = one;
                    return true;
                }
                if (target.isSimilar(one) && target.getAmount() < target.getMaxStackSize()) {
                    target.setAmount(target.getAmount() + 1);
                    return true;
                }
                return false;
            });
        }
        // hopper below -> extract one finished meal
        org.bukkit.block.Block below = block.getRelative(org.bukkit.block.BlockFace.DOWN);
        ItemStack output = inv[SLOT_OUTPUT];
        if (below.getType() == Material.HOPPER && below.getState() instanceof org.bukkit.block.Hopper hopper
                && output != null && !output.getType().isAir()) {
            ItemStack one = output.clone();
            one.setAmount(1);
            var leftover = hopper.getInventory().addItem(one);
            if (leftover.isEmpty()) {
                output.setAmount(output.getAmount() - 1);
                if (output.getAmount() <= 0) inv[SLOT_OUTPUT] = null;
                changed = true;
            }
        }
        return changed;
    }

    /** Take exactly one item out of a hopper inventory when the consumer accepts it. */
    private boolean pullOne(org.bukkit.inventory.Inventory hopperInv, java.util.function.BiFunction<Integer, ItemStack, Boolean> consumer) {
        for (int s = 0; s < hopperInv.getSize(); s++) {
            ItemStack src = hopperInv.getItem(s);
            if (src == null || src.getType().isAir()) continue;
            ItemStack one = src.clone();
            one.setAmount(1);
            if (!consumer.apply(s, one)) continue;
            src.setAmount(src.getAmount() - 1);
            if (src.getAmount() <= 0) hopperInv.setItem(s, null);
            return true;
        }
        return false;
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

    /** Stored per-recipe experience total (mod experienceTracker). */
    public float storedExperience(BukkitFurniture furniture) {
        String exp = data(furniture).get(key("exp"), PersistentDataType.STRING);
        if (exp == null || exp.isEmpty()) return 0;
        float total = 0;
        for (String part : exp.split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            FDRecipes.CookingRecipe recipe = plugin.recipes().cooking.stream()
                    .filter(r -> r.id().equals(kv[0])).findFirst().orElse(null);
            if (recipe != null) total += recipe.experience() * Integer.parseInt(kv[1]);
        }
        return total;
    }

    /** mod spawnStoredRecipeExperience: floor + fractional random round-up, orbs above the pot. */
    public void spawnStoredExperience(BukkitFurniture furniture, Location at) {
        PersistentDataContainer pdc = data(furniture);
        float total = storedExperience(furniture);
        pdc.set(key("exp"), PersistentDataType.STRING, "");
        if (total <= 0) return;
        int orbs = (int) Math.floor(total);
        float fraction = total - orbs;
        if (fraction != 0 && Math.random() < fraction) orbs++;
        if (orbs > 0 && at.getWorld() != null) {
            org.bukkit.entity.ExperienceOrb orb = at.getWorld()
                    .spawn(at.clone().add(0, 0.3, 0), org.bukkit.entity.ExperienceOrb.class);
            orb.setExperience(orbs);
        }
    }

    /* ===================== block cooking pot (real CE block) ===================== */

    /**
     * Pot tick for the real CE block placements (chunk-PDC storage). Mirrors the
     * furniture-pot cooking core; kept separate so the legacy furniture path for
     * pre-migration worlds stays untouched.
     */
    private void tickPotBlock(Block pot) {
        var state = CraftEngineHook.customBlockState(pot);
        if (state == null || !state.owner().value().id().equals(FD.COOKING_POT)) {
            potIndex.remove(pot);
            return;
        }
        PersistentDataContainer pdc = plugin.blockStore().chunkPdc(pot);
        ItemStack[] inv = plugin.blockStore().getItems(pot, "inv");
        if (inv == null || inv.length != SLOT_INPUTS + 3) {
            ItemStack[] padded = new ItemStack[SLOT_INPUTS + 3];
            if (inv != null) System.arraycopy(inv, 0, padded, 0, Math.min(inv.length, padded.length));
            inv = padded;
        }
        String facing = getPropString(state, "facing");
        if (facing == null) facing = "north";
        pdc.set(fdKey("facing"), PersistentDataType.STRING, facing);

        boolean changed = potHoppers(pot, inv);

        boolean hasInput = false;
        for (int i = 0; i < SLOT_INPUTS; i++) {
            if (inv[i] != null && !inv[i].getType().isAir()) {
                hasInput = true;
                break;
            }
        }
        FDRecipes.CookingRecipe recipe = hasInput
                ? plugin.recipes().matchCooking(java.util.Arrays.copyOfRange(inv, 0, SLOT_INPUTS))
                : null;
        boolean heated = isHeated(pot.getLocation());
        int cook = plugin.blockStore().getInt(pot, "cook", 0);
        int total = plugin.blockStore().getInt(pot, "cooktotal", 0);

        if (heated && recipe != null && canCookInto(inv, recipe)) {
            total = recipe.cookTime();
            cook += 10;
            if (cook % 60 == 0) {
                pot.getWorld().playSound(pot.getLocation(), FD.SND_BOIL_SOUP,
                        SoundCategory.BLOCKS, 0.6f, 1.0f);
                pot.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        pot.getLocation().add(0.5, 0.7, 0.5), 4, 0.2, 0.05, 0.2, 0.0);
            }
            if (cook >= total) {
                cook = 0;
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
                String containerId = recipe.container();
                if (containerId == null) {
                    ItemStack remainder = result == null ? null : craftingRemainderOf(result);
                    containerId = remainder == null ? "minecraft:air" : idOf(remainder);
                }
                plugin.blockStore().setString(pot, "container", containerId);
                addExpBlock(pot, recipe);
                consumeInputsBlock(pot, facing, inv);
                // UX: the mod finishes silently; a soft chime tells nearby players
                // the meal is ready without opening the GUI
                pot.getWorld().playSound(pot.getLocation(), "minecraft:entity.experience_orb.pickup",
                        SoundCategory.BLOCKS, 0.7f, 1.0f);
                changed = true;
            }
            plugin.blockStore().setInt(pot, "cook", cook);
            plugin.blockStore().setInt(pot, "cooktotal", total);
        } else if (cook > 0) {
            plugin.blockStore().setInt(pot, "cook", Math.max(0, cook - 20));
        }

        // hopper neighbors mutate the CE-side storage; re-read to avoid clobbering
        if (adjacentHopper(pot)) {
            inv = plugin.blockStore().getItems(pot, "inv");
            if (inv == null || inv.length != SLOT_INPUTS + 3) {
                ItemStack[] padded = new ItemStack[SLOT_INPUTS + 3];
                if (inv != null) System.arraycopy(inv, 0, padded, 0, Math.min(inv.length, padded.length));
                inv = padded;
            }
        }
        ItemStack meal = inv[SLOT_MEAL];
        if (meal != null && !meal.getType().isAir()) {
            String containerId = plugin.blockStore().getString(pot, "container");
            boolean needsContainer = containerId != null && !containerId.equals("minecraft:air");
            ItemStack containerStack = inv[SLOT_CONTAINER];
            if (!needsContainer) {
                if (inv[SLOT_OUTPUT] == null || inv[SLOT_OUTPUT].getType().isAir()) {
                    inv[SLOT_OUTPUT] = meal;
                    inv[SLOT_MEAL] = null;
                    changed = true;
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
                changed = true;
            }
        }
        if (changed) plugin.blockStore().setItems(pot, "inv", inv);
        com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotBlockGui.refreshIfHolding(pot);
    }

    private void addExpBlock(Block pot, FDRecipes.CookingRecipe recipe) {
        String exp = plugin.blockStore().getString(pot, "exp");
        Map<String, Integer> map = new HashMap<>();
        if (exp != null) {
            for (String part : exp.split(";")) {
                String[] kv = part.split(":");
                if (kv.length == 2) map.put(kv[0], Integer.parseInt(kv[1]));
            }
        }
        map.merge(recipe.id(), 1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(';');
            sb.append(k).append(':').append(v);
        });
        plugin.blockStore().setString(pot, "exp", sb.toString());
    }

    /** Mod processCooking: consume one of every input, eject remainders to the pot's left. */
    private void consumeInputsBlock(Block pot, String facing, ItemStack[] inv) {
        Vector f = dirOf(facing);
        Vector left = new Vector(f.getZ(), 0, -f.getX());
        for (int i = 0; i < SLOT_INPUTS; i++) {
            ItemStack in = inv[i];
            if (in == null || in.getType().isAir()) continue;
            ItemStack remainder = craftingRemainderOf(in);
            if (remainder != null) {
                Location dropLoc = pot.getLocation()
                        .add(0.5 + left.getX() * 0.25, 0.7, 0.5 + left.getZ() * 0.25);
                org.bukkit.entity.Item dropped = pot.getWorld().dropItem(dropLoc, remainder);
                dropped.setVelocity(new Vector(left.getX() * 0.08, 0.25, left.getZ() * 0.08));
            }
            in.setAmount(in.getAmount() - 1);
            if (in.getAmount() <= 0) inv[i] = null;
        }
    }

    /** Reads (and normalizes to 9 slots) the pot inventory from the chunk PDC. */
    public ItemStack[] potInv(Block pot) {
        ItemStack[] inv = plugin.blockStore().getItems(pot, "inv");
        if (inv == null || inv.length != SLOT_INPUTS + 3) {
            ItemStack[] padded = new ItemStack[SLOT_INPUTS + 3];
            if (inv != null) System.arraycopy(inv, 0, padded, 0, Math.min(inv.length, padded.length));
            inv = padded;
        }
        return inv;
    }

    /** Take-one-meal when the held item matches the required container (mod parity). */
    public ItemStack servePotBlock(Block pot, ItemStack held) {
        ItemStack[] inv = potInv(pot);
        ItemStack meal = inv[SLOT_MEAL];
        if (meal == null || meal.getType().isAir()) return null;
        String containerId = plugin.blockStore().getString(pot, "container");
        boolean needsContainer = containerId != null && !containerId.equals("minecraft:air");
        if (needsContainer) {
            if (held == null || held.getType().isAir()) return null;
            String heldId = idOf(held);
            if (heldId == null || !heldId.equals(containerId)) return null;
            held.setAmount(held.getAmount() - 1);
        }
        ItemStack portion = meal.clone();
        portion.setAmount(1);
        meal.setAmount(meal.getAmount() - 1);
        if (meal.getAmount() <= 0) inv[SLOT_MEAL] = null;
        plugin.blockStore().setItems(pot, "inv", inv);
        return portion;
    }

    /** Stored per-recipe experience for block pots. */
    public float storedExpBlock(Block pot) {
        String exp = plugin.blockStore().getString(pot, "exp");
        if (exp == null || exp.isEmpty()) return 0;
        float total = 0;
        for (String part : exp.split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            FDRecipes.CookingRecipe recipe = plugin.recipes().cooking.stream()
                    .filter(r -> r.id().equals(kv[0])).findFirst().orElse(null);
            if (recipe != null) total += recipe.experience() * Integer.parseInt(kv[1]);
        }
        return total;
    }

    public void clearExpBlock(Block pot) {
        plugin.blockStore().setString(pot, "exp", "");
    }

    /** Pays out stored block-pot experience as orbs (floor + fractional round-up). */
    public void spawnExpBlockOrbs(Block pot, Location at) {
        float total = storedExpBlock(pot);
        clearExpBlock(pot);
        if (total <= 0 || at.getWorld() == null) return;
        int orbs = (int) Math.floor(total);
        float fraction = total - orbs;
        if (fraction != 0 && Math.random() < fraction) orbs++;
        if (orbs > 0) {
            org.bukkit.entity.ExperienceOrb orb = at.getWorld()
                    .spawn(at.clone().add(0, 0.3, 0), org.bukkit.entity.ExperienceOrb.class);
            orb.setExperience(orbs);
        }
    }

    /* ===================== skillet ===================== */

    private void tickSkillet(BukkitFurniture skillet) {
        Block block = skillet.location().getBlock();
        PersistentDataContainer pdc = data(skillet);
        ItemStack stack = skilletItem(skillet);
        // mod SkilletBlockEntity.serverTick: no stored stack -> cookTime resets to 0
        if (stack == null) {
            pdc.set(key("cook"), PersistentDataType.INTEGER, 0);
            return;
        }
        int cook = pdc.get(key("cook"), PersistentDataType.INTEGER) == null ? 0
                : pdc.get(key("cook"), PersistentDataType.INTEGER);
        int total = pdc.get(key("cooktotal"), PersistentDataType.INTEGER) == null ? 100
                : pdc.get(key("cooktotal"), PersistentDataType.INTEGER);
        if (!isHeated(block.getLocation())) {
            // mod: unheated skillets cool down 2 ticks per game tick (never below 0)
            pdc.set(key("cook"), PersistentDataType.INTEGER, Math.max(0, cook - 20));
            return;
        }
        cook += 10;
        pdc.set(key("cook"), PersistentDataType.INTEGER, cook);
        // mod animationTick: 20%/tick steam puffs + fire-aspect sparks
        if (ThreadLocalRandom.current().nextDouble() < 0.9) {
            block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    block.getLocation().add(0.5, 0.1, 0.5), 1, 0.2, 0.02, 0.2, 0.005);
        }
        Integer fa = pdc.get(fdKey("fa"), PersistentDataType.INTEGER);
        if (fa != null && fa > 0 && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, fa * 0.5)) {
            block.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                    block.getLocation().add(0.5, 0.1, 0.5), 1, 0.2, 0.1, 0.2, 0.3);
        }
        if (cook < total) return;
        // mod cookAndOutputItems: craft ONE result, consume one item, keep the rest cooking
        pdc.set(key("cook"), PersistentDataType.INTEGER, 0);
        String resultId = pdc.get(key("result"), PersistentDataType.STRING);
        if (resultId != null) {
            ItemStack result = CraftEngineHook.buildItem(Key.of(resultId));
            if (result != null) {
                // eject toward FACING.rotateYClockwise (right side), speed 0.08h/0.25y
                String facing = pdc.get(fdKey("facing"), PersistentDataType.STRING);
                org.bukkit.util.Vector facingV = dirOf(facing == null ? "north" : facing);
                org.bukkit.util.Vector right = new org.bukkit.util.Vector(-facingV.getZ(), 0, facingV.getX());
                org.bukkit.entity.Item dropped = block.getWorld().dropItem(
                        block.getLocation().add(0.5, 0.3, 0.5), result);
                dropped.setVelocity(new org.bukkit.util.Vector(right.getX() * 0.08, 0.25, right.getZ() * 0.08));
            }
        }
        int remaining = stack.getAmount() - 1;
        if (remaining <= 0) {
            setSkilletItem(skillet, null);
            pdc.remove(key("result"));
        } else {
            stack.setAmount(remaining);
            setSkilletItem(skillet, stack);
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

    /** Public wrapper for diagnostics/bench: run one stove tick. */
    public void tickStovePublic(Block stove) {
        tickStove(stove);
    }

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
                    // UX: audible "food done" pop (mod ejects silently)
                    stove.getWorld().playSound(stove.getLocation(), "minecraft:entity.item.pickup",
                            SoundCategory.BLOCKS, 0.5f, 0.9f);
                }
            }
        }
        if (doneSomething && Math.random() < 0.3) {
            stove.getWorld().spawnParticle(Particle.SMALL_FLAME,
                    stove.getLocation().add(0.5, 0.6, 0.5), 2, 0.2, 0.05, 0.2, 0.002);
            stove.getWorld().playSound(stove.getLocation(), FD.SND_STOVE_CRACKLE,
                    SoundCategory.BLOCKS, 0.4f, 1.0f);
        }
        // ambient crackle while lit: the reference pack schedules it every 60-100 ticks
        // (volume 1.0, pitch 0.9-1.1); on the 10-tick pulse a 1-in-8 roll gives the same
        // mean interval, played only when the stove is actually burning
        if (isLit && Math.random() < 1.0 / 8.0) {
            stove.getWorld().playSound(stove.getLocation(), FD.SND_STOVE_CRACKLE,
                    SoundCategory.BLOCKS, 1.0f, 0.9f + (float) Math.random() * 0.2f);
        }
        if (dirty) {
            plugin.blockStore().setItems(stove, "grill", items);
            refreshStoveDisplays(stove, items);
        }
        // empty idle stove: progress stays all-zero, so the per-pulse PDC writes are
        // pure waste (reading an absent key yields the same zeros the write produced)
        boolean anyFood = false;
        for (int i = 0; i < 6; i++) {
            if (items != null && items[i] != null && !items[i].getType().isAir()) {
                anyFood = true;
                break;
            }
        }
        boolean anyProgress = false;
        for (int i = 0; i < 6; i++) {
            if (times[i] > 0 || totals[i] > 0) {
                anyProgress = true;
                break;
            }
        }
        if (LEGACY_TICK || anyFood || anyProgress || dirty) {
            plugin.blockStore().setString(stove, "grill_times", joinTimes(times));
            if (LEGACY_TICK || anyFood || anyProgress || dirty) {
                plugin.blockStore().setString(stove, "grill_totals", joinTimes(totals));
            }
        }
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
        // a completely full basket can never absorb anything, so the entity scan
        // (the expensive part) is skipped; partial stacks still allow merging
        if (!LEGACY_TICK && inv.firstEmpty() == -1 && noPartialStack(inv)) return;

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

    private static boolean noPartialStack(org.bukkit.inventory.Inventory inv) {
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.getType().isAir()) return false;
            if (s.getAmount() < s.getMaxStackSize()) return false;
        }
        return true;
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
        // pre-gate before the 27-block scan: the achievable chance is capped at
        // 0.02*27 + 0.1 + 0.1 = 0.74, so gate scale = 0.74 * (3/4096) * 10 ≈ 0.0054.
        // Mathematically equivalent (rejection when the uniform draw cannot pass the
        // real gate) and saves the whole neighbourhood scan for ~99.5% of calls.
        if (!LEGACY_TICK && Math.random() >= 0.74 * (3.0 / 4096.0) * 10) return;
        var state = CraftEngineHook.customBlockState(compost);
        if (state == null) {
            compostIndex.remove(compost);
            return;
        }
        Integer level = getInt(state, "composting");
        if (level == null) return;
        // mod OrganicCompostBlock.scheduledTick: scan the full 3x3x3 neighbourhood,
        // +0.02 per activator block, sky light >12 gives +0.1 (else +0.05), water +0.1;
        // the roll happens once per vanilla random tick (3 blocks picked from 4096 per tick)
        float chance = 0f;
        boolean water = false;
        int maxLight = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = compost.getRelative(dx, dy, dz);
                    if (FD.COMPOST_ACTIVATORS.contains(b.getType())) chance += 0.02f;
                    if (b.getType() == Material.WATER || b.getBlockData()
                            instanceof org.bukkit.block.data.Waterlogged waterlogged && waterlogged.isWaterlogged()) {
                        water = true;
                    }
                    int light = b.getRelative(org.bukkit.block.BlockFace.UP).getLightFromSky();
                    if (light > maxLight) maxLight = light;
                }
            }
        }
        chance += maxLight > 12 ? 0.1f : 0.05f;
        if (water) chance += 0.1f;
        // 10-tick pulse must emulate ~3/4096 random-tick picks per game tick
        if (Math.random() >= chance * (3.0 / 4096.0) * 10) return;
        if (level < 7) {
            setBlockProperty(compost, "composting", level + 1);
        } else {
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

    /**
     * Type-safe read of any block property as a lowercase string. CE property values
     * are typed (Direction enums for facing etc.), so blind String casts crash at
     * runtime - this accepts whatever the property carries.
     */
    public static String getPropString(net.momirealms.craftengine.core.block.ImmutableBlockState state, String name) {
        try {
            var prop = state.getProperty(name);
            if (prop == null) return null;
            Object value = state.getNullable(prop);
            return value == null ? null : String.valueOf(value).toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
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
