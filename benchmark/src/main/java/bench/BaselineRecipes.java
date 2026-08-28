package bench;

import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verbatim snapshot of FDRecipes matching as of commit 1ac1c83 (pre-optimization
 * baseline). Kept byte-for-byte equivalent to the old algorithm so the benchmark
 * can prove result parity and quantify the speedup. DO NOT "fix" this class.
 */
final class BaselineRecipes {

    private final List<FDRecipes.CookingRecipe> cooking = new ArrayList<>();
    private final List<FDRecipes.CuttingRecipe> cutting = new ArrayList<>();

    List<FDRecipes.CookingRecipe> cooking() {
        return cooking;
    }

    List<FDRecipes.CuttingRecipe> cutting() {
        return cutting;
    }

    FDRecipes.CookingRecipe matchCooking(List<String> present) {
        outer:
        for (FDRecipes.CookingRecipe recipe : cooking) {
            if (recipe.ingredientGroups().size() != present.size()) continue;
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

    FDRecipes.CuttingRecipe matchCutting(String input, String tool) {
        if (input == null || tool == null) return null;
        for (FDRecipes.CuttingRecipe recipe : cutting) {
            if (recipe.input().contains(input) && recipe.tools().contains(toolId(tool))) {
                return recipe;
            }
        }
        return null;
    }

    Set<String> toolsForInput(String input) {
        Set<String> tools = new HashSet<>();
        if (input == null) return tools;
        for (FDRecipes.CuttingRecipe recipe : cutting) {
            if (recipe.input().contains(input)) tools.addAll(recipe.tools());
        }
        return tools;
    }

    private static String toolId(String id) {
        return id;
    }

    static String normalizeId(String id) {
        return id == null ? null : id.toLowerCase(java.util.Locale.ROOT);
    }

    private BaselineRecipes() {
    }

    static BaselineRecipes of(FDRecipes source) {
        BaselineRecipes copy = new BaselineRecipes();
        copy.cooking.addAll(source.cooking);
        copy.cutting.addAll(source.cutting);
        return copy;
    }
}
