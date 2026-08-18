package com.nhoryzon.mc.farmersdelight.papo.listener;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    }
}
