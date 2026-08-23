# PapersDelight 配方文件格式 — 精准逆向结果

> 方法：fd-probe JVM 捕获（NMSL_ 方法流 + Bukkit 配置键值神谕）+ 喂样例迭代收敛
> 已实证：喂入下述格式的文件后 `/pd reload` 报告 `加载了1个厨锅配方和1个砧板配方`

## 配方来源（三通道）

1. **`plugins/PapersDelight/recipes/*.yml`** —— 厨锅配方（每个文件 = 一个配方，根级字段）
2. **`plugins/PapersDelight/cutting_recipes/*.yml`** —— 砧板配方（同上）
3. **CraftEngine 包配置段**（`Parsed N recipes from CraftEngine config (cooking/cutting/single/decomposition)`）
   —— 段名与字段尚未逆向（`recipes:` 嵌套 + `type: farmersdelight:cooking` 试探未命中；解析器名
   `papersdelight_recipes`；**single = 煎锅单食材、decomposition = 分解展示**，共 4 类）

另：`plugins/PapersDelight/delights/<名>/delight.yml` 附属系统（priority 排序、tags.yml 合并、
`advancement: true` 启用进度树）——本体重载日志显示「已加载 0 个 delight 附属」。

## 厨锅配方（recipes/test_meal.yml ✅ 实证通过）

```yaml
type: farmersdelight:cooking      # 可省（默认值）；合法值另有 cutting 等
result: minecraft:golden_apple    # 字符串物品 ID（必填）
result_count: 1                   # 可省，默认 1
container: minecraft:bowl         # 可省（getString container）
cookingtime: 100                  # 可省，默认 200（键名无下划线！getInt cookingtime）
experience: 1.0                   # 可省，默认 0.0
ingredients:                      # 必填，getMapList → 【map 列表】字符串列表会被判为缺失
  - item: minecraft:bread
  - item: minecraft:apple
```

- 校验错误枚举：`COOKING_MISSING_FIELDS`（result/ingredients 缺失；提示文案 `缺少必要字段 (result/ingredients)`）
- 文件内根级字段；按配方名嵌套（`test_meal: {...}`）或 `recipes:` 包裹【均失败】
- ingredient map 的键 `item:` 已实证；是否支持 `count`/`tag` 等待进一步验证

## 砧板配方（cutting_recipes/test_cut.yml ✅ 实证通过）

```yaml
type: farmersdelight:cutting
input: minecraft:apple            # 必填
tool: "#farmersdelight:tools/knives"  # 标签带引号；默认工具集来自 config.cutting_board.default_tools
results:                          # 必填（错误枚举 CUTTING_MISSING_INPUT / 「缺少结果」）
  - item: minecraft:apple
    count: 2
    chance: 1.0
```

- 解析后内存结构（捕获转储）：
  `l1[gB=input, g5=[tools], gP=[cw[we=item, wb=count, WY=chance]], g_=container(null), gT=id]`
- 工具判定默认 `#farmersdelight:tools/knives`（config.cutting_board.default_tools）

## 逆向过程中捕获的关键方法（混淆名 → 语义）

| 方法 | 语义 |
|---|---|
| `NMSL_.sya(dir,"recipes",ROOT_COOKING,…)` / `sya(dir,"cutting_recipes",ROOT_CUTTING,…)` | 配方目录扫描 |
| `NMSL_.syb/syg(file,…)` | 文件读取（"root fallback source" / "legacy source"） |
| `NMSL_.pcW(file,defaults,…)` / `pcI(YamlConfig,file,…)` / `pcR/pcK`（cutting） | YAML → 配方对象 |
| `NMSL_.syz(gs[py=<ERROR_ENUM>…])` | 结构化校验错误（含文件名） |
| `NMSL_.ujr(list,…)` | ingredient 列表后处理 |
| `NMSL_.ut[v1=cooking,vQ=?,vb=cutting,vt,vg,vl,v3,vX=失败源]` | 配方注册表（8 集合） |

## 稳定性事件记录

- boot05：开启 org.bukkit.configuration 插桩（键值神谕）后 ~2.5 分钟服务器静默退出（exit 1、无 hs_err、无 crash report）
- 此前 boot04：仅 NMSL_ 插桩稳定运行 4 分钟+（被手动 stop）
- 处置：键值神谕改为 `-Dfdprobe.oracle=true` 按需开启（fd-probe 0.1.1）；后续观察

## 对本项目的映射（差距清单输入）

- 我们的 papo-plugin 把配方打包在 jar 内（27 cooking + 106 cutting），REF 则是**服务器管理员可热加载的文件系统配方**（delights 生态）→ 取代方案需提供等价文件加载层
- CE 配置段的 4 类配方（含 single/decomposition）我们也缺
- 配方字段语义与我们 RecipeLoader 基本同构（result/container/cookingtime/experience/ingredients + tool/chance）
