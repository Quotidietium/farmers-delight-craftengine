package com.nhoryzon.mc.farmersdelight.papo.gui;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Generic 27-slot container GUI for baskets and cabinets backed by block PDC data. */
public final class ContainerBlockGui implements InventoryHolder {

    private final Block block;
    private final Component title;
    private final Inventory inventory;

    private ContainerBlockGui(Block block, Component title, ItemStack[] contents) {
        this.block = block;
        this.title = title;
        this.inventory = Bukkit.createInventory(this, 27, title);
        for (int i = 0; i < 27 && i < contents.length; i++) {
            if (contents[i] != null) inventory.setItem(i, contents[i]);
        }
    }

    public static void open(FarmersDelightPlugin plugin, Block block, Key blockId, Player player) {
        ItemStack[] contents = plugin.blockStore().getItems(block, "inv");
        if (contents == null) contents = new ItemStack[27];
        Component title = Component.translatable("block." + blockId.toString().replace(":", "."));
        ContainerBlockGui gui = new ContainerBlockGui(block, title, contents);
        player.openInventory(gui.inventory);
    }

    public static ContainerBlockGui holderOf(Inventory inv) {
        return inv.getHolder() instanceof ContainerBlockGui gui ? gui : null;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Block block() {
        return block;
    }

    public void save(FarmersDelightPlugin plugin) {
        ItemStack[] out = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack s = inventory.getItem(i);
            out[i] = (s == null || s.getType().isAir()) ? null : s;
        }
        plugin.blockStore().setItems(block, "inv", out);
    }

    public static final class ListenerImpl implements Listener {

        private final FarmersDelightPlugin plugin;

        public ListenerImpl(FarmersDelightPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (holderOf(event.getInventory()) instanceof ContainerBlockGui gui) {
                gui.save(plugin);
                // close cabinet doors again
                var state = com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook.customBlockState(gui.block());
                if (state != null) {
                    String id = state.owner().value().id().toString();
                    if (id.endsWith("_cabinet")) {
                        plugin.gameTicker().setBlockProperty(gui.block(), "open", false);
                        gui.block().getWorld().playSound(gui.block().getLocation().add(0.5, 0.5, 0.5),
                                "minecraft:block.bamboo_wood.close", org.bukkit.SoundCategory.BLOCKS, 0.7f, 1.0f);
                    }
                }
            }
        }
    }
}
