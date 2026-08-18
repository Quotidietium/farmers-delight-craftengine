package com.nhoryzon.mc.farmersdelight.papo;

import net.momirealms.craftengine.core.util.Key;

import java.util.List;
import java.util.Set;

/** Central identifiers and shared constants. */
public final class FD {

    public static final String MOD_ID = "farmersdelight";
    public static final String VERSION = "1.0.4";

    private FD() {
    }

    public static Key key(String path) {
        return Key.of(MOD_ID, path);
    }

    /* furniture ids */
    public static final Key COOKING_POT = key("cooking_pot");
    public static final Key CUTTING_BOARD = key("cutting_board");
    public static final Key SKILLET = key("skillet");
    public static final Key ROPE = key("rope");
    public static final Key SAFETY_NET = key("safety_net");
    public static final Key CANVAS_RUG = key("canvas_rug");
    public static final Key TATAMI = key("tatami");
    public static final Key FULL_TATAMI_MAT = key("full_tatami_mat");
    public static final Key HALF_TATAMI_MAT = key("half_tatami_mat");
    public static final Key ROAST_CHICKEN_BLOCK = key("roast_chicken_block");
    public static final Key STUFFED_PUMPKIN_BLOCK = key("stuffed_pumpkin_block");
    public static final Key HONEY_GLAZED_HAM_BLOCK = key("honey_glazed_ham_block");
    public static final Key SHEPHERDS_PIE_BLOCK = key("shepherds_pie_block");
    public static final Key RICE_ROLL_MEDLEY_BLOCK = key("rice_roll_medley_block");

    /* block ids */
    public static final Key STOVE = key("stove");
    public static final Key BASKET = key("basket");
    public static final Key RICH_SOIL = key("rich_soil");
    public static final Key RICH_SOIL_FARMLAND = key("rich_soil_farmland");
    public static final Key ORGANIC_COMPOST = key("organic_compost");
    public static final Key CABBAGE_CROP = key("cabbages");
    public static final Key ONION_CROP = key("onions");
    public static final Key BUDDING_TOMATO_CROP = key("budding_tomatoes");
    public static final Key TOMATO_CROP = key("tomatoes");
    public static final Key RICE_CROP = key("rice");
    public static final Key RICE_PANICLE = key("rice_panicle");
    public static final Key WILD_RICE = key("wild_rice");

    /* item ids */
    public static final Key BOWL = Key.minecraft("bowl");
    public static final Key KNIFE_FLINT = key("flint_knife");
    public static final Key KNIFE_IRON = key("iron_knife");
    public static final Key KNIFE_GOLDEN = key("golden_knife");
    public static final Key KNIFE_DIAMOND = key("diamond_knife");
    public static final Key KNIFE_NETHERITE = key("netherite_knife");

    public static final List<Key> KNIVES = List.of(KNIFE_FLINT, KNIFE_IRON, KNIFE_GOLDEN,
            KNIFE_DIAMOND, KNIFE_NETHERITE);

    public static final Key CABBAGE_SEEDS = key("cabbage_seeds");
    public static final Key TOMATO_SEEDS = key("tomato_seeds");
    public static final Key RICE_SEEDS = key("rice");
    public static final Key ONION = key("onion");
    public static final Key CABBAGE = key("cabbage");
    public static final Key TOMATO = key("tomato");
    public static final Key RICE_PANICLE_ITEM = key("rice_panicle");
    public static final Key STRAW = key("straw");
    public static final Key ROTTEN_TOMATO = key("rotten_tomato");
    public static final Key SKILLET_ITEM = key("skillet");
    public static final Key TREE_BARK = key("tree_bark");
    public static final Key CANVAS = key("canvas");
    public static final Key DOG_FOOD = key("dog_food");
    public static final Key HORSE_FEED = key("horse_feed");
    public static final Key MILK_BOTTLE = key("milk_bottle");
    public static final Key HOT_COCOA = key("hot_cocoa");
    public static final Key MELON_JUICE = key("melon_juice");

    /* sounds (farmersdelight namespace custom events) */
    public static final String SND_BOIL = MOD_ID + ":block.cooking_pot.boil";
    public static final String SND_BOIL_SOUP = MOD_ID + ":block.cooking_pot.boil_soup";
    public static final String SND_CB_KNIFE = MOD_ID + ":block.cutting_board.knife";
    public static final String SND_SKILLET_SIZZLE = MOD_ID + ":block.skillet.sizzle";
    public static final String SND_SKILLET_ADD_FOOD = MOD_ID + ":block.skillet.add_food";
    public static final String SND_SKILLET_ATK_STRONG = MOD_ID + ":item.skillet.attack.strong";
    public static final String SND_SKILLET_ATK_WEAK = MOD_ID + ":item.skillet.attack.weak";
    public static final String SND_STOVE_CRACKLE = MOD_ID + ":block.stove.crackle";
    public static final String SND_RT_THROW = MOD_ID + ":entity.rotten_tomato.throw";
    public static final String SND_RT_HIT = MOD_ID + ":entity.rotten_tomato.hit";

    /* vanilla blocks considered heat sources for cooking (mirrors mod heat_sources tag) */
    public static final Set<org.bukkit.Material> HEAT_SOURCES = Set.of(
            org.bukkit.Material.MAGMA_BLOCK,
            org.bukkit.Material.LAVA,
            org.bukkit.Material.CAMPFIRE,
            org.bukkit.Material.SOUL_CAMPFIRE,
            org.bukkit.Material.FURNACE,
            org.bukkit.Material.BLAST_FURNACE);

    public static final Set<org.bukkit.Material> HEAT_CONDUCTORS = Set.of(
            org.bukkit.Material.IRON_BLOCK,
            org.bukkit.Material.IRON_BARS,
            org.bukkit.Material.COPPER_BLOCK,
            org.bukkit.Material.RAW_IRON_BLOCK);

    /** Compost activator blocks (mod compost_activators tag, vanilla subset). */
    public static final Set<org.bukkit.Material> COMPOST_ACTIVATORS = Set.of(
            org.bukkit.Material.BROWN_MUSHROOM, org.bukkit.Material.RED_MUSHROOM,
            org.bukkit.Material.SHORT_GRASS, org.bukkit.Material.TALL_GRASS,
            org.bukkit.Material.SEAGRASS, org.bukkit.Material.SWEET_BERRY_BUSH,
            org.bukkit.Material.DEAD_BUSH);
}
