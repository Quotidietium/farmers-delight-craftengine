package bench;

import com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin;
import com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook;
import com.nhoryzon.mc.farmersdelight.papo.logic.GameTicker;
import com.nhoryzon.mc.farmersdelight.papo.recipe.FDRecipes;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * In-server recipe matching benchmark: the production FDRecipes (loaded by the
 * FarmersDelight plugin, resolving real CraftEngine items) vs the pre-optimization
 * baseline snapshot. Run `/bench recipes` from the console, results land in
 * plugins/FdBench/results.txt. The parity gate must PASS before timings count.
 */
public final class BenchPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Command cmd = new Command("bench", "FD benchmarks", "/bench recipes", List.of()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (args.length == 0) {
                    sender.sendMessage("usage: /bench recipes");
                    return true;
                }
                if (args[0].equals("recipes")) {
                    Bukkit.getGlobalRegionScheduler().execute(BenchPlugin.this, () -> {
                        String report = new RecipeBench(BenchPlugin.this).run();
                        sender.sendMessage("bench done -> plugins/FdBench/results.txt");
                        getLogger().info(report);
                    });
                } else if (args[0].equals("tick")) {
                    int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
                    startTickSampling(seconds);
                    sender.sendMessage("tick sampling for " + seconds + "s -> plugins/FdBench/tick.txt");
                } else if (args[0].equals("stove")) {
                    Bukkit.getGlobalRegionScheduler().execute(BenchPlugin.this, () -> {
                        String report = new StoveChainCheck().run();
                        sender.sendMessage("stove chain check -> plugins/FdBench/stove.txt");
                        getLogger().info(report);
                    });
                } else if (args[0].equals("load")) {
                    int n = args.length > 1 ? Integer.parseInt(args[1]) : 100;
                    startLoad(n);
                    sender.sendMessage("placing " + n + " mechanic objects -> plugins/FdBench/load.txt");
                }
                return true;
            }
        };
        Bukkit.getCommandMap().register("bench", cmd);
        getLogger().info("FdBench ready: /bench recipes|tick [s]|load [n]");
    }

    /** Samples GameTicker segment timers once a second for {@code seconds} seconds. */
    private void startTickSampling(int seconds) {
        com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin fd =
                (com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin)
                        getServer().getPluginManager().getPlugin("FarmersDelight");
        StringBuilder out = new StringBuilder("FD tick sampling " + seconds + "s @ "
                + java.time.Instant.now() + "\n");
        out.append("segments: total | furniture | stove | basket | compost | crop+soil (ns/10t pulse)\n");
        final int[] remaining = {seconds};
        final long[][] samples = new long[6][seconds];
        final Runnable[] task = new Runnable[1];
        task[0] = new Runnable() {
            int i = 0;
            @Override
            public void run() {
                long[] seg = fd.gameTicker().sampleSegments();
                for (int s = 0; s < 6; s++) samples[s][i] = seg[s];
                out.append(String.format("t=%02ds total=%,10d furn=%,9d stove=%,8d basket=%,8d compost=%,8d crop+soil=%,9d%n",
                        i, seg[0], seg[1], seg[2], seg[3], seg[4], seg[5]));
                if (++i >= seconds) {
                    for (int s = 0; s < 6; s++) {
                        java.util.Arrays.sort(samples[s]);
                        long mean = java.util.Arrays.stream(samples[s]).sum() / Math.max(1, seconds);
                        long p95 = samples[s][(int) (seconds * 0.95)];
                        out.append(String.format("seg%d mean=%,d p95=%,d ns/pulse%n", s, mean, p95));
                    }
                    writeFile("tick.txt", out.toString());
                    getLogger().info(out.toString());
                    cancelTask(taskHandle);
                    return;
                }
            }
        };
        taskHandle = getServer().getScheduler().runTaskTimer(this, task[0], 20L, 20L);
    }

    private org.bukkit.scheduler.BukkitTask taskHandle;

    private void cancelTask(org.bukkit.scheduler.BukkitTask t) {
        if (t != null) t.cancel();
    }

    /**
     * Spreads {@code n} mechanic objects (cooking pots, skillets, stoves, baskets,
     * composts, rich soil farmland with advanced wheat) around the world spawn,
     * a few per tick, to create a measurable ticker workload.
     */
    private void startLoad(int n) {
        com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin fd =
                (com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin)
                        getServer().getPluginManager().getPlugin("FarmersDelight");
        org.bukkit.World world = Bukkit.getWorlds().getFirst();
        org.bukkit.Location spawn = world.getSpawnLocation();
        Key[] furnitureIds = {Key.of("farmersdelight:skillet")};
        Key[] blockIds = {
                Key.of("farmersdelight:cooking_pot"), Key.of("farmersdelight:stove"),
                Key.of("farmersdelight:organic_compost"),
                Key.of("farmersdelight:rich_soil_farmland"), Key.of("farmersdelight:advanced_wheat"),
                Key.of("farmersdelight:basket")};
        StringBuilder out = new StringBuilder("FD load build n=" + n + " @ " + java.time.Instant.now() + "\n");
        final int[] placed = {0};
        final int[] fail = {0};
        final int[] i = {0};
        taskHandle = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long start = System.nanoTime();
                while (placed[0] < n && System.nanoTime() - start < 20_000_000L) {
                    int x = spawn.getBlockX() + (i[0] % 24) * 2 - 24;
                    int z = spawn.getBlockZ() + (i[0] / 24 % 24) * 2 - 24;
                    int y = world.getHighestBlockYAt(x, z) + 1;
                    org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z);
                    loc.getWorld().setChunkForceLoaded(x >> 4, z >> 4, true);
                    try {
                        boolean ok;
                        if (i[0] % 8 < 1) {
                            ok = net.momirealms.craftengine.bukkit.api.CraftEngineFurniture.place(
                                    loc, furnitureIds[0]) != null;
                        } else {
                            int bi = i[0] % 6;
                            if (bi == 3) {
                                // farmland into the air cell, wheat on top of it
                                ok = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(
                                        loc, Key.of("farmersdelight:rich_soil_farmland"), true);
                                if (ok) {
                                    ok = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(
                                            loc.clone().add(0, 1, 0), blockIds[3], true);
                                }
                            } else {
                                ok = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(
                                        loc, blockIds[bi], true);
                            }
                            // the static place API bypasses CE's place events, so the plugin's
                            // listener never registers the block - register exactly as it would
                            if (ok) {
                                var ticker = fd.gameTicker();
                                org.bukkit.block.Block block = loc.getBlock();
                                switch (bi) {
                                    case 0 -> ticker.potIndex.add(block);
                                    case 1 -> ticker.stoveIndex.add(block);
                                    case 2 -> ticker.compostIndex.add(block);
                                    case 3 -> ticker.soilIndex.add(block);
                                    case 4 -> ticker.cropIndex.add(loc.clone().add(0, 1, 0).getBlock());
                                    default -> ticker.basketIndex.add(block);
                                }
                            }
                        }
                        if (!ok) fail[0]++;
                    } catch (Throwable t) {
                        fail[0]++;
                        if (fail[0] < 4) out.append("place fail @").append(i[0]).append(": ").append(t).append('\n');
                    }
                    placed[0]++;
                    i[0]++;
                }
                if (placed[0] >= n) {
                    out.append("placed ").append(placed[0]).append(" objects, failures: ").append(fail[0]).append("\n");
                    writeFile("load.txt", out.toString());
                    getLogger().info(out.toString());
                    cancelTask(taskHandle);
                }
            }
        }, 1L, 1L);
    }

    /**
     * Verifies every server-side link of the stove grill chain with a real place +
     * a real Bukkit campfire-recipe lookup, bypassing only the player interaction
     * event itself.
     */
    private final class StoveChainCheck {
        // uses plugin accessor directly
        String run() {
            StringBuilder out = new StringBuilder("FD stove chain check @ " + java.time.Instant.now() + "\n");
            try {
                var fd = com.nhoryzon.mc.farmersdelight.papo.FarmersDelightPlugin.get();
                org.bukkit.World world = getServer().getWorlds().getFirst();
                org.bukkit.Location base = world.getSpawnLocation();
                int x = base.getBlockX() + 3;
                int z = base.getBlockZ() + 3;
                int y = world.getHighestBlockYAt(x, z) + 1;
                org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z);
                world.setChunkForceLoaded(x >> 4, z >> 4, true);

                Key stoveId = Key.of("farmersdelight:stove");
                boolean placed = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(loc, stoveId, true);
                out.append("place stove: ").append(placed).append('\n');
                if (!placed) return finish(out);
                org.bukkit.block.Block stove = world.getBlockAt(x, y, z);
                var ticker = fd.gameTicker();
                ticker.stoveIndex.add(stove);

                // link 1: campfire recipe lookup for a raw beef-like item
                var beef = org.bukkit.inventory.ItemStack.of(org.bukkit.Material.BEEF);
                out.append("campfireResult(BEEF): ").append(ticker.campfireResult(beef) == null ? "null" : "ok").append('\n');

                // link 2: blocked-above check
                out.append("above passable: ").append(stove.getRelative(org.bukkit.block.BlockFace.UP).isPassable()).append('\n');

                // link 3: simulate the grill placement the listener performs
                ItemStack[] grill = new ItemStack[6];
                grill[0] = org.bukkit.inventory.ItemStack.of(org.bukkit.Material.BEEF);
                fd.blockStore().setItems(stove, "grill", grill);
                ItemStack[] readBack = fd.blockStore().getItems(stove, "grill");
                out.append("grill write/read roundtrip: ").append(readBack != null && readBack[0] != null
                        && readBack[0].getType() == org.bukkit.Material.BEEF ? "ok" : "FAILED").append('\n');

                // link 4: run the actual stove tick once via the ticker's public path
                ticker.tickStovePublic(stove);
                ItemStack[] after = fd.blockStore().getItems(stove, "grill");
                out.append("after one tickStove grill[0] still present: ")
                   .append(after != null && after[0] != null ? "ok" : "GONE (check tick logic)").append('\n');
                // link 5: CE state read (lit property visible to the plugin)
                var ceState = com.nhoryzon.mc.farmersdelight.papo.ce.CraftEngineHook.customBlockState(stove);
                out.append("CE state visible: ").append(ceState != null);
                if (ceState != null) {
                    var litProp = ceState.getProperty("lit");
                    out.append(", lit=").append(litProp == null ? "?" : String.valueOf(ceState.getNullable(litProp)));
                }
                out.append('\n');
            } catch (Throwable t) {
                out.append("EXCEPTION: ").append(t).append('\n');
                for (var s : t.getStackTrace()) out.append("  at ").append(s).append('\n');
            }
            return finish(out);
        }

        private String finish(StringBuilder out) {
            try {
                var file = getServer().getPluginManager().getPlugin("FdBench").getDataFolder().toPath().resolve("stove.txt");
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, out.toString(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            return out.toString();
        }
    }

    private void writeFile(String name, String content) {
        try {
            Path file = getDataFolder().toPath().resolve(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().severe("failed to write " + name + ": " + e.getMessage());
        }
    }

    private static final class RecipeBench {
        private final JavaPlugin plugin;
        private final FDRecipes prod;
        private final BaselineRecipes baseline;
        private final List<String> cookingPool = new ArrayList<>();
        private final List<String> toolPool = new ArrayList<>();
        private final List<String> allIds = new ArrayList<>();
        private final List<ItemStack[]> cookingHits = new ArrayList<>();
        private final List<ItemStack[]> cuttingHits = new ArrayList<>();
        private final StringBuilder out = new StringBuilder();

        RecipeBench(JavaPlugin plugin) {
            this.plugin = plugin;
            this.prod = ((FarmersDelightPlugin) plugin.getServer().getPluginManager()
                    .getPlugin("FarmersDelight")).recipes();
            this.baseline = BaselineRecipes.of(prod);
            for (FDRecipes.CookingRecipe r : prod.cooking) {
                for (var group : r.ingredientGroups()) {
                    for (String id : group) {
                        if (!cookingPool.contains(id)) cookingPool.add(id);
                    }
                }
            }
            for (FDRecipes.CuttingRecipe r : prod.cutting) {
                for (String id : r.tools()) {
                    if (!toolPool.contains(id)) toolPool.add(id);
                }
            }
            allIds.addAll(cookingPool);
            allIds.addAll(toolPool);
            for (String id : allIds) {
                if (!id.startsWith("farmersdelight:")) continue;
            }
        }

        private ItemStack item(String id) {
            return CraftEngineHook.buildItem(Key.of(id));
        }

        String run() {
            line("FD recipe matching benchmark @ " + java.time.Instant.now());
            line("production: FDRecipes (live plugin instance), baseline: snapshot 1ac1c83");
            line("cooking recipes=" + prod.cooking.size() + " cutting recipes=" + prod.cutting.size());
            line("");

            // build hit inputs once (real CE items)
            Random rng = new Random(20260828L);
            for (FDRecipes.CookingRecipe r : prod.cooking) {
                ItemStack[] slots = new ItemStack[6];
                int slot = 0;
                for (var group : r.ingredientGroups()) {
                    if (slot >= 6) break;
                    ItemStack stack = item(group.iterator().next());
                    if (stack != null) slots[slot++] = stack;
                }
                if (slot > 0) cookingHits.add(slots);
            }
            for (FDRecipes.CuttingRecipe r : prod.cutting) {
                ItemStack in = item(r.input().iterator().next());
                ItemStack tool = item(r.tools().isEmpty() ? "minecraft:iron_ingot" : r.tools().iterator().next());
                if (in != null && tool != null) cuttingHits.add(new ItemStack[]{in, tool});
            }
            line("hit inputs: " + cookingHits.size() + " cooking, " + cuttingHits.size() + " cutting");
            line("");

            parityGate(rng);
            timings();
            return out.toString();
        }

        /** production must reproduce baseline results for every fuzz input. */
        private void parityGate(Random rng) {
            int mismatches = 0;
            int checked = 0;
            for (int i = 0; i < 20_000; i++) {
                ItemStack[] input = randomCookingInput(rng);
                List<String> ids = new ArrayList<>();
                for (ItemStack s : input) {
                    String id = GameTicker.idOf(s);
                    if (id != null) ids.add(id);
                }
                FDRecipes.CookingRecipe want = baseline.matchCooking(ids);
                FDRecipes.CookingRecipe got = prod.matchCooking(input);
                checked++;
                if (!Objects.equals(want == null ? "-" : want.id(), got == null ? "-" : got.id())) {
                    if (mismatches < 3) {
                        line("MISMATCH cooking: baseline=" + (want == null ? "-" : want.id())
                                + " prod=" + (got == null ? "-" : got.id()) + " input=" + ids);
                    }
                    mismatches++;
                }
            }
            for (int i = 0; i < 20_000; i++) {
                ItemStack board = randomKnownItem(rng);
                ItemStack tool = rng.nextInt(2) == 0 ? randomTool(rng) : randomKnownItem(rng);
                FDRecipes.CuttingRecipe want = baseline.matchCutting(GameTicker.idOf(board), GameTicker.idOf(tool));
                FDRecipes.CuttingRecipe got = prod.matchCutting(board, tool);
                checked++;
                if (!Objects.equals(want == null ? "-" : want.id(), got == null ? "-" : got.id())) {
                    if (mismatches < 3) {
                        line("MISMATCH cutting: baseline=" + (want == null ? "-" : want.id())
                                + " prod=" + (got == null ? "-" : got.id()));
                    }
                    mismatches++;
                }
            }
            line("parity gate: " + checked + " checks, " + mismatches + " mismatches -> "
                    + (mismatches == 0 ? "PASS" : "FAIL (timings void)"));
            line("");
        }

        private void timings() {
            Random rng = new Random(1);
            cookingSet(rng, 0.85, "miss-heavy");
            cookingSet(rng, 0.15, "hit-heavy");
            cuttingSet(rng, 0.85, "miss-heavy");
            cuttingSet(rng, 0.15, "hit-heavy");
            toolsSet(rng);
            flush();
        }

        private void cookingSet(Random seed, double missRate, String label) {
            Random rng = new Random(seed.nextLong());
            ItemStack[][] inputs = new ItemStack[512][];
            List<List<String>> idInputs = new ArrayList<>();
            for (int i = 0; i < 512; i++) {
                inputs[i] = rng.nextDouble() < missRate ? randomCookingInput(rng) : cookingHit(rng);
                List<String> ids = new ArrayList<>();
                for (ItemStack s : inputs[i]) {
                    String id = GameTicker.idOf(s);
                    if (id != null) ids.add(id);
                }
                idInputs.add(ids);
            }
            Bench.Result base = Bench.measure(label + " cooking [baseline]", 600, 1000,
                    () -> {
                    }, () -> {
                        for (List<String> ids : idInputs) baseline.matchCooking(ids);
                    });
            Bench.Result production = Bench.measure(label + " cooking [production]", 600, 1000,
                    () -> {
                    }, () -> {
                        for (ItemStack[] input : inputs) prod.matchCooking(input);
                    });
            reportPair(label, base, production);
        }

        private void cuttingSet(Random seed, double missRate, String label) {
            Random rng = new Random(seed.nextLong());
            ItemStack[][] pairs = new ItemStack[512][];
            String[][] ids = new String[512][];
            for (int i = 0; i < 512; i++) {
                if (rng.nextDouble() < missRate) {
                    pairs[i] = new ItemStack[]{randomKnownItem(rng), randomKnownItem(rng)};
                } else {
                    pairs[i] = cuttingHits.get(rng.nextInt(cuttingHits.size())).clone();
                }
                ids[i] = new String[]{GameTicker.idOf(pairs[i][0]), GameTicker.idOf(pairs[i][1])};
            }
            Bench.Result base = Bench.measure(label + " cutting [baseline]", 600, 1000,
                    () -> {
                    }, () -> {
                        for (String[] pair : ids) baseline.matchCutting(pair[0], pair[1]);
                    });
            Bench.Result production = Bench.measure(label + " cutting [production]", 600, 1000,
                    () -> {
                    }, () -> {
                        for (ItemStack[] pair : pairs) prod.matchCutting(pair[0], pair[1]);
                    });
            reportPair(label, base, production);
        }

        private void toolsSet(Random seed) {
            Random rng = new Random(seed.nextLong());
            ItemStack[] boards = new ItemStack[512];
            String[] ids = new String[512];
            for (int i = 0; i < 512; i++) {
                boards[i] = randomKnownItem(rng);
                ids[i] = GameTicker.idOf(boards[i]);
            }
            Bench.Result base = Bench.measure("toolsForInput [baseline]", 600, 1000,
                    () -> {
                    }, () -> {
                        for (String id : ids) baseline.toolsForInput(id);
                    });
            Bench.Result production = Bench.measure("toolsForInput [production]", 600, 1000,
                    () -> {
                    }, () -> {
                        for (ItemStack board : boards) prod.toolsForInput(board);
                    });
            reportPair("toolsForInput", base, production);
        }

        private void reportPair(String label, Bench.Result base, Bench.Result production) {
            line(String.format("%-38s baseline %10.1f ns/op | production %10.1f ns/op | speedup %.2fx",
                    label, base.meanNsPerOp(), production.meanNsPerOp(),
                    base.meanNsPerOp() / production.meanNsPerOp()));
        }

        /* ---------------- input generation ---------------- */

        private ItemStack[] randomCookingInput(Random rng) {
            ItemStack[] slots = new ItemStack[6];
            int n = rng.nextInt(7);
            for (int i = 0; i < n; i++) {
                ItemStack s = randomKnownItem(rng);
                if (s != null) slots[i] = s;
            }
            return slots;
        }

        private ItemStack randomKnownItem(Random rng) {
            String id = allIds.get(rng.nextInt(allIds.size()));
            ItemStack stack = item(id);
            return stack != null ? stack : item(cookingPool.get(rng.nextInt(cookingPool.size())));
        }

        private ItemStack randomTool(Random rng) {
            return item(toolPool.get(rng.nextInt(toolPool.size())));
        }

        private ItemStack[] cookingHit(Random rng) {
            return cookingHits.get(rng.nextInt(cookingHits.size())).clone();
        }

        /* ---------------- output ---------------- */

        private void line(String s) {
            out.append(s).append('\n');
            plugin.getLogger().info(s);
        }

        private void flush() {
            Path file = plugin.getDataFolder().toPath().resolve("results.txt");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("failed to write results: " + e.getMessage());
            }
        }
    }
}
