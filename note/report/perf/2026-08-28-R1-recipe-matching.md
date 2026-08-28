# R1 — 配方匹配热路径优化（FDRecipes）

> 日期：2026-08-28 ｜ 环境：smoke 服务器（Papo 1.21.11 + CE 26.7.4，空世界）
> 方法：服务器内基准插件 `benchmark/`（`/bench recipes`），生产 FDRecipes 实例 vs
> 优化前算法快照（commit 1ac1c83），真实 CE 物品输入。
> **红线量化**：parity gate = 40,000 次模糊输入逐次断言生产与基线结果一致 → 全轮 PASS。

## 基线（优化前 production，ns/512 次调用）

| 场景 | 基线快照 | 优化前 production | 说明 |
|---|---|---|---|
| cooking miss-heavy | 97.4k | 250.8k | production 含 CE idOf 解析开销（baseline 用预解析 id） |
| cooking hit-heavy | 103.3k | 233.2k | 同上 |
| cutting miss-heavy | 124.7k | 251.0k | 同上 |
| cutting hit-heavy | 191.4k | 377.4k | 同上 |
| toolsForInput | 177.7k | 219.7k | 同上 |

## 优化内容

1. **切割配方 O(1) 索引**：`Map<input, CuttingRecipe>` + `Map<input, Set<tool>>` 懒构建
   （原来每次对 106 条做双重 Set.contains 扫描）。
2. **烹饪配方 size 分桶**：`Map<组数, List<配方>>`，贪心匹配只扫组数相同的配方
   （原 27 条全扫）。
3. **否定尝试（已回退）**：输入签名 memo 缓存实测**负优化**（cooking 250.8k→344.7k，
   签名 join + 同步开销 > size 剪枝后的贪心），已删除——记录在案防止回潮。
4. **失效钩子**：`FDRecipes.invalidateCaches()`，`RecipeLoader.load` 后调用。

## 结果（production 对 production，同机多次采样）

| 场景 | 优化前 production | 优化后 production | 提速 |
|---|---|---|---|
| cooking miss-heavy | 250.8k | **211.0k** | 1.19x |
| cooking hit-heavy | 233.2k | **181.8k** | 1.28x |
| cutting miss-heavy | 251.0k | **65.3k** | **3.85x** |
| cutting hit-heavy | 377.4k | **91.5k** | **4.12x** |
| toolsForInput | 219.7k | **25.7k** | **8.55x** |

原始数据：`R1-after-raw.txt`（baseline 列为算法快照参照，波动 ±15% 为服务器噪声；
对比结论一律取 production 列自身前后）。

## 红线核验

- **结果一致性**：40,000 次模糊输入（含命中/未命中/随机混合）生产输出 == 基线输出，0 mismatch。
- **稳定性**：bench 窗口外服务器 0 ERROR；FD 1.2.0 正常启用，CE 包正常加载。
  （bench 运行时同步阻塞主线程触发 watchdog 转储属预期，R2 起改为分片执行。）
- **兼容性**：`invalidateCaches` 挂在既有 `RecipeLoader.load` 尾部，无配置/接口变更。

## 生产影响评估

实际负载（<50 活跃锅、每 10t 匹配一次）下本路径 CPU 占比本就低；本轮价值主要在
切割板高频交互（玩家连续切菜时每击一次 matchCutting+toolsForInput，此前是 106 条扫描）
与代码健康度。大头在 R2（GameTicker 全量遍历）。
