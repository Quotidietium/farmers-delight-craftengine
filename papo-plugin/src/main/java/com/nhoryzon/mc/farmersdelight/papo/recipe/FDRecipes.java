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

    public record FoodEffect(String effect, int duration, int amplifier, float chance) {
    }

    /* ------------------------------------------------ matching ------------------------------------------------ */

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        Key custom = CraftEngineItems.getCustomItemId(stack);
        if (custom != null) return custom.toString();
        return stack.getType().key().toString();
    }

    /** Unordered multiset match of the (up to) 6 input slots against ingredient groups. */
    @Nullable
    public CookingRecipe matchCooking(ItemStack[] slots) {
        List<String> present = new ArrayList<>();
        for (ItemStack s : slots) {
            String id = idOf(s);
            if (id != null) present.add(id);
        }
        outer:
        for (CookingRecipe recipe : cooking) {
            if (recipe.ingredientGroups().size() != present.size()) continue;
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
        for (CuttingRecipe recipe : cutting) {
            if (recipe.input().contains(input) && recipe.tools().contains(toolId)) {
                return recipe;
            }
        }
        return null;
    }

    public Set<String> toolsForInput(ItemStack boardItem) {
        String input = idOf(boardItem);
        Set<String> tools = new HashSet<>();
        if (input == null) return tools;
        for (CuttingRecipe recipe : cutting) {
            if (recipe.input().contains(input)) tools.addAll(recipe.tools());
        }
        return tools;
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
