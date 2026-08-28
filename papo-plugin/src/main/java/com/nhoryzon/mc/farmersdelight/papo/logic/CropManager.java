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

        // vanilla random-tick cadence: 3 picks out of 4096 blocks per game tick, 10-tick pulse
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextDouble() >= (3.0 / 4096.0) * 10) return;

        switch (id.toString()) {
            case "farmersdelight:cabbages", "farmersdelight:onions" -> {
                // mod CropBlock subclasses: vanilla growth math (max age 7)
                if (age < 7 && crop.getLightLevel() >= 9 && rollVanillaGrowth(crop, id, rand)) {
                    ticker().setBlockProperty(crop, "age", age + 1);
                }
            }
            case "farmersdelight:budding_tomatoes" -> {
                // mod BuddingBushBlock: vanilla fertility math on farmland, age 0-3,
                // then growPastMaxAge turns the mature bush into a vine
                if (crop.getLightLevel() >= 9 && rollVanillaGrowth(crop, id, rand)) {
                    if (age < 3) {
                        ticker().setBlockProperty(crop, "age", age + 1);
                    } else if (age == 3) {
                        ticker().cropIndex.remove(crop);
                        CraftEngineHook.removeBlock(crop, false);
                        CraftEngineHook.placeBlock(crop.getLocation(), FD.TOMATO_CROP, false);
                        if (!ticker().cropIndex.contains(crop)) {
                            ticker().cropIndex.add(crop);
                        }
                    }
                }
            }
            case "farmersdelight:tomatoes" -> {
                // mod TomatoVineBlock (max age 3): vanilla math, then a 30% rope climb check
                if (crop.getLightLevel() >= 9) {
                    if (age < 3 && rollVanillaGrowth(crop, id, rand)) {
                        ticker().setBlockProperty(crop, "age", age + 1);
                    }
                    if (rand.nextFloat() < 0.3f) {
                        Block above = crop.getRelative(BlockFace.UP);
                        var ropeEntry = plugin.furnitureTracker().at(
                                above.getLocation().add(0.5, 0, 0.5), FD.ROPE);
                        if (ropeEntry != null && CraftEngineHook.customBlockState(above) == null) {
                            // mod: the vine replaces the rope with a ropelogged vine (max height 3)
                            int vineHeight = 1;
                            while (isBlock(crop.getRelative(BlockFace.UP, vineHeight), FD.TOMATO_CROP)) {
                                vineHeight++;
                            }
                            if (vineHeight < 3) {
                                CraftEngineHook.placeBlock(above.getLocation(), FD.TOMATO_CROP, false);
                                ticker().cropIndex.add(above);
                                ticker().setBlockProperty(crop, "ropelogged", true);
                            }
                        }
                    }
                }
            }
            case "farmersdelight:rice" -> {
                // mod RiceCropBlock (PlantBlock, own path only): light above >= 6,
                // a 1/3 gate, then the (25/10+1)=3 roll; max age spawns the panicle
                Block above = crop.getRelative(BlockFace.UP);
                boolean panicleAbove = isBlock(above, FD.RICE_PANICLE);
                if (above.getLightLevel() >= 6 && !panicleAbove && age <= 3
                        && rand.nextInt(3) == 0 && rand.nextInt(3) == 0) {
                    if (age < 3) {
                        ticker().setBlockProperty(crop, "age", age + 1);
                    } else if (CraftEngineHook.customBlockState(above) == null
                            && above.getType() == Material.AIR) {
                        CraftEngineHook.placeBlock(above.getLocation(), FD.RICE_PANICLE, false);
                        ticker().cropIndex.add(above);
                        ticker().setBlockProperty(crop, "supporting", true);
                    }
                }
            }
            case "farmersdelight:rice_panicle" -> {
                // mod RiceUpperCropBlock (CropBlock): vanilla math, no farmland below
                // the panicle so the speed stays at the f=1 baseline (threshold 26)
                if (age < 3 && crop.getLightLevel() >= 9 && rollVanillaGrowth(crop, id, rand)) {
                    ticker().setBlockProperty(crop, "age", age + 1);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Vanilla CropBlock#getGrowthSpeed + roll: f starts at 1, each of the nine cells
     * below adds farmland fertility (moisture &gt; 0 ? 3 : 1, ring cells quartered),
     * same-crop cross pair or any same-crop diagonal halves it, and the crop grows
     * one age when random &lt; 1/(floor(25/f)+1). Rich soil farmland contributes no
     * fertility (its perk is the 20% auto-bonemeal), exactly like the mod.
     */
    private boolean rollVanillaGrowth(Block crop, Key cropId, ThreadLocalRandom rand) {
        int x = crop.getX();
        int y = crop.getY();
        int z = crop.getZ();
        float f = 1.0f;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float fertility = 0.0f;
                Block ground = crop.getWorld().getBlockAt(x + i, y - 1, z + j);
                if (ground.getType() == Material.FARMLAND) {
                    fertility = ground.getBlockData() instanceof org.bukkit.block.data.type.Farmland farmland
                            && farmland.getMoisture() > 0 ? 3.0f : 1.0f;
                }
                if (i != 0 || j != 0) {
                    fertility /= 4.0f;
                }
                f += fertility;
            }
        }
        boolean eastWest = isBlock(crop.getRelative(BlockFace.WEST), cropId)
                || isBlock(crop.getRelative(BlockFace.EAST), cropId);
        boolean northSouth = isBlock(crop.getRelative(BlockFace.NORTH), cropId)
                || isBlock(crop.getRelative(BlockFace.SOUTH), cropId);
        if (eastWest && northSouth) {
            f /= 2.0f;
        } else if (isBlock(crop.getRelative(-1, 0, -1), cropId)
                || isBlock(crop.getRelative(1, 0, -1), cropId)
                || isBlock(crop.getRelative(1, 0, 1), cropId)
                || isBlock(crop.getRelative(-1, 0, 1), cropId)) {
            f /= 2.0f;
        }
        int threshold = (int) Math.floor(25.0f / f) + 1;
        return rand.nextDouble() < 1.0 / threshold;
    }

    public boolean isBlock(Block block, Key id) {
        var state = CraftEngineHook.customBlockState(block);
        return state != null && state.owner().value().id().equals(id);
    }

    /* ===================== rich soil boost ===================== */

    public void soilTick(Block soil) {
        // rich soil FARMLAND ticks through its CE behavior (moisture + boost);
        // plain rich soil boosts here at vanilla random-tick cadence (mod scheduledTick)
        if (isBlock(soil, FD.RICH_SOIL_FARMLAND)) {
            // farmland ticks through its CE behavior; drop stale index entries
            ticker().soilIndex.remove(soil);
            return;
        }
        if (!isBlock(soil, FD.RICH_SOIL)) {
            ticker().soilIndex.remove(soil);
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= (3.0 / 4096.0) * 10) return;
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
        // mod: rich soil boost = Fertilizable.grow() at 20% per random tick (bonemeal-style +2..5)
        if (ThreadLocalRandom.current().nextDouble() >= 0.2) return;
        int max = maxAge(id);
        if (age < max) {
            ticker().setBlockProperty(above, "age",
                    Math.min(max, age + ThreadLocalRandom.current().nextInt(2, 6)));
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
            // mod 1.4.3: cabbage/onion have NO right-click harvest - they are broken
            // like vanilla crops (drops handled in breakDrops from the loot tables)
            case "farmersdelight:tomatoes" -> {
                // mod TomatoVineBlock.onUse: mature vine drops 1-2 tomatoes (+5% rotten)
                // and resets to age 0, playing the bush-pick sound
                if (age >= 3) {
                    drop(crop, FD.TOMATO, 1 + rand.nextInt(2));
                    if (rand.nextDouble() < 0.05) drop(crop, FD.ROTTEN_TOMATO, 1);
                    ticker().setBlockProperty(crop, "age", 0);
                    crop.getWorld().playSound(loc, FD.SND_TOMATO_PICK,
                            SoundCategory.BLOCKS, 1.0f, 0.8f + rand.nextFloat() * 0.4f);
                    if (player != null && java.lang.Boolean.TRUE.equals(ticker().getBool(crop, "ropelogged"))) {
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
            case "farmersdelight:cabbages", "farmersdelight:onions" -> {
                // mod: vanilla CropBlock bonemeal = +2..5 ages (max 7)
                if (age < 7) {
                    ticker().setBlockProperty(crop, "age", Math.min(7, age + rand.nextInt(2, 6)));
                    boostParticles(crop);
                    return true;
                }
            }
            case "farmersdelight:tomatoes" -> {
                // mod TomatoVineBlock.getGrowthAmount = super/2 -> +1..2 ages (max 3)
                if (age < 3) {
                    ticker().setBlockProperty(crop, "age", Math.min(3, age + rand.nextInt(1, 3)));
                    boostParticles(crop);
                    return true;
                }
            }
            case "farmersdelight:budding_tomatoes" -> {
                // mod BuddingTomatoBlock.grow: +1..4, overflow turns the bush into a vine
                int grown = age + rand.nextInt(1, 5);
                if (grown <= 3) {
                    ticker().setBlockProperty(crop, "age", grown);
                    boostParticles(crop);
                    return true;
                }
                ticker().cropIndex.remove(crop);
                CraftEngineHook.removeBlock(crop, false);
                CraftEngineHook.placeBlock(crop.getLocation(), FD.TOMATO_CROP, false);
                var vineState = CraftEngineHook.customBlockState(crop);
                if (vineState != null) {
                    ticker().setBlockProperty(crop, "age", Math.min(3, grown - 4));
                }
                if (!ticker().cropIndex.contains(crop)) {
                    ticker().cropIndex.add(crop);
                }
                boostParticles(crop);
                return true;
            }
            case "farmersdelight:rice" -> {
                // mod RiceCropBlock.grow: +1..4, overflow transfers into a panicle above
                if (age < 3) {
                    ticker().setBlockProperty(crop, "age", Math.min(3, age + rand.nextInt(1, 5)));
                    boostParticles(crop);
                    return true;
                }
                Block above = crop.getRelative(BlockFace.UP);
                if (isBlock(above, FD.RICE_PANICLE)) {
                    var ps = CraftEngineHook.customBlockState(above);
                    Integer pa = ps == null ? null : ticker().getInt(ps, "age");
                    if (pa != null && pa < 3) {
                        ticker().setBlockProperty(above, "age", Math.min(3, pa + rand.nextInt(1, 5)));
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

    /** Crop break drops (called from the block break listener), mirroring the mod loot tables. */
    public void breakDrops(Block crop) {
        breakDrops(crop, null);
    }

    public void breakDrops(Block crop, ItemStack tool) {
        var state = CraftEngineHook.customBlockState(crop);
        if (state == null) return;
        Key id = state.owner().value().id();
        Integer age = ticker().getInt(state, "age");
        int a = age == null ? 0 : age;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int fortune = tool == null ? 0 : tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
        switch (id.toString()) {
            // mod loot tables: mature cabbage drops 1 cabbage + binomial(3+fortune, 4/7) seeds,
            // immature drops a single seed; onions always drop 1 plus a mature bonus roll
            case "farmersdelight:cabbages" -> {
                if (a >= 7) {
                    drop(crop, FD.CABBAGE, 1);
                    drop(crop, FD.CABBAGE_SEEDS, binomial(3 + fortune, rand));
                } else {
                    drop(crop, FD.CABBAGE_SEEDS, 1);
                }
            }
            case "farmersdelight:onions" -> {
                drop(crop, FD.ONION, 1);
                if (a >= 7) drop(crop, FD.ONION, binomial(3 + fortune, rand));
            }
            case "farmersdelight:budding_tomatoes" -> drop(crop, FD.TOMATO_SEEDS, 1);
            case "farmersdelight:tomatoes" -> {
                drop(crop, FD.TOMATO_SEEDS, 1);
                if (a >= 3) drop(crop, FD.TOMATO, 1);
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

    /**
     * Advanced vanilla crops (wheat/carrot/potato/beetroot growing on rich soil farmland)
     * drop exactly what their vanilla loot tables would (binomial_with_bonus_count with
     * extra=3, p=4/7), plus straw when harvesting mature wheat with a knife (mod inject).
     */
    public void advancedCropDrops(Block block, ImmutableBlockState state,
                                  org.bukkit.entity.Player player, ItemStack tool) {
        String id = state.owner().value().id().value();
        Integer ageValue = ticker().getInt(state, "age");
        int age = ageValue == null ? 0 : ageValue;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int fortune = tool == null ? 0 : tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
        int bonus = binomial(3 + fortune, rand);

        switch (id) {
            case "advanced_wheat" -> {
                if (age >= 7) {
                    drop(block, Key.minecraft("wheat"), 1);
                    if (bonus > 0) drop(block, Key.minecraft("wheat_seeds"), bonus);
                    if (com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes.isKnife(tool)) {
                        drop(block, FD.STRAW, 1);
                        if (player != null) plugin.advancements().onHarvestStraw(player);
                    }
                } else {
                    drop(block, Key.minecraft("wheat_seeds"), 1);
                }
            }
            case "advanced_carrots" -> {
                drop(block, Key.minecraft("carrot"), 1);
                if (age >= 7 && bonus > 0) drop(block, Key.minecraft("carrot"), bonus);
            }
            case "advanced_potatoes" -> {
                drop(block, Key.minecraft("potato"), 1);
                if (age >= 7) {
                    if (bonus > 0) drop(block, Key.minecraft("potato"), bonus);
                    if (rand.nextDouble() < 0.02) drop(block, Key.minecraft("poisonous_potato"), 1);
                }
            }
            case "advanced_beetroots" -> {
                if (age >= 3) {
                    drop(block, Key.minecraft("beetroot"), 1);
                    if (bonus > 0) drop(block, Key.minecraft("beetroot_seeds"), bonus);
                }
            }
            // vanilla stem loot: 0-3 seeds + fortune binomial(p=4/7, extra=3)
            case "advanced_pumpkin_stem" -> drop(block, Key.minecraft("pumpkin_seeds"), binomial(3 + fortune, rand));
            case "advanced_melon_stem" -> drop(block, Key.minecraft("melon_seeds"), binomial(3 + fortune, rand));
            // torchflower: mature breaks into the flower, younger drops its seed
            case "advanced_torchflower_crop" -> {
                if (age >= 2) drop(block, Key.minecraft("torchflower"), 1);
                else drop(block, Key.minecraft("torchflower_seeds"), 1);
            }
            // pitcher: vanilla alternatives scale pods with age (approximated)
            case "advanced_pitcher_crop" -> {
                int pods = age >= 4 ? 3 : age >= 2 ? 2 : 1;
                drop(block, Key.minecraft("pitcher_pod"), pods);
            }
            default -> {
            }
        }
    }

    /** Vanilla binomial_with_bonus_count: successes over {@code trials} Bernoulli(4/7) rolls. */
    private static int binomial(int trials, ThreadLocalRandom rand) {
        int value = 0;
        for (int i = 0; i < trials; i++) {
            if (rand.nextDouble() < 0.5714286) value++;
        }
        return value;
    }

    private void drop(Block at, Key item, int count) {
        ItemStack stack = CraftEngineHook.buildItem(item);
        if (stack == null || count <= 0) return;
        stack.setAmount(count);
        at.getWorld().dropItemNaturally(at.getLocation().add(0.5, 0.4, 0.5), stack);
    }
}
