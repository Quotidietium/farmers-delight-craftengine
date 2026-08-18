package com.nhoryzon.mc.farmersdelight.papo;

import com.nhoryzon.mc.farmersdelight.papo.advancement.AdvancementListener;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.ce.PackInstaller;
import com.nhoryzon.mc.farmersdelight.papo.data.BlockStore;
import com.nhoryzon.mc.farmersdelight.papo.data.ChunkIndex;
import com.nhoryzon.mc.farmersdelight.papo.data.ContentConfig;
import com.nhoryzon.mc.farmersdelight.papo.entity.FurnitureTracker;
import com.nhoryzon.mc.farmersdelight.papo.gui.ContainerBlockGui;
import com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotGui;
import com.nhoryzon.mc.farmersdelight.papo.listener.BlockListener;
import com.nhoryzon.mc.farmersdelight.papo.listener.FurnitureListener;
import com.nhoryzon.mc.farmersdelight.papo.listener.MiscListener;
import com.nhoryzon.mc.farmersdelight.papo.listener.PlayerListener;
import com.nhoryzon.mc.farmersdelight.papo.logic.CropManager;
import com.nhoryzon.mc.farmersdelight.papo.logic.EffectManager;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import com.nhoryzon.mc.farmersdelight.papo.logic.SignSessions;
import com.nhoryzon.mc.farmersdelight.papo.logic.SkilletHand;
import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;
import com.nhoryzon.mc.farmersdelight.papo.recipe.RecipeLoader;
import com.nhoryzon.mc.farmersdelight.papo.world.WildCropGenerator;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public final class FarmersDelightPlugin extends JavaPlugin implements Listener {

    private static FarmersDelightPlugin instance;

    private BlockStore blockStore;
    private FDRecipes recipes;
    private ContentConfig content;
    private FurnitureTracker furnitureTracker;
    private GameTicker gameTicker;
    private CropManager cropManager;
    private EffectManager effectManager;
    private SkilletHand skilletHand;
    private SignSessions signSessions;
    private AdvancementListener advancements;

    public static FarmersDelightPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.blockStore = new BlockStore(this);
        this.recipes = RecipeLoader.load(this);
        this.content = ContentConfig.load(this);
        this.furnitureTracker = new FurnitureTracker(this);
        this.gameTicker = new GameTicker(this);
        this.cropManager = new CropManager(this);
        this.effectManager = new EffectManager(this);
        this.skilletHand = new SkilletHand(this);
        this.signSessions = new SignSessions(this);
        this.advancements = new AdvancementListener(this);

        // install / refresh the bundled CraftEngine pack, then reload CE content
        boolean installed = false;
        try {
            installed = PackInstaller.install(this, FD.VERSION);
            if (installed) {
                CraftEngineHook.reloadContent();
            }
        } catch (Exception e) {
            getLogger().severe("Failed to install CraftEngine pack: " + e.getMessage());
        }
        if (!installed) {
            CraftEngineHook.instance().markLoaded();
        }

        // listeners
        Bukkit.getPluginManager().registerEvents(CraftEngineHook.instance(), this);
        Bukkit.getPluginManager().registerEvents(new FurnitureListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MiscListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.nhoryzon.mc.farmersdelight.papo.listener.PhysicsListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.nhoryzon.mc.farmersdelight.papo.listener.GuiDiagnosticListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CookingPotGui.ListenerImpl(this), this);
        Bukkit.getPluginManager().registerEvents(new ContainerBlockGui.ListenerImpl(this), this);
        Bukkit.getPluginManager().registerEvents(this.signSessions, this);
        Bukkit.getPluginManager().registerEvents(this.advancements, this);
        Bukkit.getPluginManager().registerEvents(new WildCropGenerator(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);

        this.furnitureTracker.enable();
        Bukkit.getCommandMap().register("farmersdelight",
                new com.nhoryzon.mc.farmersdelight.papo.command.FDPlaceCommand("fdplace"));

        getLogger().info("Farmer's Delight (Papo port) enabled: "
                + recipes.cooking.size() + " cooking recipes, "
                + recipes.cutting.size() + " cutting recipes, "
                + content.compostables.size() + " compostables.");
    }

    @Override
    public void onDisable() {
        if (gameTicker != null) gameTicker.shutdown();
        if (effectManager != null) effectManager.shutdown();
        if (skilletHand != null) skilletHand.shutdown();
        if (furnitureTracker != null) furnitureTracker.disable();
        getLogger().info("Farmer's Delight (Papo port) disabled.");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        // wild crops are decorated on first chunk load (see WildCropGenerator)
    }

    public BlockStore blockStore() {
        return blockStore;
    }

    public FDRecipes recipes() {
        return recipes;
    }

    public FurnitureTracker furnitureTracker() {
        return furnitureTracker;
    }

    public GameTicker gameTicker() {
        return gameTicker;
    }

    public CropManager cropManager() {
        return cropManager;
    }

    public EffectManager effectManager() {
        return effectManager;
    }

    public SkilletHand skilletHand() {
        return skilletHand;
    }

    public SignSessions signSessions() {
        return signSessions;
    }

    public AdvancementListener advancements() {
        return advancements;
    }

    public Map<String, Float> compostables() {
        return content.compostables;
    }

    public Map<String, ContentConfig.TradeOffer> trades() {
        return content.trades;
    }

    public Map<String, List<Map<String, Object>>> lootInjects() {
        return content.lootInjects;
    }

    public Map<EntityType, List<ItemStack>> scavenging() {
        return ContentConfig.scavenging();
    }
}
