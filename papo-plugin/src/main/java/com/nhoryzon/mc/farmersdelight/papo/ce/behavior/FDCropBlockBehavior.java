package com.nhoryzon.mc.farmersdelight.papo.ce.behavior;

import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla-exact {@code net.minecraft.world.level.block.CropBlock} growth for CE blocks.
 * In the mod the FD crops extend CropBlock and rich soil farmland is a FarmlandBlock,
 * so plain vanilla math applies on both: fertility from the 3x3 farmland ring below
 * (moisture &gt; 0 ? 3 : 1, ring cells quartered), same-crop cross/diagonal halves the
 * speed, one age per successful {@code 1/(floor(25/f)+1)} roll at light &gt;= 9, and
 * bone meal adds 2..5 ages (beetroot-like crops: 1/3 random ticks skipped, bonus 0..1).
 */
public final class FDCropBlockBehavior extends BukkitBlockBehavior implements RandomTickBlock, BonemealableBlock {

    public static final BlockBehaviorFactory<FDCropBlockBehavior> FACTORY = new Factory();

    public final IntegerProperty ageProperty;
    public final int minGrowLight;
    public final int minSpawnLight;
    public final boolean isBoneMealTarget;
    public final boolean slowRandomTicks;
    private final Key blockId;
    private final List<Soil> soils;

    /** An eligible ground block: either a CE custom block id or a vanilla material. */
    public record Soil(Key ceBlock, Material material) {
        boolean matches(Block ground) {
            if (ceBlock != null) {
                var state = CraftEngineHook.customBlockState(ground);
                return state != null && state.owner().value().id().equals(ceBlock);
            }
            return ground.getType() == material;
        }
    }

    private FDCropBlockBehavior(BlockDefinition block, IntegerProperty ageProperty,
                                int minGrowLight, int minSpawnLight, boolean isBoneMealTarget,
                                boolean slowRandomTicks, List<Soil> soils) {
        super(block);
        this.ageProperty = ageProperty;
        this.minGrowLight = minGrowLight;
        this.minSpawnLight = minSpawnLight;
        this.isBoneMealTarget = isBoneMealTarget;
        this.slowRandomTicks = slowRandomTicks;
        this.blockId = block.id();
        this.soils = List.copyOf(soils);
    }

    public static void register() {
        BlockBehaviors.register(Key.of("farmersdelight:crop"), FACTORY);
    }

    public int getAge(ImmutableBlockState state) {
        return state.get(ageProperty);
    }

    public boolean isMaxAge(ImmutableBlockState state) {
        return state.get(ageProperty) == ageProperty.max;
    }

    /** Grow the crop by the given age delta through the BlockGrowEvent pipeline. */
    private void grow(Object level, Object pos, ImmutableBlockState state, int delta) {
        int age = getAge(state);
        int after = Math.min(ageProperty.max, age + delta);
        if (after <= age) return;
        Object newState = state.with(ageProperty, after).customBlockState().minecraftState();
        CeReflection.grow(level, pos, newState);
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {
        Object state = args[0];
        Object level = args[1];
        Object pos = args[2];
        if (CeReflection.rawBrightness(level, pos) < minGrowLight) return;
        if (slowRandomTicks && ThreadLocalRandom.current().nextInt(3) == 0) return;

        Optional<ImmutableBlockState> optionalState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optionalState.isEmpty()) return;
        ImmutableBlockState customState = optionalState.get();
        int age = getAge(customState);
        if (age >= ageProperty.max) return;

        World world = CeReflection.world(level);
        int x = CeReflection.x(pos);
        int y = CeReflection.y(pos);
        int z = CeReflection.z(pos);

        float speed = growthSpeed(world, x, y, z);
        if (speed <= 0) return;
        int threshold = (int) Math.floor(25.0f / speed) + 1;
        if (ThreadLocalRandom.current().nextFloat() < 1.0f / threshold) {
            grow(level, pos, customState, 1);
        }
    }

    /** Vanilla CropBlock#getGrowthSpeed evaluated through the Bukkit world mirror. */
    private float growthSpeed(World world, int x, int y, int z) {
        float f = 1.0f;
        int soilY = y - 1;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float fertility = farmlandFertility(world.getBlockAt(x + i, soilY, z + j));
                if (i != 0 || j != 0) {
                    fertility /= 4.0f;
                }
                f += fertility;
            }
        }
        boolean eastWest = isSameCrop(world.getBlockAt(x - 1, y, z)) || isSameCrop(world.getBlockAt(x + 1, y, z));
        boolean northSouth = isSameCrop(world.getBlockAt(x, y, z - 1)) || isSameCrop(world.getBlockAt(x, y, z + 1));
        if (eastWest && northSouth) {
            f /= 2.0f;
        } else if (isSameCrop(world.getBlockAt(x - 1, y, z - 1)) || isSameCrop(world.getBlockAt(x + 1, y, z - 1))
                || isSameCrop(world.getBlockAt(x + 1, y, z + 1)) || isSameCrop(world.getBlockAt(x - 1, y, z + 1))) {
            f /= 2.0f;
        }
        return f;
    }

    /**
     * Vanilla CropBlock#getGrowthSpeed fertility: only true vanilla farmland counts
     * (moisture &gt; 0 ? 3 : 1). Rich soil farmland deliberately contributes nothing -
     * in the mod its benefit is the 20% auto-bonemeal, not growth speed.
     */
    private float farmlandFertility(Block ground) {
        if (ground.getType() != Material.FARMLAND) return 0.0f;
        return ground.getBlockData() instanceof org.bukkit.block.data.type.Farmland farmland
                && farmland.getMoisture() > 0 ? 3.0f : 1.0f;
    }

    private boolean isSameCrop(Block neighbor) {
        var state = CraftEngineHook.customBlockState(neighbor);
        return state != null && state.owner().value().id().equals(blockId);
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        Object level = args[1];
        Object pos = args[2];
        if (CeReflection.rawBrightness(level, pos) < minSpawnLight) return false;
        World world = CeReflection.world(level);
        Block below = world.getBlockAt(
                CeReflection.x(pos),
                CeReflection.y(pos) - 1,
                CeReflection.z(pos));
        for (Soil soil : soils) {
            if (soil.matches(below)) return true;
        }
        return false;
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return !isMaxAge(state);
    }

    @Override
    public boolean isBonemealSuccess(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public boolean isValidBonemealTarget(Object thisBlock, Object[] args) {
        if (!isBoneMealTarget) return false;
        return BlockStateUtils.getOptionalCustomBlockState(args[2])
                .map(this::isMaxAge).map(max -> !max).orElse(false);
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {
        // CE calls performBonemeal with args = [level, ?, pos, nmsState]
        Object level = args[0];
        Object pos = args[2];
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[3]).orElse(null);
        if (state == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // vanilla getBonemealAgeIncrease = nextInt(2,5); beetroot overrides with super()/3 (0..1)
        int bonus = slowRandomTicks ? random.nextInt(2, 6) / 3 : random.nextInt(2, 6);
        grow(level, pos, state, bonus);
        World world = CeReflection.world(level);
        world.spawnParticle(Particle.HAPPY_VILLAGER,
                CeReflection.x(pos) + 0.5,
                CeReflection.y(pos) + 0.5,
                CeReflection.z(pos) + 0.5,
                15, 0.25, 0.25, 0.25);
    }

    private static final class Factory implements BlockBehaviorFactory<FDCropBlockBehavior> {
        @Override
        public FDCropBlockBehavior create(BlockDefinition block, ConfigSection section) {
            List<Soil> soils = new ArrayList<>();
            for (Object entry : section.getList("soils", List.of())) {
                Object id = entry instanceof java.util.Map<?, ?> map ? map.get("block") : entry;
                if (id == null) continue;
                String value = String.valueOf(id);
                if (value.startsWith("minecraft:")) {
                    Material material = Material.matchMaterial(value);
                    if (material != null) soils.add(new Soil(null, material));
                } else {
                    soils.add(new Soil(Key.of(value), null));
                }
            }
            return new FDCropBlockBehavior(
                    block,
                    (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class),
                    section.getInt("light_grow", 9),
                    section.getInt("light_spawn", 8),
                    section.getBoolean("is_bone_meal_target", true),
                    section.getBoolean("slow_random_ticks", false),
                    soils);
        }
    }
}
