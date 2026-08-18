package com.nhoryzon.mc.farmersdelight.papo.data;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads plugin_content.yml (compostables, trades, loot injects) plus scavenging tables. */
public final class ContentConfig {

    public final Map<String, Float> compostables = new HashMap<>();

    public record TradeOffer(int count, int maxUses, int villagerXp) {
    }

    public final Map<String, TradeOffer> trades = new HashMap<>();
    public final Map<String, List<Map<String, Object>>> lootInjects = new HashMap<>();

    private ContentConfig() {
    }

    @SuppressWarnings("unchecked")
    public static ContentConfig load(Plugin plugin) {
        ContentConfig config = new ContentConfig();
        YamlConfiguration yaml;
        try (InputStream in = plugin.getResource("recipes/plugin_content.yml")) {
            if (in == null) throw new IOException("missing plugin_content.yml");
            yaml = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load plugin_content.yml: " + e.getMessage());
            return config;
        }

        ConfigurationSection compost = yaml.getConfigurationSection("compostables");
        if (compost != null) {
            for (String key : compost.getKeys(false)) {
                config.compostables.put(key.toLowerCase(Locale.ROOT),
                        (float) compost.getDouble(key));
            }
        }
        // trades: farmer: [ {input, count, ...} ]
        if (yaml.get("trades.farmer") instanceof List<?> farmerTrades) {
            for (Object o : farmerTrades) {
                if (o instanceof Map<?, ?> map) {
                    String input = String.valueOf(map.get("input")).toLowerCase(Locale.ROOT);
                    config.trades.put(input, new TradeOffer(
                            intOf(map, "count", 1),
                            intOf(map, "max_uses", 16),
                            intOf(map, "villager_xp", 2)));
                }
            }
        }
        ConfigurationSection injects = yaml.getConfigurationSection("loot_injects");
        if (injects != null) {
            for (String target : injects.getKeys(false)) {
                if (injects.get(target) instanceof List<?> list) {
                    config.lootInjects.put(target.toLowerCase(Locale.ROOT),
                            (List<Map<String, Object>>) list);
                }
            }
        }
        return config;
    }

    /** Extra drops when animals are killed with a knife (mod loot_tables/inject/entities). */
    public static Map<EntityType, List<ItemStack>> scavenging() {
        Map<EntityType, List<ItemStack>> map = new HashMap<>();
        map.put(EntityType.PIG, List.of(strawStack(1)));
        map.put(EntityType.HOGLIN, List.of(strawStack(1)));
        map.put(EntityType.CHICKEN, List.of(boneMealish()));
        map.put(EntityType.COW, List.of(boneMealish()));
        map.put(EntityType.HORSE, List.of(boneMealish()));
        map.put(EntityType.DONKEY, List.of(boneMealish()));
        map.put(EntityType.MULE, List.of(boneMealish()));
        map.put(EntityType.LLAMA, List.of(boneMealish()));
        map.put(EntityType.RABBIT, List.of(boneMealish()));
        map.put(EntityType.SHULKER, List.of(new ItemStack(Material.SHULKER_SHELL)));
        map.put(EntityType.SPIDER, List.of(new ItemStack(Material.STRING, 1)));
        map.put(EntityType.CAVE_SPIDER, List.of(new ItemStack(Material.STRING, 1)));
        return map;
    }

    private static int intOf(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private static ItemStack strawStack(int count) {
        ItemStack stack = CraftEngineHook.buildItem(Key.of(FD.MOD_ID, "straw"));
        if (stack == null) return new ItemStack(Material.WHEAT, count);
        stack.setAmount(count);
        return stack;
    }

    private static ItemStack boneMealish() {
        return new ItemStack(Material.BONE_MEAL, 1);
    }
}
