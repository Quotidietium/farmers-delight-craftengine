package com.nhoryzon.mc.farmersdelight.papo.listener;

import com.nhoryzon.mc.farmersdelight.papo.FD;
import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.projectiles.BlockProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Loot injection, scavenging drops, villager trades, dispenser cutting. */
public final class MiscListener implements Listener {

    private final FarmersDelightPlugin plugin;

    public MiscListener(FarmersDelightPlugin plugin) {
        this.plugin = plugin;
    }

    /* ===================== knife scavenging on entity death ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        var killer = event.getEntity().getKiller();
        if (killer == null) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!isKnife(weapon)) return;
        List<ItemStack> extra = plugin.scavenging().get(event.getEntityType());
        if (extra == null) return;
        var looting = weapon.getEnchantmentLevel(Enchantment.LOOTING);
        for (ItemStack stack : extra) {
            ItemStack drop = stack.clone();
            if (looting > 0 && ThreadLocalRandom.current().nextDouble() < looting * 0.05) {
                drop.setAmount(drop.getAmount() + 1);
            }
            event.getDrops().add(drop);
        }
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(),
                "minecraft:item.armor.equip_generic", SoundCategory.PLAYERS, 0.5f, 0.8f);
    }

    private boolean isKnife(ItemStack stack) {
        String id = GameTicker.idOf(stack);
        return id != null && id.startsWith("farmersdelight:") && id.endsWith("_knife");
    }

    /* ===================== chest loot injection ===================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        LootTable table = event.getLootTable();
        if (table == null) return;
        String key = table.getKey().toString();
        List<Map<String, Object>> injects = plugin.lootInjects().get(key);
        if (injects == null) return;
        for (Map<String, Object> inject : injects) {
            String item = (String) inject.get("item");
            int count = ((Number) inject.getOrDefault("count", 1)).intValue();
            ItemStack stack = CraftEngineHook.buildItem(Key.of(item));
            if (stack == null) continue;
            stack.setAmount(count);
            var meta = stack.getItemMeta();
            stack.setItemMeta(meta);
            event.getLoot().add(stack);
        }
    }

    /* ===================== villager trades ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        var profession = villager.getProfession();
        if (profession != Villager.Profession.FARMER) return;
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        String heldId = GameTicker.idOf(held);
        if (heldId == null || !plugin.trades().containsKey(heldId)) return;
        event.setCancelled(true);
        var offer = plugin.trades().get(heldId);
        MerchantRecipe recipe = new MerchantRecipe(
                new ItemStack(Material.EMERALD), 0, offer.maxUses(), true, 0, 0.05f);
        ItemStack input = held.clone();
        input.setAmount(offer.count());
        recipe.addIngredient(input);
        event.getPlayer().openMerchant(new FDTradeMerchant(List.of(recipe)), true);
    }

    public static final class FDTradeMerchant implements org.bukkit.inventory.Merchant {

        private final List<MerchantRecipe> recipes;
        private org.bukkit.entity.HumanEntity trader;

        public FDTradeMerchant(List<MerchantRecipe> recipes) {
            this.recipes = recipes;
        }

        @Override
        public List<MerchantRecipe> getRecipes() {
            return recipes;
        }

        @Override
        public void setRecipes(List<MerchantRecipe> list) {
        }

        @Override
        public MerchantRecipe getRecipe(int i) {
            return recipes.get(i);
        }

        @Override
        public int getRecipeCount() {
            return recipes.size();
        }

        @Override
        public void setRecipe(int i, MerchantRecipe merchantRecipe) {
            // read-only merchant
        }

        @Override
        public boolean isTrading() {
            return trader != null;
        }

        @Override
        public org.bukkit.entity.HumanEntity getTrader() {
            return trader;
        }

    }

    /* ===================== dispenser cuts cutting boards ===================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Dispenser dispenser)) return;
        ItemStack tool = event.getItem();
        if (tool == null || tool.getType().isAir()) return;
        org.bukkit.block.BlockFace facing = block.getBlockData() instanceof org.bukkit.block.data.Directional dir
                ? dir.getFacing() : org.bukkit.block.BlockFace.EAST;
        Block target = block.getRelative(facing);
        var entry = plugin.furnitureTracker().at(
                target.getLocation().add(0.5, 0, 0.5), FD.CUTTING_BOARD);
        if (entry == null) return;
        ItemStack stored = plugin.gameTicker().skilletItem(entry.furniture());
        if (stored == null) {
            event.setCancelled(true);
            return;
        }
        var recipe = plugin.recipes().matchCutting(stored, tool);
        if (recipe == null) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        // consume one tool unit from the dispenser
        var inventory = dispenser.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot != null && slot.equals(tool)) {
                slot.setAmount(slot.getAmount() - 1);
                if (slot.getAmount() <= 0) inventory.setItem(i, null);
                break;
            }
        }
        int fortune = tool.getEnchantmentLevel(Enchantment.LOOTING);
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Location loc = entry.furniture().location().clone().add(0.5, 0.2, 0.5);
        for (var result : recipe.results()) {
            int count = 0;
            for (int i = 0; i < result.count(); i++) {
                if (rand.nextFloat() < result.chance() + 0.1f * fortune) count++;
            }
            if (count <= 0) continue;
            ItemStack out = CraftEngineHook.buildItem(Key.of(result.item()));
            if (out == null) continue;
            out.setAmount(count);
            loc.getWorld().dropItemNaturally(loc, out);
        }
        String sound = recipe.sound() != null ? recipe.sound()
                : (isKnife(tool) ? FD.SND_CB_KNIFE : "minecraft:block.wood.hit");
        loc.getWorld().playSound(loc, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.ITEM, loc.clone().add(0, 0.15, 0), 5,
                0.2, 0.1, 0.2, 0.05, stored);
        plugin.gameTicker().setDisplayChild(entry.furniture(), "itemEntity", null, 0.5, 0.12, 0.5, 0.45f);
    }
}
