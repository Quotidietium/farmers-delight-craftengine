package com.nhoryzon.mc.farmersdelight.papo.gui;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Cooking pot GUI mirroring the mod's layout:
 * row0 [_,_,in,in,in,_,_,_,_]; row1 [_,_,in,in,in,_,_,_,_]; row2 [_,_,_,meal,bowl,out,_,_,_].
 */
public final class CookingPotGui implements InventoryHolder {

    public static final List<Integer> INPUT_SLOTS = List.of(3, 4, 5, 12, 13, 14);
    public static final int MEAL_SLOT = 21;
    public static final int CONTAINER_SLOT = 22;
    public static final int OUTPUT_SLOT = 23;

    private final FarmersDelightPlugin plugin;
    private final BukkitFurniture pot;
    private final Player player;
    private final Inventory inventory;

    private CookingPotGui(FarmersDelightPlugin plugin, BukkitFurniture pot, Player player) {
        this.plugin = plugin;
        this.pot = pot;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27,
                Component.translatable("farmersdelight.container.cooking_pot"));
        ItemStack[] data = GameTicker.inv(pot);
        for (int i = 0; i < 6; i++) {
            if (data[i] != null) inventory.setItem(INPUT_SLOTS.get(i), data[i]);
        }
        if (data[GameTicker.SLOT_MEAL] != null) inventory.setItem(MEAL_SLOT, data[GameTicker.SLOT_MEAL]);
        if (data[GameTicker.SLOT_CONTAINER] != null) inventory.setItem(CONTAINER_SLOT, data[GameTicker.SLOT_CONTAINER]);
        if (data[GameTicker.SLOT_OUTPUT] != null) inventory.setItem(OUTPUT_SLOT, data[GameTicker.SLOT_OUTPUT]);
        paintFillers();
        player.openInventory(inventory);
    }

    public static void open(FarmersDelightPlugin plugin, BukkitFurniture pot, Player player) {
        new CookingPotGui(plugin, pot, player);
    }

    private void paintFillers() {
        ItemStack filler = filler();
        for (int i = 0; i < 27; i++) {
            if (!INPUT_SLOTS.contains(i) && i != MEAL_SLOT && i != CONTAINER_SLOT && i != OUTPUT_SLOT) {
                inventory.setItem(i, filler);
            }
        }
    }

    private ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.empty());
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public BukkitFurniture pot() {
        return pot;
    }

    public Player player() {
        return player;
    }

    public void refresh() {
        ItemStack[] data = GameTicker.inv(pot);
        setOrAir(MEAL_SLOT, data[GameTicker.SLOT_MEAL]);
        setOrAir(CONTAINER_SLOT, data[GameTicker.SLOT_CONTAINER]);
        setOrAir(OUTPUT_SLOT, data[GameTicker.SLOT_OUTPUT]);
    }

    private void setOrAir(int slot, ItemStack stack) {
        inventory.setItem(slot, stack == null ? new ItemStack(Material.AIR) : stack);
    }

    public static CookingPotGui holderOf(Inventory inv) {
        return inv.getHolder() instanceof CookingPotGui gui ? gui : null;
    }

    public void saveFromGui() {
        ItemStack[] data = GameTicker.inv(pot);
        for (int i = 0; i < 6; i++) {
            data[i] = stackAt(INPUT_SLOTS.get(i));
        }
        data[GameTicker.SLOT_MEAL] = stackAt(MEAL_SLOT);
        data[GameTicker.SLOT_CONTAINER] = stackAt(CONTAINER_SLOT);
        data[GameTicker.SLOT_OUTPUT] = stackAt(OUTPUT_SLOT);
        GameTicker.saveInv(pot, data);
    }

    private ItemStack stackAt(int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType() == Material.GRAY_STAINED_GLASS_PANE) return null;
        if (stack.getType().isAir()) return null;
        return stack;
    }

    public void grantStoredExperience() {
        var pdc = GameTicker.data(pot);
        String exp = pdc.get(GameTicker.fdKey("exp"), PersistentDataType.STRING);
        if (exp == null || exp.isEmpty()) return;
        float total = 0;
        for (String part : exp.split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            int count = Integer.parseInt(kv[1]);
            var recipe = plugin.recipes().cooking.stream()
                    .filter(r -> r.id().equals(kv[0])).findFirst().orElse(null);
            if (recipe != null) {
                total += recipe.experience() * count;
            }
        }
        if (total > 0) {
            int orbs = Math.max(1, Math.round(total));
            var loc = pot.location();
            var world = loc.getWorld();
            if (world != null) {
                var orb = world.spawn(loc.add(0.5, 0.8, 0.5), org.bukkit.entity.ExperienceOrb.class);
                orb.setExperience(orbs);
            }
        }
        pdc.set(GameTicker.fdKey("exp"), PersistentDataType.STRING, "");
    }

    /** Take-one-meal action when right clicking the pot with a valid container. */
    public static ItemStack serveMeal(BukkitFurniture pot, ItemStack container) {
        ItemStack[] inv = GameTicker.inv(pot);
        ItemStack meal = inv[GameTicker.SLOT_MEAL];
        if (meal == null || meal.getType().isAir()) return null;
        var pdc = GameTicker.data(pot);
        String containerId = pdc.get(GameTicker.fdKey("container"), PersistentDataType.STRING);
        boolean needsContainer = containerId != null && !containerId.equals("minecraft:air");
        if (needsContainer) {
            if (container == null || container.getType().isAir()) return null;
            String heldId = GameTicker.idOf(container);
            if (heldId == null || !heldId.equals(containerId)) return null;
            container.setAmount(container.getAmount() - 1);
        }
        ItemStack portion = meal.clone();
        portion.setAmount(1);
        meal.setAmount(meal.getAmount() - 1);
        if (meal.getAmount() <= 0) inv[GameTicker.SLOT_MEAL] = null;
        GameTicker.saveInv(pot, inv);
        return portion;
    }

    /** Single global listener handling clicks for every open pot GUI. */
    public static final class ListenerImpl implements Listener {

        private final FarmersDelightPlugin plugin;

        public ListenerImpl(FarmersDelightPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            CookingPotGui gui = holderOf(event.getView().getTopInventory());
            if (gui == null) return;
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            int raw = event.getRawSlot();
            if (raw == MEAL_SLOT) {
                event.setCancelled(true);
                return;
            }
            if (raw == OUTPUT_SLOT) {
                ItemStack current = event.getCurrentItem();
                if (current == null || current.getType().isAir()) {
                    event.setCancelled(true);
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    gui.saveFromGui();
                    gui.grantStoredExperience();
                    gui.refresh();
                });
                return;
            }
            if (raw == CONTAINER_SLOT) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir()
                        && cursor.getType() != Material.BOWL && cursor.getType() != Material.GLASS_BOTTLE) {
                    event.setCancelled(true);
                    return;
                }
            }
            ItemStack current = event.getCurrentItem();
            boolean fillerClick = (current == null || current.getType() == Material.GRAY_STAINED_GLASS_PANE)
                    && !INPUT_SLOTS.contains(raw) && raw != CONTAINER_SLOT;
            if (fillerClick) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                gui.saveFromGui();
                gui.refresh();
            });
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            CookingPotGui gui = holderOf(event.getView().getTopInventory());
            if (gui == null) return;
            for (int raw : event.getRawSlots()) {
                if (raw < 27 && !INPUT_SLOTS.contains(raw) && raw != CONTAINER_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            Bukkit.getScheduler().runTask(plugin, gui::saveFromGui);
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            CookingPotGui gui = holderOf(event.getInventory());
            if (gui == null) return;
            gui.saveFromGui();
        }
    }
}
