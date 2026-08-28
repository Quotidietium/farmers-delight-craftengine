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
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.NamespacedKey;
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
    public void onLoad() {
        // custom CE block behaviors must exist before CraftEngine parses the pack configs
        try {
            com.nhoryzon.mc.farmersdelight.papo.ce.behavior.FDCropBlockBehavior.register();
            getLogger().info("Registered block behavior: farmersdelight:crop");
            com.nhoryzon.mc.farmersdelight.papo.ce.behavior.FDRichFarmlandBehavior.register();
            getLogger().info("Registered block behavior: farmersdelight:rich_farmland");
            com.nhoryzon.mc.farmersdelight.papo.ce.behavior.FDComparatorSignalBehavior.register();
            getLogger().info("Registered block behavior: farmersdelight:comparator_signal");
            com.nhoryzon.mc.farmersdelight.papo.ce.behavior.FDCookingPotBehavior.register();
            getLogger().info("Registered block behavior: farmersdelight:cooking_pot");
        } catch (Throwable t) {
            getLogger().severe("Failed to register CE block behaviors: " + t);
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        this.blockStore = new BlockStore(this);
        this.recipes = RecipeLoader.load(this);
        this.recipes.invalidateCaches();
        this.content = ContentConfig.load(this);
        this.furnitureTracker = new FurnitureTracker(this);
        this.gameTicker = new GameTicker(this);
        this.cropManager = new CropManager(this);
        this.effectManager = new EffectManager(this);
        this.skilletHand = new SkilletHand(this);
        this.signSessions = new SignSessions(this);
        this.advancements = new AdvancementListener(this);

        // server-side check that the datapack backstabbing enchantment resolves and
        // accepts the CE knives: canEnchantItem is the same supported-items test the
        // vanilla enchanting table uses to build its offers, so a true here means
        // Backstabbing shows up in the table for every knife material
        // datapack enchantments live in the Paper registry mirror, not the legacy
        // Enchantment.getByKey lookup
        var backstab = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                .get(net.kyori.adventure.key.Key.key(FD.MOD_ID, "backstabbing"));
        var enchRegistry = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
        var fdKeys = new java.util.ArrayList<String>();
        enchRegistry.forEach(e -> {
            String k = e.getKey().toString();
            if (k.contains("farmersdelight")) fdKeys.add(k);
        });
        getLogger().info("Backstabbing registry scan: size=" + enchRegistry.size()
                + " fdKeys=" + fdKeys);
        if (backstab != null) {
            StringBuilder materials = new StringBuilder();
            for (String mat : new String[]{"flint", "iron_nugget", "gold_nugget",
                    "diamond", "netherite_scrap"}) {
                var stack = new org.bukkit.inventory.ItemStack(org.bukkit.Material.matchMaterial(mat));
                boolean ok = backstab.canEnchantItem(stack);
                materials.append(mat).append('=').append(ok).append(' ');
            }
            getLogger().info("Backstabbing datapack enchantment: resolved; canEnchantItem: " + materials);
        } else {
            getLogger().info("Backstabbing datapack enchantment: NOT FOUND");
        }

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
        Bukkit.getPluginManager().registerEvents(new com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotRecipeBook.ListenerImpl(), this);
        Bukkit.getPluginManager().registerEvents(new com.nhoryzon.mc.farmersdelight.papo.gui.CookingPotBlockGui.ListenerImpl(), this);

        // player/admin convenience command
        org.bukkit.command.Command fd = new org.bukkit.command.Command("farmersdelight",
                "Farmer's Delight plugin info", "/farmersdelight help|version|status", java.util.List.of("fd")) {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                String sub = args.length == 0 ? "help" : args[0].toLowerCase();
                switch (sub) {
                    case "version" -> sender.sendMessage(
                            "FarmersDelight (Papo port) v" + FD.VERSION + " on Papo/Paper 1.21.11 + CraftEngine");
                    case "status" -> {
                        var t = gameTicker();
                        int pots = t.potIndex.totalTracked();
                        int stoves = t.stoveIndex.totalTracked();
                        int baskets = t.basketIndex.totalTracked();
                        int crops = t.cropIndex.totalTracked();
                        int furniture = furnitureTracker().tracked().size();
                        sender.sendMessage("FarmersDelight v" + FD.VERSION
                                + " - cooking pots: " + pots
                                + ", stoves: " + stoves
                                + ", baskets: " + baskets
                                + ", crops: " + crops
                                + ", furniture tracked: " + furniture
                                + " | recipes: " + recipes.cooking.size() + " cooking / "
                                + recipes.cutting.size() + " cutting");
                    }
                    default -> sender.sendMessage(
                            "FarmersDelight v" + FD.VERSION + " - /fd version | /fd status | /fd guide");
                    case "guide" -> {
                        sender.sendMessage("§6§lFarmer's Delight 玩法速查");
                        sender.sendMessage("§e小刀 §7合成后手持右键食材可切割；放在砧板上右键切割");
                        sender.sendMessage("§e烹饪锅 §7下方热源（火/岩浆块等）加热；手持碗右键取餐");
                        sender.sendMessage("§7  - 锅 GUI 内 Shift+点击 背包食材快速投料（碗自动进容器槽）");
                        sender.sendMessage("§7  - 锅 GUI 内配方书可查看全部 27 种烹饪配方");
                        sender.sendMessage("§e炉灶 §7可堆放食材烤制（同篝火配方）；上方有方块时无法烤制");
                        sender.sendMessage("§e篮子 §7自动收集 5 格内掉落物；§e橱柜 §7为 27 格存储");
                        sender.sendMessage("§e堆肥 §7有机 compost 随时间转为富壤（作物产量更高）");
                        sender.sendMessage("§e盛宴 §7多人右键分食，分食完掉落碗");
                    }
                }
                return true;
            }
            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
                    return java.util.stream.Stream.of("help", "version", "status")
                            .filter(s -> s.startsWith(prefix)).toList();
                }
                return java.util.List.of();
            }
        };
        Bukkit.getCommandMap().register("farmersdelight", fd);
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
