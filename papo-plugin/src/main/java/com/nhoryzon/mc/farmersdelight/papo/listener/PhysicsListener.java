package com.nhoryzon.mc.farmersdelight.papo.listener;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Physics-flavoured mechanics: safety net bounce, rice bale soft landings,
 * stove step damage, vanilla composter filling with FD compostables and
 * signal smoke for campfires burning above bales.
 */
public final class PhysicsListener implements Listener {

    private final FarmersDelightPlugin plugin;
    // campfires sitting above bales -> signal smoke compensation
    private final Map<Location, Boolean> signalCampfires = new ConcurrentHashMap<>();

    public PhysicsListener(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickSignalSmoke, 40L, 40L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickStoveBurn, 10L, 10L);
    }

    /* ===================== fall damage modifiers ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Entity entity = event.getEntity();
        Location loc = entity.getLocation();

        // safety net: cancel damage and bounce
        var net = plugin.furnitureTracker().at(loc.getBlock().getLocation().add(0.5, 0, 0.5), FD.SAFETY_NET);
        if (net == null) {
            Block below = loc.getBlock().getRelative(BlockFace.DOWN);
            var belowNet = plugin.furnitureTracker().at(below.getLocation().add(0.5, 0, 0.5), FD.SAFETY_NET);
            net = belowNet;
        }
        if (net != null) {
            event.setCancelled(true);
            Vector velocity = entity.getVelocity();
            double factor = entity instanceof Player ? 0.6 : 0.8;
            entity.setVelocity(new Vector(velocity.getX() * 0.5, Math.abs(velocity.getY()) * factor + 0.4,
                    velocity.getZ() * 0.5));
            return;
        }

        // rice bale softens landings to 20% (straw/hay bales already do this natively)
        Block landing = loc.getBlock().getRelative(BlockFace.DOWN);
        var state = CraftEngineHook.customBlockState(landing);
        if (state != null) {
            String id = state.owner().value().id().toString();
            if (id.equals("farmersdelight:rice_bale")) {
                event.setDamage(event.getDamage() * 0.2f);
            }
        }
    }

    /* ===================== stove burns bare feet ===================== */

    private void tickStoveBurn() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Block ground = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
            var state = CraftEngineHook.customBlockState(ground);
            if (state == null) continue;
            if (!state.owner().value().id().equals(FD.STOVE)) continue;
            Boolean lit = plugin.gameTicker().getBool(state, "lit");
            if (lit == null || !lit) continue;
            if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)
                    || player.getLocation().getY() - ground.getY() > 1.2) continue;
            // ice walker / frost protection is not queryable; keep simple burn like the mod
            player.damage(1.0);
            player.getWorld().playSound(player.getLocation(), "minecraft:block.fire.extinguish",
                    SoundCategory.PLAYERS, 0.3f, 1.4f);
        }
    }

    /* ===================== vanilla composter accepts FD items ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onComposterInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.COMPOSTER) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack held = event.getItem();
        if (held == null || held.getType().isAir()) return;
        Float chance = plugin.compostables().get(plugin.gameTicker().idOf(held));
        if (chance == null) return;
        if (!(block.getBlockData() instanceof Levelled composter)) return;
        if (composter.getLevel() >= composter.getMaximumLevel()) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        if (Math.random() < chance) {
            composter.setLevel(composter.getLevel() + 1);
            block.setBlockData(composter);
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                    "minecraft:block.composter.fill", SoundCategory.BLOCKS, 0.8f, 1.0f);
        } else {
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                    "minecraft:block.composter.fill_failed", SoundCategory.BLOCKS, 0.8f, 1.0f);
        }
    }

    /* ===================== campfire signal smoke above bales ===================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCustomBalePlace(net.momirealms.craftengine.bukkit.api.event.CustomBlockPlaceEvent event) {
        String id = event.blockState().owner().value().id().toString();
        if (id.endsWith("rice_bale") || id.endsWith("straw_bale")) {
            trackIfSignal(event.bukkitBlock().getRelative(BlockFace.UP));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.CAMPFIRE) {
            trackIfSignal(event.getBlock());
        }
    }

    private void trackIfSignal(Block campfire) {
        if (campfire == null || campfire.getType() != Material.CAMPFIRE) return;
        Block below = campfire.getRelative(BlockFace.DOWN);
        var state = CraftEngineHook.customBlockState(below);
        boolean bale = state != null && (state.owner().value().id().toString().endsWith("rice_bale")
                || state.owner().value().id().toString().endsWith("straw_bale"));
        Location key = campfire.getLocation();
        if (bale) {
            signalCampfires.put(key, Boolean.TRUE);
        } else {
            signalCampfires.remove(key);
        }
    }

    private void tickSignalSmoke() {
        for (Location loc : List.copyOf(signalCampfires.keySet())) {
            Block campfire = loc.getBlock();
            if (campfire.getType() != Material.CAMPFIRE
                    || !(campfire.getBlockData() instanceof org.bukkit.block.data.type.Campfire cf)
                    || !cf.isLit()) {
                signalCampfires.remove(loc);
                continue;
            }
            // large signal smoke rises much higher than regular campfire smoke
            campfire.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE,
                    loc.clone().add(0.5, 1.2, 0.5), 1, 0.05, 0.05, 0.05, 0.004);
        }
    }
}
