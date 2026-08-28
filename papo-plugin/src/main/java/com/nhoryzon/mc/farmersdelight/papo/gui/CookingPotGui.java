package com.nhoryzon.mc.farmersdelight.papo.gui;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.Map;
import java.util.UUID;

/**
 * Cooking pot GUI mirroring the mod's layout:
 * row0 [_,_,in,in,in,_,_,_,_]; row1 [_,_,in,in,in,_,_,_,_]; row2 [_,_,_,meal,bowl,out,_,_,_].
 */
public final class CookingPotGui implements InventoryHolder {

    public static final List<Integer> INPUT_SLOTS = List.of(3, 4, 5, 12, 13, 14);
    public static final int MEAL_SLOT = 21;
    public static final int CONTAINER_SLOT = 22;
    public static final int OUTPUT_SLOT = 23;
    public static final int PROGRESS_SLOT = 16;
    public static final int HEAT_SLOT = 17;
    public static final int RECIPE_BOOK_SLOT = 15;

    /** Live progress stages are plain PAPER with custom model data 325001..325022 (gui.yml). */
    private static final int PROGRESS_CMD_BASE = 325001;

    /** Open GUIs keyed by the pot's base entity, so the ticker can refresh progress. */
    private static final Map<UUID, CookingPotGui> OPEN = new java.util.concurrent.ConcurrentHashMap<>();

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
        paintRecipeBookButton();
        paintProgress();
        paintHeat();
        player.openInventory(inventory);
        OPEN.put(pot.baseEntity().getUniqueId(), this);
    }

    public static void open(FarmersDelightPlugin plugin, BukkitFurniture pot, Player player) {
        new CookingPotGui(plugin, pot, player);
    }

    /** Called by the pot ticker each pulse so the progress bar and heat icon stay live. */
    public static void refreshIfHolding(BukkitFurniture pot) {
        CookingPotGui gui = OPEN.get(pot.baseEntity().getUniqueId());
        if (gui != null) {
            gui.refresh();
        }
    }

    /**
     * Shift-click smart routing: a bowl goes to the container slot, everything else
     * into the first free ingredient slot. Returns false when nothing fits.
     */
    public boolean routeIn(ItemStack moved) {
        int target = -1;
        if (moved.getType() == Material.BOWL && inventory.getItem(CONTAINER_SLOT) == null) {
            target = CONTAINER_SLOT;
        } else {
            for (int s : INPUT_SLOTS) {
                ItemStack cur = inventory.getItem(s);
                if (cur == null || cur.getType().isAir()) {
                    target = s;
                    break;
                }
            }
        }
        if (target < 0) return false;
        inventory.setItem(target, moved.clone());
        return true;
    }

    private void paintFillers() {
        ItemStack filler = filler();
        for (int i = 0; i < 27; i++) {
            if (!INPUT_SLOTS.contains(i) && i != MEAL_SLOT && i != CONTAINER_SLOT && i != OUTPUT_SLOT
                    && i != PROGRESS_SLOT && i != HEAT_SLOT && i != RECIPE_BOOK_SLOT) {
                inventory.setItem(i, filler);
            }
        }
    }

    private void paintRecipeBookButton() {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text("配方书")
                .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("查看所有烹饪锅配方")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        book.setItemMeta(meta);
        inventory.setItem(RECIPE_BOOK_SLOT, book);
    }

    private ItemStack filler() {
        // black pane carrying the mod's empty-slot sprite (farmersdelight:gui_space, CMD 114001)
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.setCustomModelData(114001);
        meta.displayName(Component.empty());
        stack.setItemMeta(meta);
        return stack;
    }

    private void paintProgress() {
        var pdc = GameTicker.data(pot);
        Integer cook = pdc.get(GameTicker.fdKey("cook"), PersistentDataType.INTEGER);
        Integer total = pdc.get(GameTicker.fdKey("cooktotal"), PersistentDataType.INTEGER);
        int cookVal = cook == null ? 0 : cook;
        int totalVal = total == null ? 0 : total;
        if (cookVal <= 0) {
            inventory.setItem(PROGRESS_SLOT, filler());
            return;
        }
        int pct = Math.min(100, cookVal * 100 / Math.max(1, totalVal));
        int stage = Math.min(21, pct * 22 / 100);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(PROGRESS_CMD_BASE + stage);
        meta.displayName(Component.text(pct + "%")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        inventory.setItem(PROGRESS_SLOT, item);
    }

    private void paintHeat() {
        boolean heated = plugin.gameTicker().isHeated(pot.location());
        ItemStack item = new ItemStack(Material.CAMPFIRE);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(114001);
        meta.displayName(Component.text(heated ? "已受热" : "下方需要热源",
                heated ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                        : net.kyori.adventure.text.format.NamedTextColor.RED)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        inventory.setItem(HEAT_SLOT, item);
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
        paintProgress();
        paintHeat();
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
        if (stack == null || stack.getType() == Material.BLACK_STAINED_GLASS_PANE) return null;
        if (stack.getType().isAir()) return null;
        return stack;
    }

    public void grantStoredExperience() {
        // mod grants stored experience at the taking player's position (floor + fractional round-up)
        plugin.gameTicker().spawnStoredExperience(pot, player.getLocation());
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
            // shift-click from the player inventory: smart-route into the pot
            // (bowls -> container slot, anything else -> first free ingredient slot)
            if (event.getClick().isShiftClick()
                    && event.getClickedInventory() == event.getView().getBottomInventory()) {
                event.setCancelled(true);
                ItemStack moved = event.getCurrentItem();
                if (moved == null || moved.getType().isAir()) return;
                if (gui.routeIn(moved)) {
                    event.getClickedInventory().setItem(event.getSlot(), null);
                    gui.saveFromGui();
                }
                return;
            }
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            int raw = event.getRawSlot();
            if (raw == MEAL_SLOT) {
                event.setCancelled(true);
                // UX: explain how to serve instead of failing silently
                ItemStack meal = event.getView().getTopInventory().getItem(MEAL_SLOT);
                ItemStack container = event.getView().getTopInventory().getItem(CONTAINER_SLOT);
                if (meal != null && !meal.getType().isAir()
                        && (container == null || container.getType().isAir())) {
                    event.getWhoClicked().sendActionBar(Component.text(
                            "手持匹配的容器右键锅可取餐", NamedTextColor.GRAY));
                }
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
            if (raw == RECIPE_BOOK_SLOT) {
                event.setCancelled(true);
                CookingPotRecipeBook.open(plugin, gui.pot, gui.player);
                return;
            }
            boolean fillerClick = (current == null || current.getType() == Material.BLACK_STAINED_GLASS_PANE
                    || current.getType() == Material.PAPER || current.getType() == Material.CAMPFIRE)
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
            OPEN.remove(gui.pot.baseEntity().getUniqueId());
            gui.saveFromGui();
        }
    }
}
