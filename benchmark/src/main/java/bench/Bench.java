package bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Minimal single-fork timing harness (JMH-style warmup + measured iterations).
 * Reports mean ns/op and per-iteration samples for p50/p95/p99.
 */
final class Bench {

    record Result(String name, double meanNsPerOp, long opsTotal, List<Double> iterMeans,
                  double p50, double p95, double p99) {
        void print() {
            System.out.printf(Locale.ROOT,
                    "%-42s %10.1f ns/op  (%d ops, iter means p50=%.1f p95=%.1f p99=%.1f ns/op)%n",
                    name, meanNsPerOp, opsTotal, p50, p95, p99);
        }
    }

    private Bench() {
    }

    /** oneOp = a single benchmarked call (or batch); runs full warmup then measurement. */
    static Result measure(String name, int warmupMs, int measureMs, Runnable setup, Runnable oneOp) {
        for (long end = System.nanoTime() + warmupMs * 1_000_000L; System.nanoTime() < end; ) {
            oneOp.run();
        }
        List<Double> iterMeans = new ArrayList<>();
        long opsTotal = 0;
        long nanosTotal = 0;
        long iter = 0;
        while (iter * 200_000_000L < measureMs * 1_000_000L) {
            iter++;
            setup.run();
            long ops = 0;
            long start = System.nanoTime();
            for (long end = start + 200_000_000L; System.nanoTime() < end; ) {
                oneOp.run();
                ops++;
            }
            long elapsed = System.nanoTime() - start;
            opsTotal += ops;
            nanosTotal += elapsed;
            iterMeans.add((double) elapsed / ops);
        }
        iterMeans.sort(Double::compare);
        double p50 = iterMeans.get((int) (iterMeans.size() * 0.5));
        double p95 = iterMeans.get(Math.min(iterMeans.size() - 1, (int) (iterMeans.size() * 0.95)));
        double p99 = iterMeans.get(Math.min(iterMeans.size() - 1, (int) (iterMeans.size() * 0.99)));
        return new Result(name, (double) nanosTotal / opsTotal, opsTotal, iterMeans, p50, p95, p99);
    }

    /** Runs the supplier exactly once and returns elapsed nanos (for one-shot paths). */
    static long timeOneShot(LongSupplier supplier) {
        long start = System.nanoTime();
        supplier.getAsLong();
        return System.nanoTime() - start;
    }
}
