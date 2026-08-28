package com.nhoryzon.mc.farmersdelight.papo.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads cooking/cutting recipes and food effects from bundled (or overridden) YAML files. */
public final class RecipeLoader {

    private RecipeLoader() {
    }

    public static FDRecipes load(Plugin plugin) {
        FDRecipes recipes = new FDRecipes();
        loadCooking(plugin, recipes);
        loadCutting(plugin, recipes);
        loadFoodEffects(plugin, recipes);
        return recipes;
    }

    private static YamlConfiguration bundledOrOverride(Plugin plugin, String name) throws IOException {
        Path override = plugin.getDataFolder().toPath().resolve(name);
        if (Files.exists(override)) {
            return YamlConfiguration.loadConfiguration(override.toFile());
        }
        try (InputStream in = plugin.getResource("recipes/" + name)) {
            if (in == null) throw new IOException("Missing bundled recipes/" + name);
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** Parses a cooking-recipes YAML into {@code out}; exposed for benchmarks/tests. */
    public static void parseCooking(YamlConfiguration yaml, FDRecipes out) {
        ConfigurationSection root = yaml.getConfigurationSection("recipes");
        if (root == null) return;
        parseCookingSection(root, out);
    }

    /** Parses a cutting-recipes YAML into {@code out}; exposed for benchmarks/tests. */
    public static void parseCutting(YamlConfiguration yaml, FDRecipes out) {
        ConfigurationSection root = yaml.getConfigurationSection("recipes");
        if (root == null) return;
        parseCuttingSection(root, out);
    }

    private static void loadCooking(Plugin plugin, FDRecipes out) {
        try {
            YamlConfiguration yaml = bundledOrOverride(plugin, "cooking_recipes.yml");
            ConfigurationSection root = yaml.getConfigurationSection("recipes");
            if (root == null) return;
            parseCookingSection(root, out);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load cooking recipes: " + e.getMessage());
        }
    }

    private static void parseCookingSection(ConfigurationSection root, FDRecipes out) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) continue;
                List<Set<String>> groups = new ArrayList<>();
                // ingredients: [[a,b],[c]]
                if (sec.get("ingredients") instanceof List<?> raw) {
                    for (Object o : raw) {
                        Set<String> g = new HashSet<>();
                        if (o instanceof List<?> lst) {
                            for (Object x : lst) g.add(String.valueOf(x).toLowerCase(Locale.ROOT));
                        } else if (o instanceof String s) {
                            g.add(s.toLowerCase(Locale.ROOT));
                        }
                        if (!g.isEmpty()) groups.add(g);
                    }
                }
                String result = sec.getString("result");
                if (result == null) continue;
                out.cooking.add(new FDRecipes.CookingRecipe(
                        id, groups, result.toLowerCase(Locale.ROOT),
                        sec.getInt("result_count", 1),
                        sec.getString("container"),
                        (float) sec.getDouble("experience", 0),
                        sec.getInt("cook_time", 200)));
            }
    }

    private static void loadCutting(Plugin plugin, FDRecipes out) {
        try {
            YamlConfiguration yaml = bundledOrOverride(plugin, "cutting_recipes.yml");
            ConfigurationSection root = yaml.getConfigurationSection("recipes");
            if (root == null) return;
            parseCuttingSection(root, out);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load cutting recipes: " + e.getMessage());
        }
    }

    private static void parseCuttingSection(ConfigurationSection root, FDRecipes out) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) continue;
                Set<String> input = strSet(sec, "input");
                Set<String> tools = strSet(sec, "tool");
                List<FDRecipes.CuttingResult> results = new ArrayList<>();
                if (sec.get("result") instanceof List<?> raw) {
                    for (Object o : raw) {
                        if (o instanceof Map<?, ?> map) {
                            String item = String.valueOf(map.get("item")).toLowerCase(Locale.ROOT);
                            int count = intVal(map, "count", 1);
                            float chance = floatVal(map, "chance", 1.0f);
                            results.add(new FDRecipes.CuttingResult(item, count, chance));
                        }
                    }
                }
                if (input.isEmpty() || results.isEmpty()) continue;
                out.cutting.add(new FDRecipes.CuttingRecipe(id, input, tools, results, sec.getString("sound")));
            }
    }

    private static void loadFoodEffects(Plugin plugin, FDRecipes out) {
        try {
            YamlConfiguration yaml = bundledOrOverride(plugin, "food_effects.yml");
            for (String item : yaml.getKeys(false)) {
                if (item.startsWith("//") || yaml.get(item) == null) continue;
                List<FDRecipes.FoodEffect> effects = new ArrayList<>();
                if (yaml.get(item) instanceof List<?> raw) {
                    for (Object o : raw) {
                        if (o instanceof Map<?, ?> map) {
                            effects.add(new FDRecipes.FoodEffect(
                                    String.valueOf(map.get("effect")).toLowerCase(Locale.ROOT),
                                    intVal(map, "duration", 0),
                                    intVal(map, "amplifier", 0),
                                    floatVal(map, "chance", 1.0f)));
                        }
                    }
                }
                if (!effects.isEmpty()) {
                    out.foodEffects.put(item.toLowerCase(Locale.ROOT), effects);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load food effects: " + e.getMessage());
        }
    }

    private static Set<String> strSet(ConfigurationSection sec, String path) {
        Set<String> set = new HashSet<>();
        Object raw = sec.get(path);
        if (raw instanceof List<?> list) {
            for (Object o : list) set.add(String.valueOf(o).toLowerCase(Locale.ROOT));
        } else if (raw instanceof String s) {
            set.add(s.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    private static int intVal(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        return v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private static float floatVal(Map<?, ?> map, String key, float def) {
        Object v = map.get(key);
        return v == null ? def : Float.parseFloat(String.valueOf(v));
    }
}
