package com.nhoryzon.mc.farmersdelight.papo.listener;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotGui;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Diagnostic logging: every GUI open (plugin-custom AND CE-native, since CE's
 * BukkitInventory.open routes through InventoryOpenEvent) is logged so one
 * right-click by a tester shows exactly which path fired and what opened.
 */
public final class GuiDiagnosticListener implements Listener {

    private final FarmersDelightPlugin plugin;

    public GuiDiagnosticListener(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        plugin.getLogger().info("[GUI] opened for " + event.getPlayer().getName()
                + " title='" + title + "' holder=" + (event.getInventory().getHolder() != null
                ? event.getInventory().getHolder().getClass().getSimpleName() : "null")
                + " size=" + event.getInventory().getSize());

        // Pot custom layout: CE's native simple_storage GUI opens first through CE's
        // own proven pipeline; one tick later we swap to the custom slot layout
        // backed by the very same CE inventory.
        if (!(event.getPlayer() instanceof Player player)) return;
        Object holder = event.getInventory().getHolder();
        if (!(holder instanceof net.momirealms.craftengine.bukkit.world.WorldlyContainerHolder wch)) return;
        try {
            var pos = wch.pos();
            if (pos == null || pos.world == null) return;
            Location loc = new Location(((org.bukkit.World) pos.world.platformWorld()),
                    pos.x(), pos.y(), pos.z());
            var entry = plugin.furnitureTracker().at(loc, FD.COOKING_POT);
            if (entry == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                // only swap if the native storage view is still what the player sees
                if (player.getOpenInventory().getTopInventory().getHolder() == wch) {
                    player.closeInventory();
                    CookingPotGui.open(plugin, entry.furniture(), player);
                    plugin.getLogger().info("[GUI] pot layout swapped for " + player.getName());
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
