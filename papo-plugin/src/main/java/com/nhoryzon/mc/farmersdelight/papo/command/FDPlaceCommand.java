package com.nhoryzon.mc.farmersdelight.papo.command;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Console debug command: /fdplace <furnitureId> <x> <y> <z> [world] */
public final class FDPlaceCommand extends Command {

    public FDPlaceCommand(@NotNull String name) {
        super(name, "Debug place FD furniture", "/fdplace <id> <x> <y> <z> [world]",
                java.util.List.of());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 4) {
            sender.sendMessage("usage: /fdplace <furnitureId> <x> <y> <z> [world]");
            return true;
        }
        try {
            String id = args[0];
            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);
            World world = args.length >= 5 ? Bukkit.getWorld(args[4]) : Bukkit.getWorlds().getFirst();
            if (world == null) {
                sender.sendMessage("world not found");
                return true;
            }
            Location loc = new Location(world, x, y, z);
            if (id.startsWith("block:")) {
                String blockId = id.substring(6);
                boolean ok = CraftEngineHook.placeBlock(loc, FD.key(blockId), true);
                sender.sendMessage("placeBlock " + blockId + " -> " + ok);
                if (ok) {
                    org.bukkit.block.Block b = loc.getBlock();
                    var inv = FarmersDelightPlugin.get().gameTicker().ceStorageInventory(b);
                    sender.sendMessage("  storage inventory: " + (inv == null ? "NULL" : inv.getSize() + " slots"));
                }
                return true;
            }
            BukkitFurniture furniture = CraftEngineHook.placeFurniture(loc, FD.key(id));
            if (furniture == null) {
                sender.sendMessage("FAILED to place furniture " + id + " (null)");
                return true;
            }
            FarmersDelightPlugin.get().furnitureTracker().track(furniture.baseEntity());
            var ceInv = com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker.ceFurnitureInventory(furniture);
            if (ceInv != null) {
                sender.sendMessage("  CE furniture storage: " + ceInv.getSize() + " slots");
            }
            var base = furniture.baseEntity();
            sender.sendMessage("placed " + id + " base=" + base.getUniqueId()
                    + " type=" + base.getType() + " loc=" + base.getLocation().toVector());
            base.getPassengers().forEach(p -> sender.sendMessage("  passenger: " + p.getType() + " " + p.getUniqueId()));
            world.getNearbyEntities(loc, 2, 2, 2).forEach(e ->
                    sender.sendMessage("  nearby: " + e.getType() + " " + e.getUniqueId()));
            return true;
        } catch (Exception e) {
            sender.sendMessage("error: " + e.getMessage());
            return true;
        }
    }
}
