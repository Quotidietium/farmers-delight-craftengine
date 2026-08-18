package com.nhoryzon.mc.farmersdelight.papo;

import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.ce.PackInstaller;
import com.nhoryzon.mc.farmersdelight.papo.data.BlockStore;
import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;
import com.nhoryzon.mc.farmersdelight.papo.recipe.RecipeLoader;
import org.bukkit.plugin.java.JavaPlugin;

public final class FarmersDelightPlugin extends JavaPlugin {

    private static FarmersDelightPlugin instance;

    private BlockStore blockStore;
    private FDRecipes recipes;

    public static FarmersDelightPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.blockStore = new BlockStore(this);
        this.recipes = RecipeLoader.load(this);

        // install / refresh the bundled CraftEngine pack, then reload CE content
        boolean reloaded = false;
        try {
            boolean installed = PackInstaller.install(this, FD.VERSION);
            if (installed) {
                CraftEngineHook.reloadContent();
                reloaded = true;
            }
        } catch (Exception e) {
            getLogger().severe("Failed to install CraftEngine pack: " + e.getMessage());
        }

        if (!reloaded && CraftEngineHook.plugin() != null) {
            // content already present; CE may have loaded it before us
            CraftEngineHook.instance().markLoaded();
        }

        getLogger().info("Farmer's Delight (Papo port) enabled: "
                + recipes.cooking.size() + " cooking recipes, "
                + recipes.cutting.size() + " cutting recipes.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Farmer's Delight (Papo port) disabled.");
    }

    public BlockStore blockStore() {
        return blockStore;
    }

    public FDRecipes recipes() {
        return recipes;
    }
}
