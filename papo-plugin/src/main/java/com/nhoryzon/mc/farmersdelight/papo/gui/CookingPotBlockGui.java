package com.nhoryzon.mc.farmersdelight.papo.gui;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Cooking pot GUI for the real CE block placements. Same slot layout as the
 * furniture-pot GUI; contents live in the chunk PDC (BlockStore field "inv").
 */
public final class CookingPotBlockGui implements InventoryHolder {

    public static final List<Integer> INPUT_SLOTS = List.of(3, 4, 5, 12, 13, 14);
    public static final int MEAL_SLOT = 21;
    public static final int CONTAINER_SLOT = 22;
    public static final int OUTPUT_SLOT = 23;
    public static final int PROGRESS_SLOT = 16;
    public static final int HEAT_SLOT = 17;
    public static final int RECIPE_BOOK_SLOT = 15;
    private static final int PROGRESS_CMD_BASE = 325001;
    private static final Map<Block, CookingPotBlockGui> OPEN = new java.util.WeakHashMap<>();

    private final FarmersDelightPlugin plugin;
    private final Block pot;
    private final Player player;
    private final Inventory inventory;

    private CookingPotBlockGui(FarmersDelightPlugin plugin, Block pot, Player player) {
        this.plugin = plugin;
        this.pot = pot;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27,
                Component.translatable("farmersdelight.container.cooking_pot"));
        ItemStack[] data = readInv();
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
        OPEN.put(pot, this);
    }

    public static void open(FarmersDelightPlugin plugin, Block pot, Player player) {
        new CookingPotBlockGui(plugin, pot, player);
    }

    public static void refreshIfHolding(Block pot) {
        CookingPotBlockGui gui = OPEN.get(pot);
        if (gui != null) gui.refresh();
    }

    private ItemStack[] readInv() {
        ItemStack[] data = plugin.blockStore().getItems(pot, "inv");
        if (data == null || data.length != 9) {
            ItemStack[] padded = new ItemStack[9];
            if (data != null) System.arraycopy(data, 0, padded, 0, Math.min(data.length, padded.length));
            data = padded;
        }
        return data;
    }

    private void saveInv(ItemStack[] data) {
        plugin.blockStore().setItems(pot, "inv", data);
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

    private void paintProgress() {
        int cook = plugin.blockStore().getInt(pot, "cook", 0);
        int total = plugin.blockStore().getInt(pot, "cooktotal", 0);
        if (cook <= 0) {
            inventory.setItem(PROGRESS_SLOT, filler());
            return;
        }
        int pct = Math.min(100, cook * 100 / Math.max(1, total));
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
        boolean heated = plugin.gameTicker().isHeated(pot.getLocation());
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

    private ItemStack filler() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.setCustomModelData(114001);
        meta.displayName(Component.empty());
        stack.setItemMeta(meta);
        return stack;
    }

    public void refresh() {
        ItemStack[] data = readInv();
        setOrAir(MEAL_SLOT, data[GameTicker.SLOT_MEAL]);
        setOrAir(CONTAINER_SLOT, data[GameTicker.SLOT_CONTAINER]);
        setOrAir(OUTPUT_SLOT, data[GameTicker.SLOT_OUTPUT]);
        paintProgress();
        paintHeat();
    }

    private void setOrAir(int slot, ItemStack stack) {
        inventory.setItem(slot, stack == null ? new ItemStack(Material.AIR) : stack);
    }

    public void saveFromGui() {
        ItemStack[] data = readInv();
        for (int i = 0; i < 6; i++) {
            data[i] = stackAt(INPUT_SLOTS.get(i));
        }
        data[GameTicker.SLOT_MEAL] = stackAt(MEAL_SLOT);
        data[GameTicker.SLOT_CONTAINER] = stackAt(CONTAINER_SLOT);
        data[GameTicker.SLOT_OUTPUT] = stackAt(OUTPUT_SLOT);
        saveInv(data);
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

    private ItemStack stackAt(int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType() == Material.BLACK_STAINED_GLASS_PANE) return null;
        if (stack.getType().isAir()) return null;
        return stack;
    }

    /** Grant stored experience at the player (mod grants on output take). */
    public void grantStoredExperience() {
        float total = plugin.gameTicker().storedExpBlock(pot);
        plugin.gameTicker().clearExpBlock(pot);
        if (total <= 0) return;
        int orbs = (int) Math.floor(total);
        float fraction = total - orbs;
        if (fraction != 0 && Math.random() < fraction) orbs++;
        if (orbs > 0) {
            var orb = player.getWorld().spawn(player.getLocation(),
                    org.bukkit.entity.ExperienceOrb.class);
            orb.setExperience(orbs);
        }
    }

    /** Take-one-meal when holding the matching container (mod useHeldItemOnMeal). */
    public ItemStack serveMeal(ItemStack held) {
        ItemStack[] data = readInv();
        ItemStack meal = data[GameTicker.SLOT_MEAL];
        if (meal == null || meal.getType().isAir()) return null;
        String containerId = plugin.blockStore().getString(pot, "container");
        boolean needsContainer = containerId != null && !containerId.equals("minecraft:air");
        if (needsContainer) {
            if (held == null || held.getType().isAir()) return null;
            String heldId = GameTicker.idOf(held);
            if (heldId == null || !heldId.equals(containerId)) return null;
            held.setAmount(held.getAmount() - 1);
        }
        ItemStack portion = meal.clone();
        portion.setAmount(1);
        meal.setAmount(meal.getAmount() - 1);
        if (meal.getAmount() <= 0) data[GameTicker.SLOT_MEAL] = null;
        saveInv(data);
        return portion;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Block pot() {
        return pot;
    }

    public static CookingPotBlockGui holderOf(Inventory inv) {
        return inv.getHolder() instanceof CookingPotBlockGui gui ? gui : null;
    }

    /** Single global click listener for every open block-pot GUI. */
    public static final class ListenerImpl implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            CookingPotBlockGui gui = holderOf(event.getView().getTopInventory());
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
            if (raw == RECIPE_BOOK_SLOT) {
                event.setCancelled(true);
                CookingPotRecipeBook.open(gui.plugin, gui.pot, gui.player);
                return;
            }
            if (raw == MEAL_SLOT || raw == PROGRESS_SLOT || raw == HEAT_SLOT) {
                event.setCancelled(true);
                if (raw == MEAL_SLOT) {
                    // UX: explain how to serve instead of failing silently
                    ItemStack meal = event.getView().getTopInventory().getItem(MEAL_SLOT);
                    ItemStack container = event.getView().getTopInventory().getItem(CONTAINER_SLOT);
                    if (meal != null && !meal.getType().isAir()
                            && (container == null || container.getType().isAir())) {
                        event.getWhoClicked().sendActionBar(Component.text(
                                "手持匹配的容器右键锅可取餐",
                                net.kyori.adventure.text.format.NamedTextColor.GRAY));
                    }
                }
                return;
            }
            if (raw == OUTPUT_SLOT) {
                ItemStack current = event.getCurrentItem();
                if (current == null || current.getType().isAir()) event.setCancelled(true);
                Bukkit.getScheduler().runTask(gui.plugin, () -> {
                    gui.saveFromGui();
                    gui.grantStoredExperience();
                    gui.refresh();
                });
                return;
            }
            ItemStack current = event.getCurrentItem();
            boolean fillerClick = (current == null || current.getType() == Material.BLACK_STAINED_GLASS_PANE
                    || current.getType() == Material.PAPER || current.getType() == Material.CAMPFIRE);
            if (fillerClick && !INPUT_SLOTS.contains(raw) && raw != CONTAINER_SLOT) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(gui.plugin, gui::saveFromGui);
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            CookingPotBlockGui gui = holderOf(event.getView().getTopInventory());
            if (gui == null) return;
            for (int raw : event.getRawSlots()) {
                if (raw < 27 && !INPUT_SLOTS.contains(raw) && raw != CONTAINER_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            Bukkit.getScheduler().runTask(gui.plugin, gui::saveFromGui);
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            CookingPotBlockGui gui = holderOf(event.getInventory());
            if (gui == null) return;
            OPEN.remove(gui.pot);
            gui.saveFromGui();
        }
    }
}
