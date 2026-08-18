package com.nhoryzon.mc.farmersdelight.papo.logic;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Hand-held skillet cooking: hold the skillet near heat with food in the offhand. */
public final class SkilletHand {

    private record Session(ItemStack food, ItemStack result, int total, int elapsed) {
    }

    private final FarmersDelightPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final BukkitTask task;

    public SkilletHand(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void shutdown() {
        task.cancel();
        sessions.clear();
    }

    public void start(Player player, ItemStack food, ItemStack result) {
        ItemStack one = food.clone();
        one.setAmount(1);
        food.setAmount(food.getAmount() - 1);
        if (food.getAmount() <= 0) {
            player.getInventory().setItemInOffHand(null);
        }
        int total = Math.max(60, plugin.gameTicker().campfireTime(one) / 5);
        sessions.put(player.getUniqueId(), new Session(one, result, total, 0));
        player.getWorld().playSound(player.getLocation(), FD.SND_SKILLET_ADD_FOOD,
                SoundCategory.PLAYERS, 0.6f, 1.0f);
    }

    private void tick() {
        var it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline()
                    || !com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook.isCustomItem(
                    player.getInventory().getItemInMainHand(), FD.SKILLET_ITEM)
                    || !plugin.gameTicker().isHeated(player.getLocation())) {
                // cancelled: return the raw food
                if (player != null && player.isOnline() && session.food() != null) {
                    player.getInventory().addItem(session.food()).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
                it.remove();
                continue;
            }
            int elapsed = session.elapsed() + 10;
            if (elapsed >= session.total()) {
                player.getInventory().addItem(session.result().clone()).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                player.getWorld().playSound(player.getLocation(), FD.SND_SKILLET_SIZZLE,
                        SoundCategory.PLAYERS, 0.7f, 1.0f);
                it.remove();
                continue;
            }
            entry.setValue(new Session(session.food(), session.result(), session.total(), elapsed));
            int pct = elapsed * 100 / session.total();
            player.sendActionBar(Component.translatable("farmersdelight.skillet.cooking",
                    NamedTextColor.YELLOW, Component.text(pct + "%")));
            if (elapsed % 40 == 0) {
                player.getWorld().playSound(player.getLocation(), FD.SND_SKILLET_SIZZLE,
                        SoundCategory.PLAYERS, 0.4f, 1.0f);
            }
        }
    }
}
