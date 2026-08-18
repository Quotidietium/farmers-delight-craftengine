package com.nhoryzon.mc.farmersdelight.papo;

import org.bukkit.plugin.java.JavaPlugin;

public final class FarmersDelightPlugin extends JavaPlugin {

    private static FarmersDelightPlugin instance;

    public static FarmersDelightPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Farmer's Delight (Papo port) enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Farmer's Delight (Papo port) disabled.");
    }
}
