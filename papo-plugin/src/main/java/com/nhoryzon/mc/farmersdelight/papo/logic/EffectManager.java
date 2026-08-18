package com.nhoryzon.mc.farmersdelight.papo.logic;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side simulation of the mod's Comfort and Nourishment status effects
 * (custom potion types cannot be registered on a Paper server).
 */
public final class EffectManager {

    private record Active(long until, boolean comfort) {
    }

    private final FarmersDelightPlugin plugin;
    private final Map<UUID, Active> active = new HashMap<>();
    private final NamespacedKey comfortKey;
    private final NamespacedKey nourishKey;
    private final BukkitTask task;

    public EffectManager(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
        this.comfortKey = new NamespacedKey(plugin, "comfort_until");
        this.nourishKey = new NamespacedKey(plugin, "nourish_until");
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        task.cancel();
        active.clear();
    }

    /* ------------ entry points ------------ */

    public void applyFromConfig(Player player, List<FDRecipes.FoodEffect> effects) {
        for (FDRecipes.FoodEffect effect : effects) {
            if (Math.random() > Math.max(0.0, Math.min(1.0, effect.chance()))) continue;
            switch (effect.effect()) {
                case "farmersdelight:comfort" -> {
                    giveComfort(player, effect.duration());
                    plugin.advancements().onEffectEaten(player, "farmersdelight:comfort");
                }
                case "farmersdelight:nourishment" -> {
                    giveNourishment(player, effect.duration());
                    plugin.advancements().onEffectEaten(player, "farmersdelight:nourishment");
                }
                case "farmersdelight:remove_random_effect" -> removeRandomEffect(player, false);
                case "farmersdelight:remove_random_bad_effect" -> removeRandomEffect(player, true);
                case "farmersdelight:heal_2" -> player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2.0));
                default -> applyVanilla(player, effect);
            }
        }
    }

    public void giveComfort(Player player, int ticks) {
        long until = System.currentTimeMillis() + ticks * 50L;
        active.merge(player.getUniqueId(), new Active(until, true), (a, b) -> b);
        player.getPersistentDataContainer().set(comfortKey,
                org.bukkit.persistence.PersistentDataType.LONG, until);
        player.sendActionBar(Component.translatable("farmersdelight.effect.comfort", NamedTextColor.GOLD));
    }

    public void giveNourishment(Player player, int ticks) {
        long until = System.currentTimeMillis() + ticks * 50L;
        active.merge(player.getUniqueId(), new Active(until, false), (a, b) -> b);
        player.getPersistentDataContainer().set(nourishKey,
                org.bukkit.persistence.PersistentDataType.LONG, until);
        player.sendActionBar(Component.translatable("farmersdelight.effect.nourishment", NamedTextColor.GOLD));
    }

    private void applyVanilla(Player player, FDRecipes.FoodEffect effect) {
        PotionEffectType type = PotionEffectType.getByName(
                effect.effect().replace("minecraft:", "").replace("farmersdelight:", ""));
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, Math.max(1, effect.duration()),
                    effect.amplifier(), false, true, true));
        }
    }

    private void removeRandomEffect(Player player, boolean harmfulOnly) {
        List<PotionEffect> candidates = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (harmfulOnly && effect.getType().getCategory() != org.bukkit.potion.PotionEffectTypeCategory.HARMFUL) {
                continue;
            }
            candidates.add(effect);
        }
        if (candidates.isEmpty()) return;
        PotionEffect removed = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        player.removePotionEffect(removed.getType());
    }

    /* ------------ ticking ------------ */

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Active>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Active a = entry.getValue();
            if (a.until() <= now) {
                it.remove();
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            if (a.comfort()) {
                tickComfort(player);
            } else {
                tickNourishment(player);
            }
        }
    }

    private void tickComfort(Player player) {
        // every 4 seconds heal half a heart unless regenerating or saturated
        if (player.hasPotionEffect(PotionEffectType.REGENERATION)) return;
        if (player.getSaturation() > 0) return;
        if (player.getHealth() >= player.getMaxHealth()) return;
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1.0));
    }

    private void tickNourishment(Player player) {
        // keep exhaustion drained while the player is not naturally healing with hunger
        if (player.getFoodLevel() < 18) {
            player.setExhaustion(0.0f);
            return;
        }
        if (!Boolean.TRUE.equals(player.getWorld().getGameRuleValue(org.bukkit.GameRule.NATURAL_REGENERATION))) return;
        if (player.getHealth() < player.getMaxHealth() && player.getFoodLevel() >= 18) {
            // let natural regen proceed; nourishment only stops hunger drain
            float saturation = player.getSaturation();
            if (saturation <= 0) {
                // would start consuming hunger: hold food level
                player.setFoodLevel(Math.max(18, player.getFoodLevel()));
            }
        }
        player.setExhaustion(0.0f);
    }

    public boolean hasComfort(Player player) {
        return remaining(player, comfortKey) > 0;
    }

    public boolean hasNourishment(Player player) {
        return remaining(player, nourishKey) > 0;
    }

    private long remaining(Player player, NamespacedKey key) {
        Long until = player.getPersistentDataContainer().get(key,
                org.bukkit.persistence.PersistentDataType.LONG);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        return Math.max(0, left);
    }
}
