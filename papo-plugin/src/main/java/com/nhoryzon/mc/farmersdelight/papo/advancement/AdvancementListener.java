package com.nhoryzon.mc.farmersdelight.papo.advancement;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Illager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Awards the bundled FD advancement datapack entries from plugin-side triggers,
 * mirroring the mod's progression (root/craft knife/cut/use pot/... master chef).
 */
public final class AdvancementListener implements Listener {

    private record ItemTrigger(String advancement, Set<String> items) {
    }

    private final FarmersDelightPlugin plugin;
    private final Map<String, Advancement> advancements = new HashMap<>();
    private final List<ItemTrigger> itemTriggers = new java.util.ArrayList<>();
    private final Map<String, Set<String>> placeTriggers = new HashMap<>();
    private final Map<String, Set<String>> plantAllTriggers = new HashMap<>();
    private final Set<String> masterChefMeals = new HashSet<>();
    // plant-all + master-chef tracking: player -> progress marker set (persisted in PDC)
    private final Map<UUID, Set<String>> plantProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> mealProgress = new ConcurrentHashMap<>();

    public AdvancementListener(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        Bukkit.getScheduler().runTask(plugin, this::resolveAdvancements);
    }

    private void loadConfig() {
        try (InputStream in = plugin.getResource("recipes/advancement_triggers.yml")) {
            if (in == null) {
                plugin.getLogger().warning("advancement_triggers.yml missing; advancements disabled");
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            for (String adv : yaml.getKeys(false)) {
                String spec = yaml.getString(adv, "");
                if (adv.equals("master_chef_meals")) {
                    masterChefMeals.addAll(split(spec));
                    continue;
                }
                if (spec.startsWith("items:")) {
                    itemTriggers.add(new ItemTrigger(adv, new HashSet<>(split(spec.substring(6)))));
                } else if (spec.startsWith("place:")) {
                    placeTriggers.put(adv, new HashSet<>(split(spec.substring(6))));
                } else if (spec.startsWith("plant_all:")) {
                    plantAllTriggers.put(adv, new HashSet<>(split(spec.substring(10))));
                }
                // special event triggers are dispatched from gameplay hooks by id
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load advancement triggers: " + e.getMessage());
        }
    }

    private static List<String> split(String csv) {
        return List.of(csv.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private void resolveAdvancements() {
        for (String id : List.of("root", "craft_knife", "use_cutting_board", "place_cooking_pot",
                "place_skillet", "use_skillet", "place_organic_compost", "get_rich_soil",
                "plant_rice", "harvest_straw", "harvest_ropelogged_tomato", "get_mushroom_colony",
                "eat_comfort_food", "eat_nourishing_food", "place_campfire", "place_feast",
                "get_ham", "hit_raider_with_rotten_tomato", "obtain_netherite_knife",
                "get_fd_seed", "plant_all_crops", "master_chef")) {
            Advancement adv = Bukkit.getAdvancement(new NamespacedKey(FD.MOD_ID, id));
            if (adv != null) {
                advancements.put(id, adv);
            }
        }
        plugin.getLogger().info("Advancements resolved: " + advancements.size() + "/22");
    }

    /* ===================== award core ===================== */

    public void award(Player player, String advancementId) {
        Advancement adv = advancements.get(advancementId);
        if (adv == null || player == null || !player.isOnline()) return;
        AdvancementProgress progress = player.getAdvancementProgress(adv);
        if (!progress.getRemainingCriteria().isEmpty()) {
            progress.awardCriteria("done");
        }
    }

    /* ===================== item obtain events ===================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            checkInventory(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> checkInventory(player));
        }
    }

    public void checkInventory(Player player) {
        // any_fd_item root trigger
        boolean anyFd = false;
        Map<String, Boolean> found = new HashMap<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            String id = itemId(stack);
            if (id == null) continue;
            if (id.startsWith(FD.MOD_ID + ":")) anyFd = true;
            found.put(id, true);
        }
        if (anyFd) award(player, "root");
        for (ItemTrigger trigger : itemTriggers) {
            for (String item : trigger.items()) {
                if (found.containsKey(FD.MOD_ID + ":" + item)) {
                    award(player, trigger.advancement());
                    break;
                }
            }
        }
    }

    /* ===================== vanilla block place (campfire etc.) ===================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String typeName = event.getBlock().getType().name();
        if (typeName.equals("CAMPFIRE") || typeName.equals("SOUL_CAMPFIRE")) {
            award(event.getPlayer(), "place_campfire");
        }
    }

    /* ===================== hooks called from gameplay code ===================== */

    public void onCustomPlace(Player player, String fdId) {
        for (var entry : placeTriggers.entrySet()) {
            if (entry.getValue().contains(fdId)) {
                award(player, entry.getKey());
            }
        }
    }

    public void onPlant(Player player, String fdId) {
        if ("farmersdelight:rice".equals(fdId)) {
            award(player, "plant_rice");
        }
        for (var entry : plantAllTriggers.entrySet()) {
            Set<String> needed = entry.getValue();
            if (!needed.contains(fdId)) continue;
            Set<String> done = plantProgress.computeIfAbsent(player.getUniqueId(),
                    k -> loadSet(player, "plants"));
            done.add(fdId);
            saveSet(player, "plants", done);
            if (done.containsAll(needed)) {
                award(player, entry.getKey());
            }
        }
    }

    public void onCuttingBoardUsed(Player player) {
        award(player, "use_cutting_board");
    }

    public void onSkilletCooked(Player player) {
        award(player, "use_skillet");
    }

    public void onHarvestStraw(Player player) {
        award(player, "harvest_straw");
    }

    public void onHarvestRopeloggedTomato(Player player) {
        award(player, "harvest_ropelogged_tomato");
    }

    public void onEffectEaten(Player player, String effectKey) {
        if ("farmersdelight:comfort".equals(effectKey)) {
            award(player, "eat_comfort_food");
        } else if ("farmersdelight:nourishment".equals(effectKey)) {
            award(player, "eat_nourishing_food");
        }
    }

    public void onTomatoHitRaider(Player player, Entity hit) {
        if (hit instanceof Illager || hit.getType() == EntityType.WITCH
                || hit.getType() == EntityType.RAVAGER) {
            award(player, "hit_raider_with_rotten_tomato");
        }
    }

    public void onMealConsumed(Player player, ItemStack consumed) {
        String id = itemId(consumed);
        if (id == null || !masterChefMeals.contains(id.substring(FD.MOD_ID.length() + 1))) {
            return;
        }
        Set<String> eaten = mealProgress.computeIfAbsent(player.getUniqueId(),
                k -> loadSet(player, "meals"));
        eaten.add(id.substring(FD.MOD_ID.length() + 1));
        saveSet(player, "meals", eaten);
        if (eaten.containsAll(masterChefMeals)) {
            award(player, "master_chef");
        }
    }

    /* ===================== PDC progress sets ===================== */

    private Set<String> loadSet(Player player, String field) {
        String raw = player.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "adv." + field), PersistentDataType.STRING);
        Set<String> set = new HashSet<>();
        if (raw != null && !raw.isEmpty()) {
            set.addAll(List.of(raw.split(",")));
        }
        return set;
    }

    private void saveSet(Player player, String field, Set<String> set) {
        player.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "adv." + field), PersistentDataType.STRING,
                String.join(",", set));
    }

    private String itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        var key = CraftEngineHook.customItemId(stack);
        return key != null ? key.toString() : stack.getType().key().toString();
    }
}
