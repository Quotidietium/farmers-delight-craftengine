package com.nhoryzon.mc.farmersdelight.papo.logic;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Crop growth, rich soil boosting and harvest rules, mirroring the mod's
 * CropBlock / BuddingBushBlock / RiceCropBlock behaviour.
 */
public final class CropManager {

    private final FarmersDelightPlugin plugin;

    public CropManager(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
    }

    private GameTicker ticker() {
        return plugin.gameTicker();
    }

    /* ===================== growth ===================== */

    public void growthTick(Block crop) {
        ImmutableBlockState state = CraftEngineHook.customBlockState(crop);
        if (state == null) {
            ticker().cropIndex.remove(crop);
            return;
        }
        Key id = state.owner().value().id();
        Integer age = ticker().getInt(state, "age");
        if (age == null) return;

        // random-tick equivalent probability (about 3% per call)
        if (ThreadLocalRandom.current().nextDouble() >= 0.03) return;

        switch (id.toString()) {
            case "farmersdelight:cabbages", "farmersdelight:onions" -> {
                if (!canGrow(crop)) return;
                int max = 7;
                if (age < max && ThreadLocalRandom.current().nextDouble() < 0.35) {
                    ticker().setBlockProperty(crop, "age", age + 1);
                }
            }
            case "farmersdelight:budding_tomatoes" -> {
                if (age < 4 && ThreadLocalRandom.current().nextDouble() < 0.3) {
                    ticker().setBlockProperty(crop, "age", age + 1);
                } else if (age >= 4) {
                    // transform into a tomato vine
                    ticker().cropIndex.remove(crop);
                    CraftEngineHook.removeBlock(crop, false);
                    CraftEngineHook.placeBlock(crop.getLocation(), FD.TOMATO_CROP, false);
                    Block vine = crop;
                    if (ticker().cropIndex.contains(vine)) return;
                    ticker().cropIndex.add(vine);
                }
            }
            case "farmersdelight:tomatoes" -> {
                if (age < 7 && ThreadLocalRandom.current().nextDouble() < 0.3) {
                    ticker().setBlockProperty(crop, "age", age + 1);
                }
                // rope climb: if a rope sits above, sometimes grow a vine on top of it
                if (age >= 4 && ThreadLocalRandom.current().nextDouble() < 0.3) {
                    Block above = crop.getRelative(BlockFace.UP);
                    var ropeEntry = plugin.furnitureTracker().at(above.getLocation().add(0.5, 0, 0.5), FD.ROPE);
                    if (ropeEntry != null && CraftEngineHook.customBlockState(above) == null) {
                        CraftEngineHook.placeBlock(above.getLocation(), FD.TOMATO_CROP, false);
                        ticker().cropIndex.add(above);
                        Block vineAbove = above;
                        var st = CraftEngineHook.customBlockState(vineAbove);
                        if (st != null) {
                            ticker().setBlockProperty(crop, "ropelogged", true);
                        }
                    }
                }
            }
            case "farmersdelight:rice" -> {
                // rice grows slower; at max age spawns the panicle above
                Block above = crop.getRelative(BlockFace.UP);
                boolean panicleAbove = isBlock(above, FD.RICE_PANICLE);
                if (age < 3 && !panicleAbove
                        && ThreadLocalRandom.current().nextDouble() < 0.25) {
                    ticker().setBlockProperty(crop, "age", age + 1);
                } else if (age >= 3 && !panicleAbove
                        && CraftEngineHook.customBlockState(above) == null
                        && above.getType() == Material.AIR) {
                    CraftEngineHook.placeBlock(above.getLocation(), FD.RICE_PANICLE, false);
                    ticker().cropIndex.add(above);
                    ticker().setBlockProperty(crop, "supporting", true);
                }
            }
            case "farmersdelight:rice_panicle" -> {
                // panicle grows at 1/3 speed
                if (age < 3 && ThreadLocalRandom.current().nextDouble() < 0.25 / 3) {
                    ticker().setBlockProperty(crop, "age", age + 1);
                }
            }
            default -> {
            }
        }
    }

    private boolean canGrow(Block crop) {
        if (crop.getLightLevel() < 9) return false;
        Block below = crop.getRelative(BlockFace.DOWN);
        return isBlock(below, FD.RICH_SOIL_FARMLAND)
                || isBlock(below, Key.minecraft("farmland"))
                || below.getType() == Material.FARMLAND;
    }

    public boolean isBlock(Block block, Key id) {
        var state = CraftEngineHook.customBlockState(block);
        return state != null && state.owner().value().id().equals(id);
    }

    /* ===================== rich soil boost ===================== */

    public void soilTick(Block soil) {
        boolean farmland = isBlock(soil, FD.RICH_SOIL_FARMLAND);
        if (!farmland && !isBlock(soil, FD.RICH_SOIL)) {
            ticker().soilIndex.remove(soil);
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 0.2) return;
        Block above = soil.getRelative(BlockFace.UP);
        var state = CraftEngineHook.customBlockState(above);
        if (state == null) return;
        Key id = state.owner().value().id();

        // mushrooms become colonies on rich soil
        if (above.getType() == Material.BROWN_MUSHROOM) {
            CraftEngineHook.removeBlock(above, false);
            CraftEngineHook.placeBlock(above.getLocation(), Key.of(FD.MOD_ID, "brown_mushroom_colony"), false);
            ticker().cropIndex.add(above);
            boostParticles(above);
            return;
        }
        if (above.getType() == Material.RED_MUSHROOM) {
            CraftEngineHook.removeBlock(above, false);
            CraftEngineHook.placeBlock(above.getLocation(), Key.of(FD.MOD_ID, "red_mushroom_colony"), false);
            ticker().cropIndex.add(above);
            boostParticles(above);
            return;
        }
        if (id.toString().equals("farmersdelight:brown_mushroom_colony")
                || id.toString().equals("farmersdelight:red_mushroom_colony")) {
            Integer age = ticker().getInt(state, "age");
            if (age != null && age < 3) {
                ticker().setBlockProperty(above, "age", age + 1);
                boostParticles(above);
            }
            return;
        }
        Integer age = ticker().getInt(state, "age");
        if (age == null) return;
        int max = maxAge(id);
        if (age < max) {
            ticker().setBlockProperty(above, "age", age + 1);
            boostParticles(above);
        }
    }

    private void boostParticles(Block block) {
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                block.getLocation().add(0.5, 0.3, 0.5), 4, 0.25, 0.1, 0.25, 0.0);
    }

    public int maxAge(Key cropId) {
        return switch (cropId.toString()) {
            case "farmersdelight:budding_tomatoes" -> 4;
            case "farmersdelight:rice", "farmersdelight:rice_panicle" -> 3;
            case "farmersdelight:brown_mushroom_colony", "farmersdelight:red_mushroom_colony" -> 3;
            default -> 7;
        };
    }

    /* ===================== player interactions ===================== */

    /** @return true if the interaction was handled (harvest performed). */
    public boolean tryHarvest(Block crop) {
        return tryHarvest(crop, null);
    }

    public boolean tryHarvest(Block crop, org.bukkit.entity.Player player) {
        return tryHarvest(crop, player, null);
    }

    public boolean tryHarvest(Block crop, org.bukkit.entity.Player player, org.bukkit.inventory.ItemStack held) {
        var state = CraftEngineHook.customBlockState(crop);
        if (state == null) return false;
        Key id = state.owner().value().id();
        Integer age = ticker().getInt(state, "age");
        if (age == null) return false;
        Location loc = crop.getLocation().add(0.5, 0.4, 0.5);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        switch (id.toString()) {
            case "farmersdelight:cabbages" -> {
                if (age >= 7) {
                    drop(crop, FD.CABBAGE, 1);
                    drop(crop, FD.CABBAGE_SEEDS, rand.nextInt(1, 3));
                    ticker().setBlockProperty(crop, "age", 2);
                    return true;
                }
            }
            case "farmersdelight:onions" -> {
                if (age >= 7) {
                    drop(crop, FD.ONION, rand.nextInt(1, 3));
                    ticker().setBlockProperty(crop, "age", 0);
                    return true;
                }
            }
            case "farmersdelight:tomatoes" -> {
                if (age >= 7) {
                    drop(crop, FD.TOMATO, rand.nextInt(1, 3));
                    if (rand.nextDouble() < 0.05) drop(crop, FD.ROTTEN_TOMATO, 1);
                    ticker().setBlockProperty(crop, "age", 5);
                    if (player != null && ticker().getBool(crop, "ropelogged") != null
                            && java.lang.Boolean.TRUE.equals(ticker().getBool(crop, "ropelogged"))) {
                        plugin.advancements().onHarvestRopeloggedTomato(player);
                    } else if (player != null) {
                        plugin.advancements().onHarvestRopeloggedTomato(player);
                    }
                    return true;
                }
            }
            case "farmersdelight:rice_panicle" -> {
                if (age >= 3) {
                    drop(crop, FD.RICE_PANICLE_ITEM, 1);
                    drop(crop, FD.RICE_SEEDS, rand.nextInt(1, 4));
                    // remove panicle, lower crop keeps supporting visual off
                    ticker().cropIndex.remove(crop);
                    CraftEngineHook.removeBlock(crop, false);
                    Block below = crop.getRelative(BlockFace.DOWN);
                    if (isBlock(below, FD.RICE_CROP)) {
                        ticker().setBlockProperty(below, "supporting", false);
                    }
                    return true;
                }
            }
            case "farmersdelight:brown_mushroom_colony" -> {
                if (age > 0 && held != null && held.getType() == org.bukkit.Material.SHEARS) {
                    drop(crop, Key.minecraft("brown_mushroom"), 1);
                    ticker().setBlockProperty(crop, "age", age - 1);
                    crop.getWorld().playSound(loc, "minecraft:entity.mooshroom.shear",
                            SoundCategory.BLOCKS, 1.0f, 1.0f);
                    return true;
                }
            }
            case "farmersdelight:red_mushroom_colony" -> {
                if (age > 0 && held != null && held.getType() == org.bukkit.Material.SHEARS) {
                    drop(crop, Key.minecraft("red_mushroom"), 1);
                    ticker().setBlockProperty(crop, "age", age - 1);
                    crop.getWorld().playSound(loc, "minecraft:entity.mooshroom.shear",
                            SoundCategory.BLOCKS, 1.0f, 1.0f);
                    return true;
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    /** Bonemeal support; returns true when the crop consumed the meal. */
    public boolean tryBonemeal(Block crop) {
        var state = CraftEngineHook.customBlockState(crop);
        if (state == null) return false;
        Key id = state.owner().value().id();
        Integer age = ticker().getInt(state, "age");
        if (age == null) return false;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        switch (id.toString()) {
            case "farmersdelight:cabbages", "farmersdelight:onions", "farmersdelight:tomatoes" -> {
                if (age < 7) {
                    ticker().setBlockProperty(crop, "age", Math.min(7, age + rand.nextInt(2, 4)));
                    boostParticles(crop);
                    return true;
                }
            }
            case "farmersdelight:budding_tomatoes" -> {
                if (age < 4) {
                    ticker().setBlockProperty(crop, "age", Math.min(4, age + rand.nextInt(1, 3)));
                    boostParticles(crop);
                    return true;
                }
            }
            case "farmersdelight:rice" -> {
                if (age < 3) {
                    ticker().setBlockProperty(crop, "age", 3);
                    boostParticles(crop);
                    return true;
                }
                Block above = crop.getRelative(BlockFace.UP);
                if (isBlock(above, FD.RICE_PANICLE)) {
                    var ps = CraftEngineHook.customBlockState(above);
                    Integer pa = ps == null ? null : ticker().getInt(ps, "age");
                    if (pa != null && pa < 3) {
                        ticker().setBlockProperty(above, "age", 3);
                        boostParticles(crop);
                        return true;
                    }
                }
            }
            case "farmersdelight:sandy_shrub" -> {
                // sandy shrub bonemeal spreads a small patch
                for (int i = 0; i < 3; i++) {
                    Block target = crop.getRelative(rand.nextInt(-2, 3), 0, rand.nextInt(-2, 3));
                    if (target.getType() == Material.AIR
                            && target.getRelative(BlockFace.DOWN).getType() == Material.SAND) {
                        CraftEngineHook.placeBlock(target.getLocation(),
                                Key.of(FD.MOD_ID, "sandy_shrub"), false);
                    }
                }
                boostParticles(crop);
                return true;
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    /** Crop break drops (called from the block break listener). */
    public void breakDrops(Block crop) {
        var state = CraftEngineHook.customBlockState(crop);
        if (state == null) return;
        Key id = state.owner().value().id();
        Integer age = ticker().getInt(state, "age");
        int a = age == null ? 0 : age;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        switch (id.toString()) {
            case "farmersdelight:cabbages" -> {
                drop(crop, FD.CABBAGE_SEEDS, 1);
                if (a >= 7) drop(crop, FD.CABBAGE, 1);
            }
            case "farmersdelight:onions" -> drop(crop, FD.ONION, 1);
            case "farmersdelight:budding_tomatoes" -> drop(crop, FD.TOMATO_SEEDS, 1);
            case "farmersdelight:tomatoes" -> {
                drop(crop, FD.TOMATO_SEEDS, 1);
                if (a >= 7) drop(crop, FD.TOMATO, 1);
            }
            case "farmersdelight:rice" -> {
                drop(crop, FD.RICE_SEEDS, 1);
                // remove panicle above
                Block above = crop.getRelative(BlockFace.UP);
                if (isBlock(above, FD.RICE_PANICLE)) {
                    ticker().cropIndex.remove(above);
                    CraftEngineHook.removeBlock(above, false);
                    drop(above, FD.RICE_PANICLE_ITEM, 1);
                }
            }
            case "farmersdelight:rice_panicle" -> {
                drop(crop, FD.RICE_PANICLE_ITEM, 1);
                drop(crop, FD.RICE_SEEDS, rand.nextInt(1, 3));
                Block below = crop.getRelative(BlockFace.DOWN);
                if (isBlock(below, FD.RICE_CROP)) {
                    ticker().setBlockProperty(below, "supporting", false);
                }
            }
            case "farmersdelight:wild_cabbages" -> {
                drop(crop, FD.CABBAGE_SEEDS, rand.nextInt(1, 3));
                drop(crop, FD.CABBAGE, 1);
            }
            case "farmersdelight:wild_onions" -> {
                drop(crop, FD.ONION, rand.nextInt(1, 3));
            }
            case "farmersdelight:wild_tomatoes" -> {
                drop(crop, FD.TOMATO_SEEDS, rand.nextInt(1, 3));
                drop(crop, FD.TOMATO, 1);
            }
            case "farmersdelight:wild_carrots" -> drop(crop, Key.minecraft("carrot"), 1);
            case "farmersdelight:wild_potatoes" -> drop(crop, Key.minecraft("potato"), 1);
            case "farmersdelight:wild_beetroots" -> {
                drop(crop, Key.minecraft("beetroot_seeds"), rand.nextInt(1, 3));
                drop(crop, Key.minecraft("beetroot"), 1);
            }
            case "farmersdelight:wild_rice" -> drop(crop, FD.RICE_SEEDS, rand.nextInt(1, 3));
            case "farmersdelight:sandy_shrub" -> drop(crop, FD.STRAW, 1);
            case "farmersdelight:brown_mushroom_colony" -> {
                drop(crop, Key.minecraft("brown_mushroom"), 1 + a / 2);
                if (a >= 3) drop(crop, Key.of(FD.MOD_ID, "brown_mushroom_colony"), 1);
            }
            case "farmersdelight:red_mushroom_colony" -> {
                drop(crop, Key.minecraft("red_mushroom"), 1 + a / 2);
                if (a >= 3) drop(crop, Key.of(FD.MOD_ID, "red_mushroom_colony"), 1);
            }
            default -> {
            }
        }
    }

    private void drop(Block at, Key item, int count) {
        ItemStack stack = CraftEngineHook.buildItem(item);
        if (stack == null || count <= 0) return;
        stack.setAmount(count);
        at.getWorld().dropItemNaturally(at.getLocation().add(0.5, 0.4, 0.5), stack);
    }
}
