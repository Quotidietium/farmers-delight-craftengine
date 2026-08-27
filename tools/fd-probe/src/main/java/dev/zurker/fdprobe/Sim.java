package dev.zurker.fdprobe;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** 合成交互：服务端直接构造事件/调用 CE API，毫秒级触发 REF 插件全流程（配合 fdprobe arm 捕获）。 */
public final class Sim {

    private Sim() {}

    public static boolean place(String ceId, int x, int y, int z) {
        Block b = Bukkit.getWorlds().getFirst().getBlockAt(x, y, z);
        return CraftEngineBlocks.place(b.getLocation(), Key.of(ceId), true);
    }

    public static ItemStack item(String id, int count) {
        if (id.equals("empty")) return new ItemStack(Material.AIR);
        try {
            var def = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(Key.of(id));
            ItemStack is = def == null ? null : def.buildBukkitItem((org.bukkit.entity.Player) null);
            if (is != null && !is.getType().isAir()) {
                is.setAmount(Math.max(1, count));
                return is;
            }
        } catch (Throwable ignored) {}
        Material m = Material.matchMaterial(id);
        if (m == null) return null;
        return new ItemStack(m, Math.max(1, count));
    }

    public static String setHand(Player p, String id, int count) {
        ItemStack is = item(id, count);
        if (is == null) return "unknown item: " + id;
        p.getInventory().setItemInMainHand(is);
        return "hand=" + is.getType() + "x" + is.getAmount();
    }

    public static String give(Player p, String id, int count) {
        ItemStack is = item(id, count);
        if (is == null) return "unknown item: " + id;
        p.getInventory().addItem(is);
        return "gave " + is.getType() + "x" + is.getAmount();
    }

    private static BlockFace face(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "top", "up" -> BlockFace.UP;
            case "bottom", "down" -> BlockFace.DOWN;
            case "north" -> BlockFace.NORTH;
            case "south" -> BlockFace.SOUTH;
            case "west" -> BlockFace.WEST;
            case "east" -> BlockFace.EAST;
            default -> BlockFace.UP;
        };
    }

    /** 合成右键/左键方块事件并广播 —— 走完整 Bukkit 监听链（CE/REF 的入口） */
    public static String interact(Player p, int x, int y, int z, String faceStr, boolean sneak, boolean leftClick) {
        Block b = Bukkit.getWorlds().getFirst().getBlockAt(x, y, z);
        Action action = leftClick ? Action.LEFT_CLICK_BLOCK : Action.RIGHT_CLICK_BLOCK;
        if (sneak) p.setSneaking(true);
        ItemStack hand = p.getInventory().getItemInMainHand();
        PlayerInteractEvent evt = new PlayerInteractEvent(p, action, hand, b, face(faceStr), EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(evt);
        if (sneak) p.setSneaking(false);
        return "interact fired: " + action + " @ " + x + "," + y + "," + z + " block=" + b.getType()
                + " hand=" + (hand == null ? "empty" : hand.getType())
                + " useBlock=" + evt.useInteractedBlock() + " useItem=" + evt.useItemInHand();
    }

    /** 合成实体右键事件（CE 家具 = item_display/interaction 实体路径） */
    public static String useEntity(Player p, String selectorOrId) {
        Entity target = null;
        try {
            int id = Integer.parseInt(selectorOrId);
            for (Entity e : p.getWorld().getEntities()) {
                if (e.getEntityId() == id) { target = e; break; }
            }
        } catch (NumberFormatException ex) {
            for (Entity e : p.getWorld().getEntities()) {
                if (selectorOrId.equals(e.getCustomName() != null ? e.getCustomName().toString() : null)) { target = e; break; }
            }
        }
        if (target == null) {
            // 退化为：玩家 8 格内最近的实体
            Entity nearest = null;
            double best = 8;
            for (Entity e : p.getNearbyEntities(8, 8, 8)) {
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < best) { best = d; nearest = e; }
            }
            target = nearest;
        }
        if (target == null) return "no entity found";
        PlayerInteractEntityEvent evt = new PlayerInteractEntityEvent(p, target, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(evt);
        return "entity interact fired: " + target.getType() + "#" + target.getEntityId() + " @" + target.getLocation().toVector();
    }
}
