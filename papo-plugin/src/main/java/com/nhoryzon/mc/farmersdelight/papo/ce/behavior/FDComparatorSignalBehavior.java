package com.nhoryzon.mc.farmersdelight.papo.ce.behavior;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps an integer block property to a redstone comparator signal, mirroring the mod's
 * comparator outputs: organic compost emits (maxStage + 1 - stage) - i.e. the signal
 * FALLS as composting progresses - configured as signal_map = {0:8, 1:7, ... 7:1}.
 * The comparator hooks are the same CE behavior callbacks the reference
 * implementation uses.
 */
public final class FDComparatorSignalBehavior extends BukkitBlockBehavior {

    public static final BlockBehaviorFactory<FDComparatorSignalBehavior> FACTORY = new Factory();

    private final boolean enabled;
    private final IntegerProperty property;
    private final Map<Integer, Integer> signalMap;

    private FDComparatorSignalBehavior(BlockDefinition block, boolean enabled,
                                       IntegerProperty property, Map<Integer, Integer> signalMap) {
        super(block);
        this.enabled = enabled;
        this.property = property;
        this.signalMap = signalMap;
    }

    public static void register() {
        BlockBehaviors.register(Key.of("farmersdelight:comparator_signal"), FACTORY);
    }

    @SuppressWarnings("unused")
    public boolean hasAnalogOutputSignal(Object thisBlock, Object[] args) {
        return enabled;
    }

    @SuppressWarnings("unused")
    public int getAnalogOutputSignal(Object thisBlock, Object[] args) {
        if (!enabled || property == null) return 0;
        return BlockStateUtils.getOptionalCustomBlockState(args[0])
                .map(state -> {
                    Integer value = state.get(property);
                    return value == null ? 0 : signalMap.getOrDefault(value, 0);
                })
                .orElse(0);
    }

    private static final class Factory implements BlockBehaviorFactory<FDComparatorSignalBehavior> {
        @Override
        public FDComparatorSignalBehavior create(BlockDefinition block, ConfigSection section) {
            boolean enabled = section.getBoolean("has_comparator", true);
            IntegerProperty property = null;
            String propertyName = section.getString("property");
            if (propertyName != null && !propertyName.isEmpty()) {
                for (Property<?> prop : block.properties()) {
                    if (prop.name().equals(propertyName) && prop instanceof IntegerProperty ip) {
                        property = ip;
                        break;
                    }
                }
            }
            Map<Integer, Integer> signalMap = new HashMap<>();
            ConfigSection mapSection = section.getSection("signal_map");
            if (mapSection != null) {
                for (String key : mapSection.keySet()) {
                    try {
                        signalMap.put(Integer.parseInt(key),
                                Math.max(0, Math.min(15, mapSection.getInt(key, 0))));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return new FDComparatorSignalBehavior(block, enabled, property, signalMap);
        }
    }
}
