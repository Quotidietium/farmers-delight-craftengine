package com.nhoryzon.mc.farmersdelight.papo.listener;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotGui;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent;
import net.momirealms.craftengine.bukkit.api.event.FurniturePlaceEvent;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Interactions with FD furniture: cutting board, cooking pot, skillet, feasts, rope, canvas signs. */
public final class FurnitureListener implements Listener {

    private final FarmersDelightPlugin plugin;

    public FurnitureListener(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
    }

    private GameTicker ticker() {
        return plugin.gameTicker();
    }

    /* ===================== placement tracking ===================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(FurniturePlaceEvent event) {
        BukkitFurniture furniture = event.furniture();
        plugin.furnitureTracker().track(furniture.baseEntity());
        String facing = facingOf(furniture);
        GameTicker.data(furniture).set(GameTicker.fdKey("facing"),
                PersistentDataType.STRING, facing);
        String placedId = furniture.id().value();
        // pots & skillets sitting on a stove get the tray visual (mod getTrayState)
        if (placedId.equals("cooking_pot") || placedId.equals("skillet")) {
            org.bukkit.block.Block below = furniture.location().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            var belowState = CraftEngineHook.customBlockState(below);
            if (belowState != null && belowState.owner().value().id().equals(FD.STOVE)) {
                furniture.setVariant("tray", false);
            }
        }
        // placed pots/skillets restore the state carried inside their dropped items
        // (mod CopyMeal meal carry / CopySkillet full item carry incl. enchants)
        if (placedId.equals("skillet") || placedId.equals("cooking_pot")) {
            org.bukkit.inventory.ItemStack placedItem = event.player().getInventory().getItem(
                    event.hand() == net.momirealms.craftengine.core.entity.player.InteractionHand.MAIN_HAND
                            ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            if (placedItem != null && !placedItem.getType().isAir()) {
                org.bukkit.persistence.PersistentDataContainer itemPdc =
                        placedItem.getItemMeta() == null ? null : placedItem.getItemMeta().getPersistentDataContainer();
                if (placedId.equals("skillet")) {
                    PersistentDataContainer pdc = GameTicker.data(furniture);
                    // remember the actual placed stack so breaking drops the same enchanted skillet (mod BE item)
                    pdc.set(GameTicker.fdKey("skillet_item"), PersistentDataType.BYTE_ARRAY,
                            placedItem.serializeAsBytes());
                    int fireAspect = placedItem.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
                    if (fireAspect > 0) {
                        pdc.set(GameTicker.fdKey("fa"), PersistentDataType.INTEGER, fireAspect);
                    }
                    byte[] food = itemPdc == null ? null
                            : itemPdc.get(GameTicker.fdKey("skillet_food"), PersistentDataType.BYTE_ARRAY);
                    if (food != null && food.length > 0) {
                        try {
                            ItemStack foodStack = ItemStack.deserializeBytes(food);
                            if (!foodStack.getType().isAir()) {
                                ticker().setSkilletItem(furniture, foodStack);
                                String result = itemPdc.get(GameTicker.fdKey("skillet_result"), PersistentDataType.STRING);
                                Integer total = itemPdc.get(GameTicker.fdKey("skillet_total"), PersistentDataType.INTEGER);
                                if (result != null) {
                                    pdc.set(GameTicker.fdKey("result"), PersistentDataType.STRING, result);
                                }
                                if (total != null) {
                                    pdc.set(GameTicker.fdKey("cooktotal"), PersistentDataType.INTEGER, total);
                                }
                                pdc.set(GameTicker.fdKey("cook"), PersistentDataType.INTEGER, 0);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } else if (itemPdc != null) {
                    byte[] mealBytes = itemPdc.get(GameTicker.fdKey("pot_meal"), PersistentDataType.BYTE_ARRAY);
                    if (mealBytes != null && mealBytes.length > 0) {
                        try {
                            ItemStack meal = ItemStack.deserializeBytes(mealBytes);
                            if (!meal.getType().isAir()) {
                                ItemStack[] inv = GameTicker.inv(furniture);
                                inv[GameTicker.SLOT_MEAL] = meal;
                                GameTicker.saveInv(furniture, inv);
                                String containerId = itemPdc.get(
                                        GameTicker.fdKey("pot_container"), PersistentDataType.STRING);
                                if (containerId != null && !containerId.isEmpty()) {
                                    GameTicker.data(furniture).set(GameTicker.fdKey("container"),
                                            PersistentDataType.STRING, containerId);
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
        // feasts are placed fully stocked (highest servings variant)
        if (placedId.endsWith("_block") && (isFeast(furniture.id()))) {
            int max = placedId.equals("rice_roll_medley_block") ? 8 : 4;
            furniture.setVariant("s" + max, false);
        }
        // plain tatami halves pair up visually with neighbours
        if (furniture.id().equals(FD.TATAMI)) {
            var dir = dirOf(facing);
            for (org.bukkit.util.Vector offset : new org.bukkit.util.Vector[]{
                    dir, dir.clone().multiply(-1)}) {
                Location neighbourLoc = furniture.location().clone().add(offset);
                var neighbour = plugin.furnitureTracker().at(neighbourLoc.add(0.5, 0, 0.5), FD.TATAMI);
                if (neighbour != null) {
                    furniture.setVariant("paired", false);
                    neighbour.furniture().setVariant("paired", false);
                    break;
                }
            }
        }
        // full tatami mats occupy two blocks: place the linked partner part
        if (furniture.id().equals(FD.FULL_TATAMI_MAT)) {
            var dir = dirOf(facing);
            Location partnerLoc = furniture.location().clone().add(dir);
            BukkitFurniture partner = CraftEngineHook.placeFurniture(partnerLoc, FD.FULL_TATAMI_MAT);
            if (partner != null) {
                plugin.furnitureTracker().track(partner.baseEntity());
                GameTicker.data(partner).set(GameTicker.fdKey("facing"),
                        PersistentDataType.STRING, facing);
                GameTicker.data(partner).set(GameTicker.fdKey("partner"),
                        PersistentDataType.STRING, furniture.baseEntity().getUniqueId().toString());
                GameTicker.data(furniture).set(GameTicker.fdKey("partner"),
                        PersistentDataType.STRING, partner.baseEntity().getUniqueId().toString());
                partner.setVariant("head", false);
                furniture.setVariant("foot", false);
            }
        }
        // feast / pot / skillet placement advancements
        plugin.advancements().onCustomPlace(event.player(), furniture.id().value());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(FurnitureBreakEvent event) {
        BukkitFurniture furniture = event.furniture();
        plugin.furnitureTracker().untrack(furniture.baseEntity());
        Key id = furniture.id();
        if (id.equals(FD.COOKING_POT)) {
            ItemStack[] inv = GameTicker.inv(furniture);
            Location drop = furniture.location().clone().add(0.5, 0.6, 0.5);
            // mod drops every slot EXCEPT the meal display slot, which travels inside the pot item (CopyMeal)
            for (int i = 0; i < inv.length; i++) {
                if (i == GameTicker.SLOT_MEAL) continue;
                ItemStack stack = inv[i];
                if (stack != null && !stack.getType().isAir()) {
                    drop.getWorld().dropItemNaturally(drop, stack);
                }
            }
            ticker().spawnStoredExperience(furniture, drop);
            ItemStack meal = inv[GameTicker.SLOT_MEAL];
            event.setDropItems(false);
            ItemStack potItem = CraftEngineHook.buildItem(id);
            if (potItem != null) {
                if (meal != null && !meal.getType().isAir()) {
                    String containerId = GameTicker.data(furniture).get(
                            GameTicker.fdKey("container"), PersistentDataType.STRING);
                    potItem = potItem.clone();
                    ItemStack mealCopy = meal;
                    potItem.editMeta(meta -> {
                        meta.getPersistentDataContainer().set(GameTicker.fdKey("pot_meal"),
                                PersistentDataType.BYTE_ARRAY, mealCopy.serializeAsBytes());
                        if (containerId != null && !containerId.isEmpty()) {
                            meta.getPersistentDataContainer().set(GameTicker.fdKey("pot_container"),
                                    PersistentDataType.STRING, containerId);
                        }
                        meta.lore(List.of(
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
        } else if (id.equals(FD.CUTTING_BOARD)) {
            ItemStack stored = ticker().skilletItem(furniture);
            if (stored != null) {
                furniture.location().getWorld().dropItemNaturally(
                        furniture.location().clone().add(0.5, 0.4, 0.5), stored);
            }
            ticker().setDisplayChild(furniture, "itemEntity", null, 0.5, 0.4, 0.5, 0.3f);
        } else if (id.equals(FD.SKILLET)) {
            // mod CopySkillet: the uncooked food travels inside the dropped skillet item,
            // and the dropped skillet is the exact stack that was placed (enchants included)
            ItemStack stored = ticker().skilletItem(furniture);
            ticker().setDisplayChild(furniture, "itemEntity", null, 0.5, 0.4, 0.5, 0.3f);
            event.setDropItems(false);
            ItemStack skilletItem = null;
            byte[] placed = GameTicker.data(furniture).get(
                    GameTicker.fdKey("skillet_item"), PersistentDataType.BYTE_ARRAY);
            if (placed != null && placed.length > 0) {
                try {
                    skilletItem = ItemStack.deserializeBytes(placed);
                } catch (Throwable ignored) {
                }
            }
            if (skilletItem == null || skilletItem.getType().isAir()) {
                skilletItem = CraftEngineHook.buildItem(id);
            }
            if (skilletItem != null) {
                if (stored != null && !stored.getType().isAir()) {
                    PersistentDataContainer pdc = GameTicker.data(furniture);
                    String result = pdc.get(GameTicker.fdKey("result"), PersistentDataType.STRING);
                    Integer total = pdc.get(GameTicker.fdKey("cooktotal"), PersistentDataType.INTEGER);
                    Integer fa = pdc.get(GameTicker.fdKey("fa"), PersistentDataType.INTEGER);
                    skilletItem = skilletItem.clone();
                    ItemStack storedCopy = stored;
                    skilletItem.editMeta(meta -> {
                        meta.getPersistentDataContainer().set(GameTicker.fdKey("skillet_food"),
                                PersistentDataType.BYTE_ARRAY, storedCopy.serializeAsBytes());
                        if (result != null) {
                            meta.getPersistentDataContainer().set(GameTicker.fdKey("skillet_result"),
                                    PersistentDataType.STRING, result);
                        }
                        if (total != null) {
                            meta.getPersistentDataContainer().set(GameTicker.fdKey("skillet_total"),
                                    PersistentDataType.INTEGER, total);
                        }
                        if (fa != null) {
                            meta.getPersistentDataContainer().set(GameTicker.fdKey("fa"),
                                    PersistentDataType.INTEGER, fa);
                        }
                    });
                }
                furniture.location().getWorld().dropItemNaturally(
                        furniture.location().clone().add(0.5, 0.4, 0.5), skilletItem);
            }
        } else if (id.toString().endsWith("_canvas_sign") || id.toString().endsWith("_canvas_wall_sign")) {
            removeSignText(furniture);
        } else if (id.equals(FD.TATAMI)) {
            // unpair visual neighbours
            for (var entry : plugin.furnitureTracker().tracked().values()) {
                if (entry.furnitureId().equals(FD.TATAMI)
                        && "paired".equals(entry.furniture().currentVariant() == null
                        ? "ground" : entry.furniture().currentVariant().name())) {
                    Location other = entry.furniture().location();
                    Location self = furniture.location();
                    if (Math.abs(other.getBlockX() - self.getBlockX())
                            + Math.abs(other.getBlockZ() - self.getBlockZ()) == 1
                            && other.getBlockY() == self.getBlockY()) {
                        entry.furniture().setVariant("ground", false);
                    }
                }
            }
        } else if (id.equals(FD.FULL_TATAMI_MAT)) {
            // remove the linked partner half as well
            String partnerId = GameTicker.data(furniture).get(
                    GameTicker.fdKey("partner"), PersistentDataType.STRING);
            if (partnerId != null) {
                try {
                    var partner = org.bukkit.Bukkit.getEntity(java.util.UUID.fromString(partnerId));
                    var entry = partner == null ? null : plugin.furnitureTracker().tracked().get(partner.getUniqueId());
                    if (entry != null) {
                        CraftEngineHook.removeFurniture(entry.furniture(), false, false);
                        plugin.furnitureTracker().untrack(partner);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /* ===================== interactions ===================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(FurnitureInteractEvent event) {
        BukkitFurniture furniture = event.furniture();
        plugin.getLogger().info("[PATH-A] CE furniture event: " + furniture.id()
                + " player=" + event.player().getName());
        Key id = furniture.id();
        Player player = event.player();
        org.bukkit.inventory.EquipmentSlot slot = event.hand() == net.momirealms.craftengine.core.entity.player.InteractionHand.OFF_HAND
                ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND;
        ItemStack held = player.getInventory().getItem(slot);

        if (id.equals(FD.CUTTING_BOARD)) {
            event.setCancelled(true);
            interactCuttingBoard(furniture, player, held);
        } else if (id.equals(FD.COOKING_POT)) {
            // pot GUI: let CE's native storage behavior open first (proven pipeline);
            // our listener swaps in the custom layout one tick later.
            // Only the container-serving interaction cancels here.
            interactCookingPotServeOnly(furniture, player, held, event::setCancelled);
        } else if (id.equals(FD.SKILLET)) {
            event.setCancelled(true);
            interactSkillet(furniture, player, held);
        } else if (id.equals(FD.ROPE)) {
            event.setCancelled(true);
            interactRope(furniture, player, held);
        } else if (isFeast(id)) {
            event.setCancelled(true);
            interactFeast(furniture, player, held);
        } else if (id.toString().endsWith("_canvas_sign") || id.toString().endsWith("_canvas_wall_sign")) {
            event.setCancelled(true);
            interactSign(furniture, player);
        }
    }

    /* ===================== dual-path fallback (Bukkit entity interact) =====================
     * CE routes furniture clicks through its own packet listener (FurnitureInteractEvent).
     * If that path is unavailable, Bukkit's PlayerInteractEntityEvent on the furniture's
     * interaction/collider entities still reaches us here. Deduplicated per player+tick. */
    private final java.util.Map<java.util.UUID, Long> lastRoute = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBukkitEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (plugin.furnitureTracker().tracked().isEmpty()) return;
        var furniture = net.momirealms.craftengine.bukkit.api.CraftEngineFurniture
                .getLoadedFurnitureByCollider(event.getRightClicked());
        if (furniture == null) {
            furniture = net.momirealms.craftengine.bukkit.api.CraftEngineFurniture
                    .getLoadedFurnitureByMetaEntity(event.getRightClicked());
        }
        if (furniture == null) return;
        plugin.getLogger().info("[PATH-B] Bukkit entity interact fallback: " + furniture.id()
                + " player=" + event.getPlayer().getName());
        long now = System.currentTimeMillis();
        Long last = lastRoute.get(event.getPlayer().getUniqueId());
        if (last != null && now - last < 200) return; // same click via both paths
        lastRoute.put(event.getPlayer().getUniqueId(), now);

        Key id = furniture.id();
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (id.equals(FD.CUTTING_BOARD)) {
            event.setCancelled(true);
            interactCuttingBoard(furniture, player, held);
        } else if (id.equals(FD.COOKING_POT)) {
            // pot GUI: let CE's native storage behavior open first (proven pipeline);
            // our listener swaps in the custom layout one tick later.
            // Only the container-serving interaction cancels here.
            interactCookingPotServeOnly(furniture, player, held, event::setCancelled);
        } else if (id.equals(FD.SKILLET)) {
            event.setCancelled(true);
            interactSkillet(furniture, player, held);
        } else if (id.equals(FD.ROPE)) {
            event.setCancelled(true);
            interactRope(furniture, player, held);
        } else if (isFeast(id)) {
            event.setCancelled(true);
            interactFeast(furniture, player, held);
        } else if (id.toString().endsWith("_canvas_sign") || id.toString().endsWith("_canvas_wall_sign")) {
            event.setCancelled(true);
            interactSign(furniture, player);
        }
    }

    /* ===================== cutting board ===================== */

    private void interactCuttingBoard(BukkitFurniture board, Player player, ItemStack held) {
        ItemStack stored = ticker().skilletItem(board);
        Location loc = board.location().clone().add(0.5, 0.2, 0.5);

        if (stored == null || stored.getType().isAir()) {
            if (held == null || held.getType().isAir()) return;
            // place item onto the board
            ItemStack one = held.clone();
            one.setAmount(1);
            ticker().setDisplayChild(board, "itemEntity", one, 0.5, 0.12, 0.5, 0.45f);
            consumeHeld(player, held);
            board.location().getWorld().playSound(loc, "minecraft:block.wood.place",
                    SoundCategory.BLOCKS, 0.8f, 1.0f);
            return;
        }

        if (held == null || held.getType().isAir()) {
            // take the stored item back
            ticker().setDisplayChild(board, "itemEntity", null, 0.5, 0.12, 0.5, 0.45f);
            giveOrDrop(player, stored);
            return;
        }

        // try cutting
        FDRecipes.CuttingRecipe recipe = plugin.recipes().matchCutting(stored, held);
        if (recipe == null) {
            // fall back: allow tool to carve display
            if (player.isSneaking() && isTool(held)) {
                ticker().setDisplayChild(board, "itemEntity", held.clone(), 0.5, 0.12, 0.5, 0.45f);
                board.location().getWorld().playSound(loc, "minecraft:block.wood.place",
                        SoundCategory.BLOCKS, 0.8f, 1.0f);
            }
            return;
        }

        int fortune = held.getEnchantmentLevel(Enchantment.FORTUNE);
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        // mod: results pop out on the LEFT of the board facing (FACING.rotateYCounterclockwise),
        // spawned 0.2 blocks towards that side at y+0.2, flying off with 0.2 horizontal speed
        Vector facing = dirOf(facingOf(board));
        Vector left = new Vector(facing.getZ(), 0, -facing.getX());
        Location dropLoc = board.location().clone()
                .add(0.5 + left.getX() * 0.2, 0.2, 0.5 + left.getZ() * 0.2);
        Vector eject = left.clone().multiply(0.2);
        for (FDRecipes.CuttingResult result : recipe.results()) {
            float chance = result.chance() + 0.1f * fortune;
            int count = 0;
            for (int i = 0; i < result.count(); i++) {
                if (rand.nextFloat() < chance) count++;
            }
            if (count <= 0) continue;
            ItemStack out = CraftEngineHook.buildItem(Key.of(result.item()));
            if (out == null) continue;
            out.setAmount(count);
            Item dropped = loc.getWorld().dropItem(dropLoc, out);
            dropped.setVelocity(eject);
        }
        // durability + sound + particles
        damageTool(player, held);
        plugin.advancements().onCuttingBoardUsed(player);
        for (FDRecipes.CuttingResult result : recipe.results()) {
            if (result.item().endsWith(":straw")) {
                plugin.advancements().onHarvestStraw(player);
                break;
            }
        }
        // mod playProcessingSound priority: recipe sound > shears > knife > wood fallback
        String sound = recipe.sound() != null ? recipe.sound()
                : held.getType() == Material.SHEARS ? "minecraft:entity.sheep.shear"
                : (FDRecipes.isKnife(held) ? FD.SND_CB_KNIFE : "minecraft:block.wood.break");
        loc.getWorld().playSound(loc, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
        spawnCuttingParticles(loc, stored, 5);
        ticker().setDisplayChild(board, "itemEntity", null, 0.5, 0.12, 0.5, 0.45f);
    }

    private boolean isTool(ItemStack stack) {
        String name = stack.getType().name().toLowerCase();
        return name.endsWith("_pickaxe") || name.endsWith("_axe") || name.endsWith("_shovel")
                || name.endsWith("_hoe") || stack.getType() == Material.SHEARS
                || stack.getType() == Material.TRIDENT || FDRecipes.isKnife(stack);
    }

    private void spawnCuttingParticles(Location loc, ItemStack item, int count) {
        loc.getWorld().spawnParticle(Particle.ITEM, loc.clone().add(0, 0.15, 0), count,
                0.2, 0.1, 0.2, 0.05, item);
    }

    /* ===================== cooking pot ===================== */

    /** Serve a meal when holding the right container; otherwise leave the event
     *  uncancelled so CE's native storage GUI opens (custom layout swaps in after). */
    private void interactCookingPotServeOnly(BukkitFurniture pot, Player player, ItemStack held,
                                             java.util.function.Consumer<Boolean> cancel) {
        if ((held == null || held.getType().isAir()) && player.isSneaking()) {
            String current = pot.currentVariant() == null ? "ground" : pot.currentVariant().name();
            String next = switch (current) {
                case "ground" -> "tray";
                case "tray" -> "handle";
                default -> "ground";
            };
            pot.setVariant(next, false);
            cancel.accept(true);
            return;
        }
        ItemStack meal = GameTicker.inv(pot)[GameTicker.SLOT_MEAL];
        if (meal != null && !meal.getType().isAir() && held != null && !held.getType().isAir()) {
            ItemStack portion = CookingPotGui.serveMeal(pot, held);
            if (portion != null) {
                giveOrDrop(player, portion);
                pot.location().getWorld().playSound(
                        pot.location().clone().add(0.5, 0.5, 0.5),
                        "minecraft:item.armor.equip_generic", SoundCategory.BLOCKS, 0.8f, 1.0f);
                cancel.accept(true);
            }
        }
    }

    private void interactCookingPot(BukkitFurniture pot, Player player, ItemStack held) {
        // sneak + empty hand cycles the support mode (ground/tray/handle)
        if ((held == null || held.getType().isAir()) && player.isSneaking()) {
            String current = pot.currentVariant() == null ? "ground" : pot.currentVariant().name();
            String next = switch (current == null ? "ground" : current) {
                case "ground" -> "tray";
                case "tray" -> "handle";
                default -> "ground";
            };
            pot.setVariant(next, false);
            return;
        }
        // serve a portion when holding the right container
        ItemStack meal = GameTicker.inv(pot)[GameTicker.SLOT_MEAL];
        if (meal != null && !meal.getType().isAir() && held != null && !held.getType().isAir()) {
            ItemStack portion = CookingPotGui.serveMeal(pot, held);
            if (portion != null) {
                giveOrDrop(player, portion);
                pot.location().getWorld().playSound(
                        pot.location().clone().add(0.5, 0.5, 0.5),
                        "minecraft:item.armor.equip_generic", SoundCategory.BLOCKS, 0.8f, 1.0f);
                return;
            }
        }
        CookingPotGui.open(plugin, pot, player);
    }

    /* ===================== skillet (placed) ===================== */

    private void interactSkillet(BukkitFurniture skillet, Player player, ItemStack held) {
        ItemStack current = ticker().skilletItem(skillet);
        if (held == null || held.getType().isAir()) return;
        ItemStack cooked = ticker().campfireResult(held);
        if (cooked == null) return;
        // mod addItemToCook: the whole held stack goes into the single skillet slot
        // (merging with a same-type stack already cooking; different types are rejected)
        boolean wasEmpty = current == null || current.getType().isAir();
        int capacity = wasEmpty ? 64 : (current.isSimilar(held) ? 64 - current.getAmount() : 0);
        int amount = Math.min(held.getAmount(), capacity);
        if (amount <= 0) return;
        ItemStack stack = held.clone();
        stack.setAmount((wasEmpty ? 0 : current.getAmount()) + amount);
        ticker().setSkilletItem(skillet, stack);
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - amount);
        }
        // fire-aspect acceleration comes from the SKILLET item itself (stored at placement)
        Integer skilletFa = GameTicker.data(skillet).get(GameTicker.fdKey("fa"), PersistentDataType.INTEGER);
        int fireAspect = skilletFa == null ? 0 : skilletFa;
        int base = ticker().campfireTime(held);
        int total = skilletCookTime(base, fireAspect);
        GameTicker.data(skillet).set(GameTicker.fdKey("cooktotal"), PersistentDataType.INTEGER, total);
        GameTicker.data(skillet).set(GameTicker.fdKey("cook"), PersistentDataType.INTEGER, 0);
        GameTicker.data(skillet).set(GameTicker.fdKey("result"), PersistentDataType.STRING,
                GameTicker.idOf(cooked));
        // mod: the add-food sound only plays when the skillet was empty AND already heated
        if (wasEmpty && ticker().isHeated(skillet.location())) {
            skillet.location().getWorld().playSound(skillet.location().clone().add(0.5, 0.5, 0.5),
                    FD.SND_SKILLET_ADD_FOOD, SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
    }

    private int skilletCookTime(int base, int fireAspect) {
        int t = (int) (base * 0.2f);
        t -= base * 0.05f * fireAspect;
        return Math.max(60, (t / 20) * 20);
    }

    /* ===================== rope ===================== */

    private void interactRope(BukkitFurniture rope, Player player, ItemStack held) {
        if (held != null && !held.getType().isAir()) return;
        if (player.isSneaking()) {
            // reel in the whole rope column
            Location loc = rope.location();
            int removed = 0;
            for (int y = loc.getBlockY(); y > loc.getWorld().getMinHeight(); y--) {
                var entry = plugin.furnitureTracker().at(
                        new Location(loc.getWorld(), loc.getBlockX(), y, loc.getBlockZ()).add(0.5, 0, 0.5), FD.ROPE);
                if (entry == null) break;
                ticker().setDisplayChild(entry.furniture(), "itemEntity", null, 0.5, 0.4, 0.5, 0.3f);
                CraftEngineHook.removeFurniture(entry.furniture(), false, false);
                removed++;
            }
            if (removed > 0) {
                ItemStack stack = CraftEngineHook.buildItem(FD.ROPE);
                if (stack != null) {
                    stack.setAmount(removed);
                    player.getInventory().addItem(stack).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
            }
            return;
        }
        // ring a bell hanging above (up to 24 blocks of rope)
        Location loc = rope.location();
        for (int y = loc.getBlockY() + 1; y <= loc.getBlockY() + 24; y++) {
            Block b = loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            if (b.getType() == Material.BELL) {
                org.bukkit.block.Bell bell = (org.bukkit.block.Bell) b.getState();
                bell.ring(player, BlockFace.DOWN);
                return;
            }
            var entry = plugin.furnitureTracker().at(
                    new Location(loc.getWorld(), loc.getBlockX(), y, loc.getBlockZ()).add(0.5, 0, 0.5), FD.ROPE);
            if (entry == null) return;
        }
    }

    /* ===================== feasts ===================== */

    private boolean isFeast(Key id) {
        return id.equals(FD.ROAST_CHICKEN_BLOCK) || id.equals(FD.STUFFED_PUMPKIN_BLOCK)
                || id.equals(FD.HONEY_GLAZED_HAM_BLOCK) || id.equals(FD.SHEPHERDS_PIE_BLOCK)
                || id.equals(FD.RICE_ROLL_MEDLEY_BLOCK);
    }

    private void interactFeast(BukkitFurniture feast, Player player, ItemStack held) {
        String variant = feast.currentVariant() == null ? "s0" : feast.currentVariant().name();
        int servings = variant.startsWith("s")
                ? Integer.parseInt(variant.substring(1)) : 0;
        if (servings <= 0) {
            // empty platter: break it
            removeSignText(feast);
            CraftEngineHook.removeFurniture(feast, false, true);
            return;
        }
        if (held == null || held.getType() != Material.BOWL) return;
        ItemStack serving = feastServing(feast.id(), servings);
        if (serving == null) return;
        held.setAmount(held.getAmount() - 1);
        giveOrDrop(player, serving);
        feast.setVariant("s" + (servings - 1), false);
        feast.location().getWorld().playSound(feast.location().clone().add(0.5, 0.4, 0.5),
                "minecraft:entity.generic.eat", SoundCategory.BLOCKS, 0.8f, 1.0f);
    }

    private ItemStack feastServing(Key feastId, int servings) {
        String id = feastId.toString();
        String item = switch (id.substring(id.indexOf(':') + 1)) {
            case "roast_chicken_block" -> "roast_chicken";
            case "stuffed_pumpkin_block" -> "stuffed_pumpkin";
            case "honey_glazed_ham_block" -> "honey_glazed_ham";
            case "shepherds_pie_block" -> "shepherds_pie";
            case "rice_roll_medley_block" -> switch (servings) {
                case 8, 7 -> "cod_roll";
                case 6, 5, 4 -> "salmon_roll";
                default -> "kelp_roll";
            };
            default -> null;
        };
        return item == null ? null : CraftEngineHook.buildItem(Key.of(FD.MOD_ID, item));
    }

    /* ===================== canvas signs ===================== */

    private void interactSign(BukkitFurniture sign, Player player) {
        plugin.signSessions().begin(player, sign);
    }

    private void removeSignText(BukkitFurniture furniture) {
        UUIDLike: {
            String s = GameTicker.data(furniture).get(GameTicker.fdKey("text"), PersistentDataType.STRING);
            if (s == null) break UUIDLike;
            try {
                Entity e = org.bukkit.Bukkit.getEntity(java.util.UUID.fromString(s));
                if (e != null) e.remove();
            } catch (IllegalArgumentException ignored) {
            }
            GameTicker.data(furniture).remove(GameTicker.fdKey("text"));
        }
    }

    /* ===================== helpers ===================== */

    public String facingOf(BukkitFurniture furniture) {
        float yaw = furniture.baseEntity().getLocation().getYaw();
        double rot = (yaw % 360 + 360) % 360;
        if (rot >= 315 || rot < 45) return "south";
        if (rot < 135) return "west";
        if (rot < 225) return "north";
        return "east";
    }

    public Vector dirOf(String facing) {
        return switch (facing == null ? "east" : facing) {
            case "north" -> new Vector(0, 0, -1);
            case "south" -> new Vector(0, 0, 1);
            case "west" -> new Vector(-1, 0, 0);
            default -> new Vector(1, 0, 0);
        };
    }

    private void consumeHeld(Player player, ItemStack held) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        held.setAmount(held.getAmount() - 1);
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void damageTool(Player player, ItemStack tool) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        tool.damage(1, player);
    }

    private List<Entity> unused() {
        return List.of();
    }
}
