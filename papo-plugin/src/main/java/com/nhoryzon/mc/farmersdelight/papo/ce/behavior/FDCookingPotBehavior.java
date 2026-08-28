package com.nhoryzon.mc.farmersdelight.papo.ce.behavior;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The cooking pot as a real CE block (mod parity). The comparator output uses the
 * mod formula MathUtils.calcRedstoneFromItemHandler over the 9 pot slots:
 * floor(fullness * 14) + 1 when anything is stored, where fullness is the sum of
 * stack/full-stack ratios divided by the slot count. Pot contents live in the
 * chunk PDC (BlockStore field "inv") managed by the plugin ticker.
 */
public final class FDCookingPotBehavior extends BukkitBlockBehavior {

    public static final BlockBehaviorFactory<FDCookingPotBehavior> FACTORY = new Factory();

    public static final int POT_SLOTS = 9;

    private FDCookingPotBehavior(BlockDefinition block) {
        super(block);
    }

    public static void register() {
        BlockBehaviors.register(Key.of("farmersdelight:cooking_pot"), FACTORY);
    }

    @SuppressWarnings("unused")
    public boolean hasAnalogOutputSignal(Object thisBlock, Object[] args) {
        return true;
    }

    @SuppressWarnings("unused")
    public int getAnalogOutputSignal(Object thisBlock, Object[] args) {
        Block block = pluginBlock(args);
        if (block == null) return 0;
        ItemStack[] inv = FarmersDelightPlugin.get().blockStore().getItems(block, "inv");
        if (inv == null) return 0;
        float fullness = 0f;
        int itemCount = 0;
        for (ItemStack stack : inv) {
            if (stack == null || stack.getType().isAir()) continue;
            itemCount++;
            int max = Math.min(64, stack.getMaxStackSize());
            fullness += (float) stack.getAmount() / max;
        }
        if (POT_SLOTS > 0) fullness /= POT_SLOTS;
        return (int) Math.floor(fullness * 14.0f) + (itemCount > 0 ? 1 : 0);
    }

    private static org.bukkit.block.Block pluginBlock(Object[] args) {
        try {
            // comparator hooks receive (state, level, pos); resolve the Bukkit block
            Object pos = args.length > 2 ? args[2] : args[1];
            Object level = args.length > 2 ? args[1] : null;
            org.bukkit.World world = CeReflection.world(level);
            if (world == null) return null;
            return world.getBlockAt(CeReflection.x(pos), CeReflection.y(pos), CeReflection.z(pos));
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class Factory implements BlockBehaviorFactory<FDCookingPotBehavior> {
        @Override
        public FDCookingPotBehavior create(BlockDefinition block, ConfigSection section) {
            return new FDCookingPotBehavior(block);
        }
    }
}
