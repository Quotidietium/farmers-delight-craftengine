package com.nhoryzon.mc.farmersdelight.papo.gui;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Cooking pot recipe book (mod GUI parity): a paged list of every cooking recipe
 * with a detail view per recipe, reachable from the pot GUI and able to jump back.
 */
public final class CookingPotRecipeBook implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_BACK_POT_LIST = 52;
    private static final int D_SLOT_BACK = 18;
    private static final int D_SLOT_BACK_POT = 16;
    private static final int D_SLOT_RESULT = 4;
    private static final int D_SLOT_INFO = 22;
    private static final int D_SLOT_CONTAINER = 24;
    private static final List<Integer> D_INGREDIENT_SLOTS = List.of(10, 11, 12, 13, 14, 15);

    private final FarmersDelightPlugin plugin;
    private final BukkitFurniture pot;
    private final Player player;
    private final List<FDRecipes.CookingRecipe> recipes;
    private int page;
    private Inventory inventory;
    private boolean detail;
    private FDRecipes.CookingRecipe selected;

    private CookingPotRecipeBook(FarmersDelightPlugin plugin, BukkitFurniture pot, Player player) {
        this.plugin = plugin;
        this.pot = pot;
        this.player = player;
        this.recipes = plugin.recipes().cooking;
        openList(0);
    }

    public static void open(FarmersDelightPlugin plugin, BukkitFurniture pot, Player player) {
        new CookingPotRecipeBook(plugin, pot, player);
    }

    /* ---------------- list view ---------------- */

    private void openList(int page) {
        this.detail = false;
        this.page = Math.max(0, Math.min(page, maxPage()));
        int pages = maxPage() + 1;
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("烹饪锅配方").color(NamedTextColor.GOLD));
        ItemStack filler = filler();
        int index = this.page * PAGE_SIZE;
        for (int i = 0; i < 54; i++) {
            if (i >= SLOT_PREV) {
                inventory.setItem(i, filler);
                continue;
            }
            if (index < recipes.size()) {
                ItemStack icon = iconFor(recipes.get(index));
                if (icon != null) {
                    inventory.setItem(i, icon);
                    index++;
                    continue;
                }
                index++;
            }
            inventory.setItem(i, filler);
        }
        // navigation buttons
        if (this.page > 0) {
            inventory.setItem(SLOT_PREV, button(Material.ARROW, "上一页",
                    Component.text((this.page) + "/" + pages).color(NamedTextColor.GRAY)));
        }
        inventory.setItem(SLOT_BACK_POT_LIST, button(Material.CAMPFIRE, "返回厨锅", null));
        if (this.page < maxPage()) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW, "下一页",
                    Component.text((this.page + 2) + "/" + pages).color(NamedTextColor.GRAY)));
        }
        player.openInventory(inventory);
    }

    private ItemStack iconFor(FDRecipes.CookingRecipe recipe) {
        ItemStack icon = CraftEngineHook.buildItem(Key.of(recipe.result()));
        if (icon == null) return null;
        icon = icon.clone();
        icon.setAmount(Math.max(1, recipe.resultCount()));
        var meta = icon.getItemMeta();
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("原料:").color(NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        for (var group : recipe.ingredientGroups()) {
            String first = group.isEmpty() ? "?" : group.iterator().next();
            ItemStack ing = CraftEngineHook.buildItem(Key.of(first));
            Component name = ing == null ? Component.text(first)
                    : ing.effectiveName().colorIfAbsent(NamedTextColor.WHITE);
            lore.add(Component.text(" • ").color(NamedTextColor.DARK_GRAY).append(name)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        if (recipe.container() != null) {
            lore.add(Component.text("需盛装: ").color(NamedTextColor.GRAY)
                    .append(Component.text(recipe.container()).color(NamedTextColor.WHITE))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("耗时 " + (recipe.cookTime() / 20) + " 秒")
                .color(NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /* ---------------- detail view ---------------- */

    private void openDetail(FDRecipes.CookingRecipe recipe) {
        this.detail = true;
        this.selected = recipe;
        this.inventory = Bukkit.createInventory(this, 27,
                Component.text("配方详情").color(NamedTextColor.GOLD));
        ItemStack filler = filler();
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);
        List<Component> info = List.of(
                Component.text("耗时: " + (recipe.cookTime() / 20) + " 秒").color(NamedTextColor.GRAY),
                Component.text("经验: " + recipe.experience()).color(NamedTextColor.GRAY));
        inventory.setItem(D_SLOT_INFO, button(Material.PAPER, "烹饪信息", null, info));
        ItemStack result = CraftEngineHook.buildItem(Key.of(recipe.result()));
        if (result != null) {
            result.setAmount(Math.max(1, recipe.resultCount()));
            inventory.setItem(D_SLOT_RESULT, result);
        }
        if (recipe.container() != null) {
            ItemStack container = CraftEngineHook.buildItem(Key.of(recipe.container()));
            if (container != null) inventory.setItem(D_SLOT_CONTAINER, container);
        }
        int slot = 0;
        for (var group : recipe.ingredientGroups()) {
            if (slot >= D_INGREDIENT_SLOTS.size()) break;
            String first = group.isEmpty() ? null : group.iterator().next();
            ItemStack ing = first == null ? null : CraftEngineHook.buildItem(Key.of(first));
            if (ing != null) {
                inventory.setItem(D_INGREDIENT_SLOTS.get(slot), ing);
                slot++;
            }
        }
        inventory.setItem(D_SLOT_BACK, button(Material.ARROW, "返回列表", null));
        inventory.setItem(D_SLOT_BACK_POT, button(Material.CAMPFIRE, "返回厨锅", null));
        player.openInventory(inventory);
    }

    /* ---------------- shared ---------------- */

    private int maxPage() {
        return Math.max(0, (recipes.size() - 1) / PAGE_SIZE);
    }

    private ItemStack filler() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var meta = stack.getItemMeta();
        meta.setCustomModelData(114001);
        meta.displayName(Component.empty());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack button(Material material, String name, Component suffix) {
        return button(material, name, suffix, null);
    }

    private ItemStack button(Material material, String name, Component suffix, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(Component.text(name).color(NamedTextColor.YELLOW)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        if (suffix != null) {
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(suffix);
            meta.lore(lines);
        } else if (lore != null) {
            meta.lore(lore);
        }
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

    public static CookingPotRecipeBook holderOf(Inventory inv) {
        return inv.getHolder() instanceof CookingPotRecipeBook book ? book : null;
    }

    /** Single global click listener for every open recipe book. */
    public static final class ListenerImpl implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            CookingPotRecipeBook book = holderOf(event.getView().getTopInventory());
            if (book == null) return;
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            int raw = event.getRawSlot();
            if (book.detail) {
                if (raw == D_SLOT_BACK) {
                    book.openList(book.page);
                } else if (raw == D_SLOT_BACK_POT) {
                    CookingPotGui.open(book.plugin, book.pot, book.player);
                }
                return;
            }
            if (raw == SLOT_PREV && book.page > 0) {
                book.openList(book.page - 1);
            } else if (raw == SLOT_NEXT && book.page < book.maxPage()) {
                book.openList(book.page + 1);
            } else if (raw == SLOT_BACK_POT_LIST) {
                CookingPotGui.open(book.plugin, book.pot, book.player);
            } else if (raw >= 0 && raw < PAGE_SIZE) {
                int index = book.page * PAGE_SIZE + raw;
                if (index < book.recipes.size()) {
                    book.openDetail(book.recipes.get(index));
                }
            }
        }
    }
}
