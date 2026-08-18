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
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side simulation of the mod's Comfort and Nourishment status effects
 * (custom potion types cannot be registered on a Paper server).
 * State lives entirely in the player's PDC, so effects survive relogs/restarts.
 */
public final class EffectManager {

    private final FarmersDelightPlugin plugin;
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
        long until = Math.max(existing(player, comfortKey), System.currentTimeMillis() + ticks * 50L);
        player.getPersistentDataContainer().set(comfortKey,
                org.bukkit.persistence.PersistentDataType.LONG, until);
        player.sendActionBar(Component.translatable("farmersdelight.effect.comfort", NamedTextColor.GOLD));
    }

    public void giveNourishment(Player player, int ticks) {
        long until = Math.max(existing(player, nourishKey), System.currentTimeMillis() + ticks * 50L);
        player.getPersistentDataContainer().set(nourishKey,
                org.bukkit.persistence.PersistentDataType.LONG, until);
        player.sendActionBar(Component.translatable("farmersdelight.effect.nourishment", NamedTextColor.GOLD));
    }

    private long existing(Player player, NamespacedKey key) {
        Long v = player.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.LONG);
        return v == null ? 0 : v;
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
            if (harmfulOnly && effect.getType().getCategory() != PotionEffectTypeCategory.HARMFUL) {
                continue;
            }
            candidates.add(effect);
        }
        if (candidates.isEmpty()) return;
        PotionEffect removed = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        player.removePotionEffect(removed.getType());
    }

    /* ------------ ticking (PDC driven, survives relog) ------------ */

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (remaining(player, comfortKey, now) > 0) {
                tickComfort(player);
            }
            if (remaining(player, nourishKey, now) > 0) {
                tickNourishment(player);
            }
        }
    }

    private long remaining(Player player, NamespacedKey key, long now) {
        Long until = player.getPersistentDataContainer().get(key,
                org.bukkit.persistence.PersistentDataType.LONG);
        if (until == null) return 0;
        long left = until - now;
        if (left <= 0) {
            player.getPersistentDataContainer().remove(key);
            return 0;
        }
        return left;
    }

    private void tickComfort(Player player) {
        // every 4 seconds heal half a heart unless regenerating or saturated
        if (player.hasPotionEffect(PotionEffectType.REGENERATION)) return;
        if (player.getSaturation() > 0) return;
        if (player.getHealth() >= player.getMaxHealth()) return;
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1.0));
    }

    private void tickNourishment(Player player) {
        // keep exhaustion drained so hunger never depletes (mod NourishmentEffect)
        player.setExhaustion(0.0f);
    }

    public boolean hasComfort(Player player) {
        return existing(player, comfortKey) > System.currentTimeMillis();
    }

    public boolean hasNourishment(Player player) {
        return existing(player, nourishKey) > System.currentTimeMillis();
    }
}
