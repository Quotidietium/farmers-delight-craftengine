package com.nhoryzon.mc.farmersdelight.papo.recipe;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory recipe models loaded from the plugin's bundled YAML configs. */
public final class FDRecipes {

    public record CookingRecipe(String id, List<Set<String>> ingredientGroups, String result,
                                int resultCount, @Nullable String container, float experience, int cookTime) {
    }

    public record CuttingResult(String item, int count, float chance) {
    }

    public record CuttingRecipe(String id, Set<String> input, Set<String> tools,
                                List<CuttingResult> results, @Nullable String sound) {
    }

    public final List<CookingRecipe> cooking = new ArrayList<>();
    public final List<CuttingRecipe> cutting = new ArrayList<>();

    /** item id -> effects applied on consume */
    public final Map<String, List<FoodEffect>> foodEffects = new HashMap<>();

    // ---- lookup accelerators (lazily built; recipes are only mutated during load) ----
    private volatile Map<String, CuttingRecipe> cuttingIndex;
    private volatile Map<String, Set<String>> toolIndex;
    private final Map<Integer, List<CookingRecipe>> cookingBySize = new HashMap<>();

    /** Drops all lookup caches; must be called after the recipe lists are (re)loaded. */
    public void invalidateCaches() {
        cuttingIndex = null;
        toolIndex = null;
        cookingBySize.clear();
    }

    private Map<String, CuttingRecipe> cuttingIndex() {
        Map<String, CuttingRecipe> index = cuttingIndex;
        if (index == null) {
            synchronized (this) {
                index = cuttingIndex;
                if (index == null) {
                    index = new HashMap<>();
                    for (CuttingRecipe recipe : cutting) {
                        for (String input : recipe.input()) {
                            index.putIfAbsent(input, recipe);
                        }
                    }
                    cuttingIndex = index;
                }
            }
        }
        return index;
    }

    private Map<String, Set<String>> toolIndex() {
        Map<String, Set<String>> index = toolIndex;
        if (index == null) {
            synchronized (this) {
                index = toolIndex;
                if (index == null) {
                    index = new HashMap<>();
                    for (CuttingRecipe recipe : cutting) {
                        for (String input : recipe.input()) {
                            index.computeIfAbsent(input, k -> new HashSet<>())
                                    .addAll(recipe.tools());
                        }
                    }
                    toolIndex = index;
                }
            }
        }
        return index;
    }

    public record FoodEffect(String effect, int duration, int amplifier, float chance) {
    }

    /* ------------------------------------------------ matching ------------------------------------------------ */

    /**
     * Item-id resolution strategy. Defaults to the CraftEngine lookup; tests and
     * benchmarks may inject a vanilla-only resolver to run without a server.
     */
    public interface ItemIdResolver {
        @Nullable String idOf(@Nullable ItemStack stack);
    }

    private static volatile ItemIdResolver idResolver = null;

    /** Injects an alternative id resolver (benchmark/test hook); pass null to restore CE lookup. */
    public static void setItemIdResolver(@Nullable ItemIdResolver resolver) {
        idResolver = resolver;
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemIdResolver resolver = idResolver;
        if (resolver != null) return resolver.idOf(stack);
        Key custom = CraftEngineItems.getCustomItemId(stack);
        if (custom != null) return custom.toString();
        return stack.getType().key().toString();
    }

    /**
     * Unordered multiset match of the (up to) 6 input slots against ingredient groups.
     * Recipes are bucketed by group count so most candidates are skipped without a
     * scan (a signature cache measured slower than this direct greedy pass).
     */
    @Nullable
    public CookingRecipe matchCooking(ItemStack[] slots) {
        List<String> present = new ArrayList<>(slots.length);
        for (ItemStack s : slots) {
            String id = idOf(s);
            if (id != null) present.add(id);
        }
        int n = present.size();
        if (n == 0) return null;
        List<CookingRecipe> bucket = cookingBySize.computeIfAbsent(n, k -> {
            List<CookingRecipe> list = new ArrayList<>(2);
            for (CookingRecipe recipe : cooking) {
                if (recipe.ingredientGroups().size() == n) list.add(recipe);
            }
            return list;
        });
        outer:
        for (CookingRecipe recipe : bucket) {
            // greedy bipartite match: each group consumed by exactly one present item
            List<String> pool = new ArrayList<>(present);
            for (Set<String> group : recipe.ingredientGroups()) {
                String taken = null;
                for (String p : pool) {
                    if (group.contains(p)) {
                        taken = p;
                        break;
                    }
                }
                if (taken == null) continue outer;
                pool.remove(taken);
            }
            if (pool.isEmpty()) return recipe;
        }
        return null;
    }

    @Nullable
    public CuttingRecipe matchCutting(ItemStack boardItem, ItemStack tool) {
        String input = idOf(boardItem);
        String toolId = idOf(tool);
        if (input == null || toolId == null) return null;
        CuttingRecipe recipe = cuttingIndex().get(input);
        return recipe != null && recipe.tools().contains(toolId) ? recipe : null;
    }

    public Set<String> toolsForInput(ItemStack boardItem) {
        String input = idOf(boardItem);
        if (input == null) return Set.of();
        Set<String> tools = toolIndex().get(input);
        return tools == null ? Set.of() : tools;
    }

    @Nullable
    public List<FoodEffect> effectsFor(ItemStack consumed) {
        String id = idOf(consumed);
        return id == null ? null : foodEffects.get(id);
    }

    /** Vanilla + FD ids helper. */
    public static boolean isKnife(ItemStack stack) {
        String id = idOf(stack);
        if (id == null) return false;
        return id.startsWith("farmersdelight:") && id.endsWith("_knife");
    }
}
