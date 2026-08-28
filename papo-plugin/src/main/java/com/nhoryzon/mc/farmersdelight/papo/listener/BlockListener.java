package com.nhoryzon.mc.farmersdelight.papo.listener;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.gui.ContainerBlockGui;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockPlaceEvent;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** CE-block interactions: stove, basket, cabinet, crops, compost, rich soil, pies. */
public final class BlockListener implements Listener {

    private final FarmersDelightPlugin plugin;

    private static final Set<String> CROP_IDS = Set.of(
            "farmersdelight:cabbages", "farmersdelight:onions", "farmersdelight:budding_tomatoes",
            "farmersdelight:tomatoes", "farmersdelight:rice", "farmersdelight:rice_panicle",
            "farmersdelight:wild_cabbages", "farmersdelight:wild_onions", "farmersdelight:wild_tomatoes",
            "farmersdelight:wild_carrots", "farmersdelight:wild_potatoes", "farmersdelight:wild_beetroots",
            "farmersdelight:wild_rice", "farmersdelight:sandy_shrub",
            "farmersdelight:brown_mushroom_colony", "farmersdelight:red_mushroom_colony");

    private static final Set<String> PIE_IDS = Set.of(
            "farmersdelight:apple_pie", "farmersdelight:sweet_berry_cheesecake", "farmersdelight:chocolate_pie");

    public BlockListener(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
    }

    private GameTicker ticker() {
        return plugin.gameTicker();
    }

    /* ===================== placement rules + index registration ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(CustomBlockPlaceEvent event) {
        ImmutableBlockState state = event.blockState();
        Key id = state.owner().value().id();
        Block block = event.bukkitBlock();

        // soil placement registers for boost ticking
        if (id.equals(FD.RICH_SOIL) || id.equals(FD.RICH_SOIL_FARMLAND)) {
            ticker().soilIndex.add(block);
            return;
        }
        if (id.equals(FD.STOVE)) {
            ticker().stoveIndex.add(block);
            return;
        }
        if (FEAST_IDS.contains(id.toString().substring(id.toString().indexOf(':') + 1))) {
            // feasts are placed fully stocked via the block's default state
            plugin.advancements().onCustomPlace(event.player(), id.toString());
            return;
        }
        if (id.equals(FD.COOKING_POT)) {
            ticker().potIndex.add(block);
            // restore a meal carried inside the placed pot item (mod CopyMeal);
            // the event exposes no item, so read the remaining main-hand stack
            ItemStack placed = event.player().getInventory().getItemInMainHand();
            if (placed != null && !placed.getType().isAir() && placed.getItemMeta() != null) {
                var itemPdc = placed.getItemMeta().getPersistentDataContainer();
                byte[] meal = itemPdc.get(GameTicker.fdKey("pot_meal"), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY);
                if (meal != null && meal.length > 0) {
                    try {
                        ItemStack mealStack = ItemStack.deserializeBytes(meal);
                        if (!mealStack.getType().isAir()) {
                            ItemStack[] inv = ticker().potInv(block);
                            inv[GameTicker.SLOT_MEAL] = mealStack;
                            plugin.blockStore().setItems(block, "inv", inv);
                            String containerId = itemPdc.get(GameTicker.fdKey("pot_container"),
                                    org.bukkit.persistence.PersistentDataType.STRING);
                            if (containerId != null) {
                                plugin.blockStore().setString(block, "container", containerId);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return;
        }
        if (id.equals(FD.BASKET)) {
            ticker().basketIndex.add(block);
            var dirProp = state.<String>getProperty("direction");
            if (dirProp != null) {
                plugin.blockStore().setString(block, "facing", state.getNullable(dirProp));
            }
            return;
        }
        if (id.equals(FD.ORGANIC_COMPOST)) {
            ticker().compostIndex.add(block);
            return;
        }
        if (CROP_IDS.contains(id.toString())) {
            if (!validateCropPlacement(id, block, event.player())) {
                event.setCancelled(true);
                return;
            }
            ticker().cropIndex.add(block);
            plugin.advancements().onPlant(event.player(), id.toString());
            // colony items place a mature colony (mod MushroomColonyBlockItem)
            if (id.toString().endsWith("_mushroom_colony")) {
                ticker().setBlockProperty(block, "age", 3);
            }
        }
        if (id.equals(FD.ORGANIC_COMPOST)) {
            plugin.advancements().onCustomPlace(event.player(), "organic_compost");
        }
    }

    private boolean validateCropPlacement(Key id, Block block, Player player) {
        Block below = block.getRelative(BlockFace.DOWN);
        String s = id.toString();
        if (s.endsWith("rice") || s.contains("rice")) {
            // rice must sit inside water above soil
            if (block.getType() != Material.WATER) {
                sendHint(player, "rice");
                return false;
            }
            return plugin.cropManager().isBlock(below, FD.RICH_SOIL)
                    || plugin.cropManager().isBlock(below, FD.RICH_SOIL_FARMLAND)
                    || below.getType() == Material.DIRT || below.getType() == Material.GRASS_BLOCK
                    || below.getType() == Material.FARMLAND || below.getType() == Material.SAND;
        }
        if (s.contains("mushroom_colony")) {
            return true;
        }
        switch (s) {
            case "farmersdelight:cabbages", "farmersdelight:onions" -> {
                return below.getType() == Material.FARMLAND
                        || plugin.cropManager().isBlock(below, FD.RICH_SOIL_FARMLAND);
            }
            case "farmersdelight:budding_tomatoes", "farmersdelight:tomatoes" -> {
                return below.getType() == Material.FARMLAND
                        || plugin.cropManager().isBlock(below, FD.RICH_SOIL_FARMLAND);
            }
            default -> {
                return true; // wild crops are placed by worldgen only
            }
        }
    }

    private void sendHint(Player player, String what) {
        // minimal feedback; keep silent like vanilla when placement is invalid
    }

    /* ===================== interactions ===================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(CustomBlockInteractEvent event) {
        ImmutableBlockState state = event.blockState();
        Key id = state.owner().value().id();
        Block block = event.bukkitBlock();
        Player player = event.player();
        ItemStack held = event.item();
        String s = id.toString();

        if (s.startsWith("farmersdelight:")) {
            s = s.substring(s.indexOf(':') + 1);
        }

        switch (s) {
            case "stove" -> {
                event.setCancelled(true);
                interactStove(block, player, held, state);
            }
            case "basket", "oak_cabinet", "birch_cabinet", "spruce_cabinet", "jungle_cabinet",
                 "acacia_cabinet", "dark_oak_cabinet", "mangrove_cabinet",
                 "crimson_cabinet", "warped_cabinet" -> {
                // GUI handled natively by CE simple_storage_block behavior
            }
            case "rich_soil" -> {
                if (held != null && isHoe(held.getType())) {
                    event.setCancelled(true);
                    if (block.getRelative(BlockFace.UP).getType().isAir()) {
                        CraftEngineHook.removeBlock(block, false);
                        CraftEngineHook.placeBlock(block.getLocation(), FD.RICH_SOIL_FARMLAND, false);
                        ticker().soilIndex.remove(block);
                        ticker().soilIndex.add(block);
                        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                                "minecraft:item.hoe.till", SoundCategory.BLOCKS, 1.0f, 1.0f);
                        if (player.getGameMode() != GameMode.CREATIVE) damage(held, player);
                    }
                }
            }
            case "organic_compost" -> {
                event.setCancelled(true);
                interactCompost(block, player, held, state);
            }
            case "cooking_pot" -> {
                event.setCancelled(true);
                interactCookingPotBlock(block, state, player, held);
            }
            case "roast_chicken_block", "stuffed_pumpkin_block", "honey_glazed_ham_block",
                 "shepherds_pie_block", "rice_roll_medley_block" -> {
                event.setCancelled(true);
                interactFeastBlock(block, state, player, held);
            }
            case "cabbages", "onions", "tomatoes", "rice_panicle",
                 "brown_mushroom_colony", "red_mushroom_colony" -> {
                if (held != null && held.getType() == Material.BONE_MEAL) {
                    event.setCancelled(true);
                    if (plugin.cropManager().tryBonemeal(block)) {
                        consumeBoneMeal(player, held);
                    }
                    return;
                }
                event.setCancelled(plugin.cropManager().tryHarvest(block, player, held));
            }
            case "sandy_shrub" -> {
                if (held != null && held.getType() == Material.BONE_MEAL) {
                    event.setCancelled(true);
                    if (plugin.cropManager().tryBonemeal(block)) {
                        consumeBoneMeal(player, held);
                    }
                }
            }
            case "apple_pie", "sweet_berry_cheesecake", "chocolate_pie" -> {
                event.setCancelled(true);
                interactPie(block, player, state);
            }
            default -> {
            }
        }
    }

    private boolean isHoe(Material material) {
        return material.name().endsWith("_HOE");
    }

    private void damage(ItemStack tool, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        tool.damage(1, player);
    }

    private void consumeBoneMeal(Player player, ItemStack held) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        held.setAmount(held.getAmount() - 1);
    }

    /* ===================== stove ===================== */

    private void interactStove(Block stove, Player player, ItemStack held, ImmutableBlockState state) {
        Boolean lit = ticker().getBool(state, "lit");
        boolean isLit = lit != null && lit;

        if (!isLit) {
            if (held != null && (held.getType() == Material.FLINT_AND_STEEL
                    || held.getType() == Material.FIRE_CHARGE)) {
                ticker().setBlockProperty(stove, "lit", true);
                stove.getWorld().playSound(stove.getLocation().add(0.5, 0.5, 0.5),
                        "minecraft:item.flintandsteel.use", SoundCategory.BLOCKS, 1.0f, 1.0f);
                if (held.getType() == Material.FLINT_AND_STEEL) damage(held, player);
                else consumeBoneMeal(player, held);
                return;
            }
        }
        // mod order: try placing food FIRST (no lit requirement - the mod lets you
        // stock an unlit stove and it starts cooking once lit), extinguish only when
        // the held item matches no campfire recipe
        if (held != null && !held.getType().isAir() && !held.getType().name().endsWith("_SHOVEL")
                && held.getType() != Material.WATER_BUCKET
                && ticker().campfireResult(held) != null
                && !isBlockedAbove(stove)) {
            ItemStack[] grill = plugin.blockStore().getItems(stove, "grill");
            if (grill == null) grill = new ItemStack[6];
            for (int i = 0; i < 6; i++) {
                if (grill[i] == null || grill[i].getType().isAir()) {
                    ItemStack one = held.clone();
                    one.setAmount(1);
                    grill[i] = one;
                    consumeBoneMeal(player, held); // consumes one food item
                    var facingProp = state.<String>getProperty("facing");
                    if (facingProp != null) {
                        String facing = state.getNullable(facingProp);
                        plugin.blockStore().setString(stove, "facing", facing == null ? "north" : facing);
                    }
                    int[] totals = new int[6];
                    int[] existing = grillTimes(plugin.blockStore().getString(stove, "grill_totals"));
                    System.arraycopy(existing, 0, totals, 0, 6);
                    totals[i] = ticker().campfireTime(one);
                    plugin.blockStore().setString(stove, "grill_totals", join(totals));
                    plugin.blockStore().setItems(stove, "grill", grill);
                    ticker().refreshStoveDisplays(stove, grill);
                    stove.getWorld().playSound(stove.getLocation().add(0.5, 0.8, 0.5),
                            "minecraft:block.bamboo_wood.place", SoundCategory.BLOCKS, 0.5f, 1.0f);
                    return;
                }
            }
        }

        // mod tryExtinguish: shovel / water bucket put the stove out (only reached
        // when the held item is not a placeable grill item)
        if (isLit && held != null && (held.getType().name().endsWith("_SHOVEL")
                || held.getType() == Material.WATER_BUCKET)) {
            ticker().setBlockProperty(stove, "lit", false);
            stove.getWorld().playSound(stove.getLocation().add(0.5, 0.5, 0.5),
                    "minecraft:item.firecharge.use", SoundCategory.BLOCKS, 1.0f, 0.8f);
            if (held.getType() == Material.WATER_BUCKET) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            } else {
                damage(held, player);
            }
        }
    }

    /** mod isStoveBlockedAbove: no free space above the grill means nothing to place onto. */
    private static boolean isBlockedAbove(Block stove) {
        return !stove.getRelative(org.bukkit.block.BlockFace.UP).isPassable();
    }

    private int[] grillTimes(String joined) {
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

    private String join(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /* ===================== organic compost ===================== */

    private void interactCompost(Block compost, Player player, ItemStack held, ImmutableBlockState state) {
        if (held == null || held.getType().isAir()) return;
        Integer level = ticker().getInt(state, "composting");
        if (level == null || level >= 7) return;
        Float chance = plugin.compostables().get(GameTicker.idOf(held));
        if (chance == null) return;
        consumeBoneMeal(player, held);
        if (Math.random() < chance) {
            ticker().setBlockProperty(compost, "composting", level + 1);
            compost.getWorld().playSound(compost.getLocation().add(0.5, 0.5, 0.5),
                    "minecraft:block.composter.fill", SoundCategory.BLOCKS, 0.8f, 1.0f);
        } else {
            compost.getWorld().playSound(compost.getLocation().add(0.5, 0.5, 0.5),
                    "minecraft:block.composter.fill_failed", SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
    }

    /* ===================== pies ===================== */

    private void interactPie(Block pie, Player player, ImmutableBlockState state) {
        Integer bites = ticker().getInt(state, "bites");
        if (bites == null) return;
        if (bites >= 4) {
            // eaten up: remove like cake
            ticker().cropIndex.remove(pie);
            CraftEngineHook.removeBlock(pie, false);
            return;
        }
        // eating a bite directly (like cake): restore food
        var food = player.getFoodLevel();
        player.setFoodLevel(Math.min(20, food + 2));
        float sat = player.getSaturation();
        player.setSaturation(Math.min(player.getFoodLevel(), sat + 0.4f));
        pie.getWorld().playSound(pie.getLocation().add(0.5, 0.5, 0.5),
                "minecraft:entity.generic.eat", SoundCategory.PLAYERS, 0.8f, 1.0f);
        ticker().setBlockProperty(pie, "bites", bites + 1);
    }

    /* ===================== block feasts ===================== */

    private static final java.util.Set<String> FEAST_IDS = java.util.Set.of(
            "roast_chicken_block", "stuffed_pumpkin_block", "honey_glazed_ham_block",
            "shepherds_pie_block", "rice_roll_medley_block");

    private void interactFeastBlock(Block block, ImmutableBlockState state,
                                    Player player, ItemStack held) {
        Integer servingsState = ticker().getInt(state, "servings");
        int servings = servingsState == null ? 0 : servingsState;
        boolean hasLeftovers = !block.getType().key().toString().contains("stuffed_pumpkin");

        if (servings <= 0) {
            // mod takeServing(0): wood-break sound + breakBlock(drops=true)
            block.getWorld().playSound(block.getLocation(), "minecraft:block.wood.break",
                    SoundCategory.PLAYERS, 0.8f, 0.8f);
            dropFeastLoot(block, 0);
            CraftEngineHook.removeBlock(block, false);
            return;
        }
        // mod: the serving's recipe remainder is the bowl, so a bowl must be held
        if (held == null || held.getType() != Material.BOWL) return;
        ItemStack serving = feastServing(block, servings);
        if (serving == null) return;
        held.setAmount(held.getAmount() - 1);
        giveOrDrop(player, serving);
        int next = servings - 1;
        if (next == 0 && !hasLeftovers) {
            // mod: no-leftovers feasts vanish at the last serving (no drop)
            CraftEngineHook.removeBlock(block, false);
        } else {
            ticker().setBlockProperty(block, "servings", next);
        }
        block.getWorld().playSound(block.getLocation().add(0.5, 0.4, 0.5),
                "minecraft:item.armor.equip_generic", SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** Mod per-serving item, including RiceRollMedley's servings-indexed table. */
    private ItemStack feastServing(Block block, int servings) {
        String item = switch (block.getType().key().toString()) {
            case "roast_chicken_block" -> "roast_chicken";
            case "stuffed_pumpkin_block" -> "stuffed_pumpkin";
            case "honey_glazed_ham_block" -> "honey_glazed_ham";
            case "shepherds_pie_block" -> "shepherds_pie";
            case "rice_roll_medley_block" -> switch (servings) {
                case 8, 7, 6 -> "kelp_roll_slice";
                case 5, 4, 3 -> "salmon_roll";
                default -> "cod_roll";
            };
            default -> null;
        };
        return item == null ? null : CraftEngineHook.buildItem(Key.of(FD.MOD_ID, item));
    }

    /** Mod loot: full feast drops itself; a touched one drops bowl + bone meal. */
    private void dropFeastLoot(Block block, int servings) {
        String id = block.getType().key().toString();
        if (servings == 4 || (id.equals("rice_roll_medley_block") && servings == 8)) {
            ItemStack self = CraftEngineHook.buildItem(Key.of(FD.MOD_ID, id));
            if (self != null) {
                block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.4, 0.5), self);
            }
        } else {
            dropFeastLootSimple(block, Material.BOWL);
            dropFeastLootSimple(block, Material.BONE_MEAL);
        }
    }

    private void dropFeastLootSimple(Block block, Material material) {
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.4, 0.5),
                new ItemStack(material));
    }

    /* ===================== block cooking pot ===================== */

    private void interactCookingPotBlock(Block block, ImmutableBlockState state,
                                         Player player, ItemStack held) {
        String support = null;
        var supProp = state.getProperty("support");
        if (supProp != null) {
            Object v = state.getNullable(supProp);
            support = v == null ? "none" : String.valueOf(v);
        }
        // sneak + empty hand cycles the support variant (mod support toggle)
        if ((held == null || held.getType().isAir()) && player.isSneaking()) {
            String next = switch (support) {
                case "tray" -> "handle";
                case "handle" -> "none";
                default -> "tray";
            };
            ticker().setBlockProperty(block, "support", next);
            block.getWorld().playSound(block.getLocation(), "minecraft:block.lantern.place",
                    SoundCategory.BLOCKS, 0.7f, 1.0f);
            return;
        }
        // serve a held-container portion before opening (mod useHeldItemOnMeal)
        if (held != null && !held.getType().isAir()) {
            ItemStack portion = ticker().servePotBlock(block, held);
            if (portion != null) {
                giveOrDrop(player, portion);
                player.swingMainHand();
                return;
            }
        }
        com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotBlockGui.open(plugin, block, player);
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /* ===================== breaks ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(CustomBlockBreakEvent event) {
        ImmutableBlockState state = event.blockState();
        Key id = state.owner().value().id();
        Block block = event.bukkitBlock();
        String s = id.toString();

        if (CROP_IDS.contains(s)) {
            event.setDropItems(false);
            plugin.cropManager().breakDrops(block, event.getPlayer().getInventory().getItemInMainHand());
            ticker().cropIndex.remove(block);
            if (s.contains("rice_panicle") || s.endsWith("rice")) {
                // adjacent rice blocks handled by CropManager
            }
        } else if (s.startsWith("farmersdelight:advanced_")) {
            // advanced vanilla crops: vanilla loot-table drops + knife straw (plugin-side)
            event.setDropItems(false);
            plugin.cropManager().advancedCropDrops(block, state, event.getPlayer(),
                    event.getPlayer().getInventory().getItemInMainHand());
        } else if (s.equals("farmersdelight:cooking_pot")) {
            // mod CopyMeal: every slot but the meal drops raw; the meal travels inside
            // the dropped pot item (PDC) along with its container requirement; stored
            // experience is paid out as orbs; the replacement restores the meal
            event.setDropItems(false);
            ticker().potIndex.remove(block);
            Location drop = block.getLocation().add(0.5, 0.6, 0.5);
            ItemStack[] inv = ticker().potInv(block);
            for (int i = 0; i < inv.length; i++) {
                if (i == GameTicker.SLOT_MEAL) continue;
                ItemStack stack = inv[i];
                if (stack != null && !stack.getType().isAir()) {
                    drop.getWorld().dropItemNaturally(drop, stack);
                }
            }
            ticker().spawnExpBlockOrbs(block, drop);
            ItemStack meal = inv[GameTicker.SLOT_MEAL];
            ItemStack potItem = CraftEngineHook.buildItem(id);
            if (potItem != null) {
                if (meal != null && !meal.getType().isAir()) {
                    String containerId = plugin.blockStore().getString(block, "container");
                    ItemStack mealCopy = meal;
                    potItem.editMeta(meta -> {
                        meta.getPersistentDataContainer().set(GameTicker.fdKey("pot_meal"),
                                org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, mealCopy.serializeAsBytes());
                        if (containerId != null && !containerId.isEmpty()) {
                            meta.getPersistentDataContainer().set(GameTicker.fdKey("pot_container"),
                                    org.bukkit.persistence.PersistentDataType.STRING, containerId);
                        }
                        meta.lore(java.util.List.of(
                                net.kyori.adventure.text.Component.text()
                                        .content("装有 " + mealCopy.getAmount() + " 份: ")
                                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                                        .append(mealCopy.effectiveName().colorIfAbsent(
                                                net.kyori.adventure.text.format.NamedTextColor.GRAY))
                                        .build()));
                    });
                }
                drop.getWorld().dropItemNaturally(drop, potItem);
            }
            plugin.blockStore().clearAll(block, java.util.List.of(
                    "inv", "cook", "cooktotal", "container", "exp", "facing"));
        } else if (s.equals("farmersdelight:organic_compost")) {
            event.setDropItems(false);
            ItemStack self = CraftEngineHook.buildItem(FD.ORGANIC_COMPOST);
            if (self != null) block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5), self);
            ticker().compostIndex.remove(block);
        } else if (s.equals("farmersdelight:stove")) {
            // drop grilled items that are still on the rack
            ItemStack[] grill = plugin.blockStore().getItems(block, "grill");
            if (grill != null) {
                for (ItemStack g : grill) {
                    if (g != null && !g.getType().isAir()) {
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.8, 0.5), g);
                    }
                }
            }
            plugin.blockStore().clearAll(block, java.util.List.of("grill", "grill_times",
                    "grill_totals", "facing", "grill0", "grill1", "grill2", "grill3", "grill4", "grill5"));
            ticker().refreshStoveDisplays(block, new ItemStack[6]);
            ticker().stoveIndex.remove(block);
        } else if (s.equals("farmersdelight:basket")) {
            plugin.blockStore().clear(block, "facing");
            ticker().basketIndex.remove(block);
        } else if (FEAST_IDS.contains(s.substring(s.indexOf(':') + 1))) {
            event.setDropItems(false);
            Integer servingsState = ticker().getInt(state, "servings");
            dropFeastLoot(block, servingsState == null ? 0 : servingsState);
        } else if (PIE_IDS.contains(s)) {
            event.setDropItems(false); // pies drop nothing, like cake
        } else if (s.equals("farmersdelight:rich_soil") || s.equals("farmersdelight:rich_soil_farmland")) {
            ticker().soilIndex.remove(block);
        }
    }

    /**
     * Mod straw loot injects on VANILLA blocks: knives harvesting mature wheat always
     * yield one straw; cutting grass/tall grass has a 20% straw chance (mod loot injects).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes.isKnife(tool)) return;
        Block block = event.getBlock();
        java.util.concurrent.ThreadLocalRandom rand = java.util.concurrent.ThreadLocalRandom.current();
        boolean straw = false;
        if (block.getType() == Material.WHEAT
                && block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable
                && ageable.getAge() == ageable.getMaximumAge()) {
            straw = true;
        } else if ((block.getType() == Material.SHORT_GRASS || block.getType() == Material.TALL_GRASS)
                && rand.nextDouble() < 0.2) {
            straw = true;
        }
        if (straw) {
            ItemStack strawStack = CraftEngineHook.buildItem(FD.STRAW);
            if (strawStack != null) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.4, 0.5), strawStack);
                plugin.advancements().onHarvestStraw(event.getPlayer());
            }
        }
    }
}
