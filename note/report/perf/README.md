# 性能对比报告索引

> 方法论：服务器内基准（`benchmark/` 插件，`/bench` 命令）+ 同构建 A/B 开关
> （`-Dfd.legacy-tick`）+ 结果一致性门（R1 为 40k 次模糊输入 parity gate；
> R2/R3 为数学等价性论证 + 采样对比）。红线：交互体验与行为结果一致。

| 轮次 | 报告 | 优化点 | 结果 |
|---|---|---|---|
| R1 | [2026-08-28-R1-recipe-matching.md](2026-08-28-R1-recipe-matching.md) | 配方匹配索引/分桶 | 切割 3.9x、工具 8.5x、烹饪 1.2x |
| R2 | [2026-08-28-R2-ticker-hotpath.md](2026-08-28-R2-ticker-hotpath.md) | 堆肥预门控、chunk 早退、库存条件重读 | 总耗时 1.58x（堆肥 20.7x） |
| R3 | [2026-08-28-R3-chunkindex-cache.md](2026-08-28-R3-chunkindex-cache.md) | ChunkIndex 缓存、炉灶零写、满篮跳扫 | 总耗时 2.15x（炉灶 2.31x） |

原始采样：`R*-before/after-raw.txt`。

## 复现

```
# 部署 benchmark/fd-bench.jar 与插件 jar 到服务器 plugins/，然后控制台/RCON：
bench load 400     # 构建压力负载
bench tick 25      # 采样 ticker 分段耗时（before 加 -Dfd.legacy-tick=true 启动参数）
bench recipes      # 配方匹配 parity gate + 吞吐对比
```
