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
                }
                return true;
            }
        };
        Bukkit.getCommandMap().register("bench", cmd);
        getLogger().info("FdBench ready: /bench recipes");
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
