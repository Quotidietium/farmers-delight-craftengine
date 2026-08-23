package dev.zurker.fdprobe;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/** 事件追踪：以 MONITOR 顺序记录 FD 相关事件的可观测行为（开窗/实体生成/掉落/伤害/交互）。 */
public class EventTracer implements Listener {

    private final FdProbePlugin plugin;

    public EventTracer(FdProbePlugin plugin) { this.plugin = plugin; }

    private void ev(String name, Object... kvs) {
        StringBuilder sb = new StringBuilder(name);
        for (int i = 0; i + 1 < kvs.length; i += 2) {
            sb.append(' ').append(kvs[i]).append('=').append(shrunk(kvs[i + 1]));
        }
        plugin.ev(sb.toString());
    }

    private String shrunk(Object o) {
        if (o == null) return "null";
        if (o instanceof Block b) return b.getX() + "," + b.getY() + "," + b.getZ() + "/" + b.getType();
        if (o instanceof Entity e) return e.getType() + "#" + e.getEntityId() + "@" + e.getLocation().getBlockX() + "," + e.getLocation().getBlockY() + "," + e.getLocation().getBlockZ();
        if (o instanceof Player p) return p.getName();
        if (o instanceof ItemStack is) return is.getType() + "x" + is.getAmount();
        if (o instanceof Inventory inv) return "inv(" + inv.getSize() + ")";
        String s = String.valueOf(o);
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private String hand(org.bukkit.inventory.EquipmentSlot s) { return s == null ? "-" : s.name(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        ev("INTERACT", "player", e.getPlayer(), "action", e.getAction(), "hand", hand(e.getHand()),
                "item", e.getItem(), "block", e.getClickedBlock(), "useBlock", e.useInteractedBlock(), "useItem", e.useItemInHand());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        ev("INTERACT_ENTITY", "player", e.getPlayer(), "target", e.getRightClicked(), "hand", hand(e.getHand()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent e) { ev("SNEAK", "player", e.getPlayer(), "state", e.isSneaking()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnim(PlayerAnimationEvent e) { ev("ANIM", "player", e.getPlayer(), "type", e.getAnimationType()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvOpen(InventoryOpenEvent e) {
        ev("INV_OPEN", "player", e.getPlayer(), "inv", e.getInventory(),
                "title", titleOf(e), "slots", e.getInventory().getSize());
    }

    private String titleOf(InventoryOpenEvent e) {
        try { return String.valueOf(e.getView().title()); } catch (Throwable t) { return "?"; }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvClick(InventoryClickEvent e) {
        ev("INV_CLICK", "player", e.getWhoClicked(), "slot", e.getSlot(), "raw", e.getRawSlot(),
                "click", e.getClick(), "cursor", e.getCursor(), "current", e.getCurrentItem(),
                "invType", e.getInventory().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvClose(InventoryCloseEvent e) { ev("INV_CLOSE", "player", e.getPlayer(), "inv", e.getInventory()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInvDrag(InventoryDragEvent e) { ev("INV_DRAG", "player", e.getWhoClicked(), "slots", e.getRawSlots(), "old", e.getOldCursor()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) { ev("BLOCK_PLACE", "player", e.getPlayer(), "block", e.getBlock(), "item", e.getItemInHand(), "canBuild", e.canBuild()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) { ev("BLOCK_BREAK", "player", e.getPlayer(), "block", e.getBlock(), "drops", e.isDropItems()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamage(BlockDamageEvent e) { ev("BLOCK_DAMAGE", "player", e.getPlayer(), "block", e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDrop(BlockDropItemEvent e) {
        List<Item> items = e.getItems();
        StringBuilder sb = new StringBuilder();
        for (Item it : items) sb.append(it.getItemStack().getType()).append('x').append(it.getItemStack().getAmount()).append(' ');
        ev("BLOCK_DROP", "player", e.getPlayer(), "block", e.getBlock(), "items", sb.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(EntitySpawnEvent e) { ev("SPAWN", "entity", e.getEntity(), "loc", e.getLocation().toVector()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent e) { ev("ITEM_SPAWN", "entity", e.getEntity(), "item", e.getEntity().getItemStack()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDespawn(ItemDespawnEvent e) { ev("ITEM_DESPAWN", "entity", e.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsume(PlayerItemConsumeEvent e) { ev("CONSUME", "player", e.getPlayer(), "item", e.getItem()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFood(FoodLevelChangeEvent e) { ev("FOOD", "entity", e.getEntity(), "from", e.getFoodLevel(), "item", e.getItem()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        ev("DAMAGE", "entity", e.getEntity(), "cause", e.getCause(), "dmg", e.getFinalDamage());
        if (e instanceof EntityDamageByEntityEvent by) ev("DAMAGE_BY", "src", by.getDamager(), "target", by.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegain(EntityRegainHealthEvent e) { ev("REGAIN", "entity", e.getEntity(), "amt", e.getAmount(), "reason", e.getRegainReason()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { ev("JOIN", "player", e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { ev("QUIT", "player", e.getPlayer()); }

    @SuppressWarnings("unused")
    private static void unusedReflect(Method m) { /* keep import used pattern for future extractors */ }
}
