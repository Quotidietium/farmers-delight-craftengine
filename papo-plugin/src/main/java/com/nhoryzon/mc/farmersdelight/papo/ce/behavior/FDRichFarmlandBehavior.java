package com.nhoryzon.mc.farmersdelight.papo.ce.behavior;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rich soil farmland simulation mirroring the mod's RichSoilFarmlandBlock:
 * moisture 0-7 with the vanilla farmland hydration box (x/z -4..4, y 0..1),
 * rain exposure, decay when dry, and the 20% growth boost of the plant above
 * once fully moist (bonemeal-style +2..5 ages with happy-villager particles).
 * Trampling is intentionally absent (the mod farmland is immune) and losing
 * the block below turns the farmland back into rich soil instead of dirt.
 */
public final class FDRichFarmlandBehavior extends BukkitBlockBehavior implements RandomTickBlock {

    public static final BlockBehaviorFactory<FDRichFarmlandBehavior> FACTORY = new Factory();

    /** The mod's unaffected_by_rich_soil tag plus tall flowers that never get boosted. */
    private static final Set<Material> UNAFFECTED_ABOVE = Set.of(
            Material.CACTUS, Material.SUGAR_CANE, Material.BAMBOO, Material.BAMBOO_SAPLING,
            Material.LARGE_FERN, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH,
            Material.PEONY, Material.PITCHER_PLANT, Material.TORCHFLOWER);

    public final IntegerProperty moistureProperty;
    public final float boostChance;
    private final Key richSoil;

    private FDRichFarmlandBehavior(BlockDefinition block, IntegerProperty moistureProperty,
                                   float boostChance, Key richSoil) {
        super(block);
        this.moistureProperty = moistureProperty;
        this.boostChance = boostChance;
        this.richSoil = richSoil;
    }

    public static void register() {
        BlockBehaviors.register(Key.of("farmersdelight:rich_farmland"), FACTORY);
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return true;
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {
        Object state = args[0];
        Object level = args[1];
        Object pos = args[2];
        var optionalState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optionalState.isEmpty()) return;
        ImmutableBlockState customState = optionalState.get();
        int moisture = customState.get(moistureProperty);

        World world = CeReflection.world(level);
        int x = CeReflection.x(pos);
        int y = CeReflection.y(pos);
        int z = CeReflection.z(pos);

        if (!hasWater(world, x, y, z) && !hasRain(world, x, y, z)) {
            if (moisture > 0) {
                placeMoisture(level, pos, customState, moisture - 1);
            }
        } else if (moisture < 7) {
            placeMoisture(level, pos, customState, 7);
        } else if (ThreadLocalRandom.current().nextFloat() <= boostChance) {
            boostPlantAbove(level, pos, world, x, y, z);
        }
    }

    private void placeMoisture(Object level, Object pos, ImmutableBlockState state, int moisture) {
        Object newState = state.with(moistureProperty, moisture).customBlockState().minecraftState();
        CeReflection.grow(level, pos, newState);
    }

    /** Vanilla farmland hydration: any water in the x/z -4..4, y 0..1 box around the farmland. */
    private boolean hasWater(World world, int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (block.getType() == Material.WATER || block.getBlockData()
                            instanceof org.bukkit.block.data.Waterlogged waterlogged && waterlogged.isWaterlogged()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasRain(World world, int x, int y, int z) {
        if (!world.hasStorm()) return false;
        return world.getBlockAt(x, y + 1, z).getLightFromSky() >= 14;
    }

    /** 20% chance to bonemeal-boost the crop standing on fully moist rich soil farmland. */
    private void boostPlantAbove(Object level, Object pos, World world, int x, int y, int z) {
        Block above = world.getBlockAt(x, y + 1, z);
        if (UNAFFECTED_ABOVE.contains(above.getType())) return;
        var ceState = CraftEngineHook.customBlockState(above);
        if (ceState == null) return;
        Property<Integer> ageProperty = ceState.getProperty("age");
        if (!(ageProperty instanceof IntegerProperty integerProperty)) return;
        int age = ceState.get(integerProperty);
        if (age >= integerProperty.max) return;

        Object abovePos = net.momirealms.craftengine.bukkit.util.LocationUtils.toBlockPos(x, y + 1, z);
        int after = Math.min(integerProperty.max, age + ThreadLocalRandom.current().nextInt(2, 6));
        Object newState = ceState.with(integerProperty, after).customBlockState().minecraftState();
        boolean success = CeReflection.grow(level, abovePos, newState);
        if (success) {
            // mod syncWorldEvent BONEMEAL_PARTICLES above the farmland
            world.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, y + 1.5, z + 0.5, 15, 0.25, 0.25, 0.25);
        }
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        World world = CeReflection.world(args[1]);
        Block below = world.getBlockAt(
                CeReflection.x(args[2]),
                CeReflection.y(args[2]) - 1,
                CeReflection.z(args[2]));
        return below.getType().isSolid();
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Direction direction = DirectionUtils.fromNMSDirection(args[updateShape$direction]);
        if (direction != Direction.DOWN) {
            return args[0];
        }
        if (canSurvive(thisBlock, new Object[]{args[0], args[updateShape$level], args[updateShape$blockPos]})) {
            return args[0];
        }
        // mod turnToRichSoil: losing support converts the farmland back into rich soil
        World world = CeReflection.world(args[updateShape$level]);
        int x = CeReflection.x(args[updateShape$blockPos]);
        int y = CeReflection.y(args[updateShape$blockPos]);
        int z = CeReflection.z(args[updateShape$blockPos]);
        Bukkit.getScheduler().runTask(FarmersDelightPlugin.get(),
                () -> CraftEngineHook.placeBlock(new org.bukkit.Location(world, x, y, z), richSoil, false));
        return BlockStateUtils.blockDataToBlockState(Bukkit.createBlockData(Material.AIR));
    }

    private static final class Factory implements BlockBehaviorFactory<FDRichFarmlandBehavior> {
        @Override
        public FDRichFarmlandBehavior create(BlockDefinition block, ConfigSection section) {
            IntegerProperty moisture = (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "moisture", Integer.class);
            return new FDRichFarmlandBehavior(
                    block,
                    moisture,
                    (float) section.getDouble("boost_chance", 0.2),
                    Key.of(section.getString("turn_to", "farmersdelight:rich_soil")));
        }
    }
}
