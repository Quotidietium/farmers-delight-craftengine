# Farmer's Delight — Papo (Paper 1.21.11) 插件移植

> 移植日期：2026-08-18
> 产物：`papo-plugin/build/libs/farmers-delight-papo-1.0.0.jar`
> 运行环境：Papo 0.32.1（Paper 1.21.11 分支）+ CraftEngine 26.7.4 插件 + Java 21

## 1. 架构总览

```
papo-plugin/
├── build.gradle.kts            # Java 21 工具链；paper-api 1.21.11 + craft-engine 26.7 compileOnly
├── src/main/java/.../papo/
│   ├── FarmersDelightPlugin    # 主类：装包→CE reload→注册监听/任务
│   ├── bootstrap/              # Bootstrap：RegistryEvents 注册背刺附魔
│   ├── FD.java                 # 全部 ID/音效/热源常量
│   ├── ce/
│   │   ├── CraftEngineHook     # CE 门面封装（物品/方块/家具/reload）
│   │   └── PackInstaller       # 从 jar 解压 ce-pack → plugins/CraftEngine/resources/farmersdelight/
│   ├── data/                   # BlockStore(区块PDC) / ChunkIndex(刻索引) / ContentConfig
│   ├── entity/FurnitureTracker # 重启后按 PDL craftengine:furniture_id 重挂家具
│   ├── recipe/                 # FDRecipes 模型 + RecipeLoader（cooking/cutting/effects YAML）
│   ├── gui/                    # CookingPotGui（27格布局复刻）/ ContainerBlockGui（篮子/橱柜）
│   ├── logic/
│   │   ├── GameTicker          # 0.5s 心跳：锅/煎锅/炉灶/篮子/堆肥 + 作物/沃土错峰
│   │   ├── CropManager         # 生长/采收/骨粉/破坏掉落/蘑菇菌落
│   │   ├── EffectManager       # 舒适/滋养服务端模拟（每秒任务）
│   │   ├── SkilletHand         # 手持煎锅烹饪会话
│   │   └── SignSessions        # 画布告示牌聊天输入
│   ├── listener/               # Furniture/Block/Player/Misc 四大监听器
│   └── world/WildCropGenerator # ChunkLoad 首次加载时生成野生作物（PDC 防重）
└── src/main/resources/
    ├── paper-plugin.yml        # depend CraftEngine (BEFORE, join-classpath)
    ├── ce-pack/                # CraftEngine 资源包（生成器产物，795 文件）
    └── recipes/                # 插件侧配置（cooking/cutting/food_effects/plugin_content）
```

**数据存储**：家具数据（锅的 9 格物品/烹饪进度）→ 家具基实体的 PDC；
方块数据（炉灶烤架、篮子库存、堆肥）→ 所在区块的 PDC（`fd.<x>.<y>.<z>.<field>`）。
均为原版持久化，重启无损。

## 2. CraftEngine 包内容（由 `tools/ce_gen` 生成）

| 内容 | 数量 | 说明 |
|---|---|---|
| items | 158 | 全部物品；碗餐基材 mushroom_stew+use_remainder 碗、瓶饮 honey_bottle、刀具 5 把（属性/耐久/附魔能力） |
| blocks | 55 | 通用 blockstate→appearances/variants 转换；作物按容量分配 kelp/twisting_vine/weeping_vine/cave_vine 组，实心方块 note_block 组（共 167 状态，预算内） |
| furniture | 35 | 烹饪锅(3支撑变体)/砧板/煎锅(托架变体)/绳子/安全网/地毯/榻榻米3种/盛宴5种(份数变体)/告示牌34 |
| 配方 | 148 | crafting/smelting/blasting/smoking/campfire/smithing → CE recipes（FD 标签声明于 items.settings.tags；c: 标签递归展开） |
| sounds | 14 事件 | mod 原版 OGG 原样托管 |
| lang | 30 语言 | mod 语言文件全量 + 插件键 |
| 贴图/模型 | 305 PNG + 319 JSON | mod 资产原样复制（含告示牌面板模型生成） |

## 3. 功能对照审计（vs mod）

### ✅ 完整移植（交互与反馈一致）
- **砧板**：放物/工具切割（106 条配方，时运加成 0.1/级）/空手取回/雕刻展示/发射器切割（24 类工具）/音效粒子/进度触发条件（切割）
- **烹饪锅**：6 槽无序匹配（27 条配方）/热源判定（直热+传导）/200tick 烹饪+进度衰减/容器装碗/手持容器取餐/经验结算/破坏掉落保全/支撑模式切换（潜行空手）
- **炉灶**：点燃/熄灭（铲/水桶）/6 格烤架+物品展示/营火配方烤制/遮挡散落/熄火进度衰减
- **煎锅**：放置（托架变体）+ 手持（副手食材+热源+火焰附加加速）/完成向侧弹出
- **篮子**：朝向吸取（漏斗节奏 8tick）/红石禁用/27 格 GUI/破坏散落
- **橱柜**（9 木种+制菌/诡异）：开门动画（open 属性）/27 格 GUI/掉落
- **作物**：卷心菜/洋葱（采收+再生）/番茄苗→藤（攀绳）/水稻+稻穗（1/3 速度+supporting）/骨粉（含稻穗满龄转移）/破坏掉落表
- **沃土**：锄→耕地/20% 随机刻催熟+骨粉粒子/蘑菇→菌落转化/菌落生长+剪刀采收
- **有机堆肥**：右键堆肥填充（35 种可堆肥物+几率）/渐进 0-7/满级转沃土
- **野生作物**：8 种按生物群系规则生成（1/30 区块+簇状）
- **食物系统**：碗/瓶返还（use_remainder 组件）/舒适+滋养（服务端模拟：舒适=4s/HP 条件回血、滋养=清空疲惫度）/原版效果/特殊饮品（奶瓶随机清效果、热可可清负面、西瓜汁回血）/慢食海带卷 3.2s
- **刀具**：5 把（攻击 1.5~4.5/速度 -1.8/附魔白名单含背刺）/背刺（×1.4~1.8+暴击音）/轻击退/切蛋糕+切派
- **投掷**：腐坏番茄（雪球基材+碎裂粒子+命中音）
- **动物**：狗粮（治疗+3 效果+星星粒子）/马粮/鸡（3 种子）/猪（卷心菜番茄）繁殖/鹦鹉驯服
- **盛宴**：5 种（份数变体+空盘破坏+碗取餐；寿司卷拼盘按份数给不同卷）
- **绳子**：攀爬（速度模拟）/敲钟（24 格）/卷绳回收
- **榻榻米**：整张垫自动配对（foot+head）/破坏联动
- **告示牌**：34 种（17 色×立/挂）聊天输入 4 行+TextDisplay 渲染
- **村民**：农夫收购 4 种作物（潜行+手持作物打开交易）
- **战利品**：箱子注入（LootGenerateEvent）/刀割搜刮（12 种生物，时运加成）
- **堆肥几率**：35 种物品右键堆肥机（手动 level 推进）
- **合成**：148 条 CE 配方（含 3 条原版作物箱）
- **进度系统**：22 条主线进度（捆绑数据包 + 插件触发授予）——获得物品/切菜/放置/种植/吃效果食物/番茄命中袭击者/种齐作物/大厨全餐，全部还原 mod 的 toast 反馈
- **物理与细节机制**：安全网弹跳缓冲、稻捆落地伤害 0.2×、炉灶赤脚烫伤（每 0.5s 判定）、原版堆肥器接受 FD 可堆肥物（35 种按几率）、箱子战利品注入（键前缀修正）、菌落物品放置即成熟（age 3）、菌落剪刀采收、榻榻米相邻视觉配对/破坏解除、下界合金刀防火（fire_resistant 组件）、营火置于稻/稻草捆上方产生信号烟
- **CE 分类**：7 个物品浏览器分类（烹饪设施/作物与种子/食物与食材/餐食与饮品/工具与刀具/材料/家具与装饰），158 物品全部分类、多语言（en/zh_cn/zh_tw）

### 第九轮：锅 GUI 锚定 CE 原生打开（1.0.5）

专用布局经 CE 原生打开事件 1-tick 换肤实现，彻底消除对未测路由的依赖。详见 note/release/1.0.5.md。

### 第八轮：烹饪锅三重 GUI 保障（1.0.4）

锅存储统一到 CE 家具实体 NBT；GUI 打开路径：CE 事件 / Bukkit 实体事件 / CE 原生行为三重保障共用同一库存。详见 note/release/1.0.4.md。

### 第七轮：GUI 改用 CE 原生实现（1.0.3）

橱柜/篮子 → CE `simple_storage_block`（原生 GUI+持久化+破坏掉落+漏斗/比较器）；家具交互加 Bukkit 实体交互兜底路径。详见 note/release/1.0.3.md。

### 第六轮：家具客户端渲染对齐 bench 模式（1.0.2）

默认变体渲染家具自身物品（CE bench 同构）；62 个变体模型改为 parent-free 自包含。详见 note/release/1.0.2.md。

### 第五轮用户反馈修复（同日，家具不显示 + 大分类）

**「砧板等放下后不显示」根因**：CE 的普通配置重载（`reloadPlugin`）**不会再生资源包**——只有 `ce reload pack/all` 才调用 `generateResourcePack()`。我们的 PackInstaller 此前只做配置重载，客户端拿到的资源包缺失/陈旧内部展示物品的 items/ 定义 → 家具实体在服务端正常生成（已用控制台 `/fdplace` + `data get` 实证 ITEM_DISPLAY + INTERACTION 实体都在）但客户端无法解析模型 → 隐形。
**修复**：`CraftEngineHook.reloadContent()` 现在在配置重载成功后自动执行 `generateResourcePack() + uploadResourcePack()`——插件每次安装/更新内容后资源包自动重建并推送。冒烟验证：清空后启动，`generated/resource_pack.zip`（2.1MB、1541 条目、76 个内部展示定义）自动出现。
**诊断工具**：新增 `/fdplace` 控制台命令（CommandMap 注册）放置家具并回显实体数据。

**顶级大分类**：`farmersdelight:main`「农夫乐事」（金字图标 cooking_pot，priority 0）嵌套 7 个子分类（hidden:true + `#` 引用），三语言名称（en/zh_cn/zh_tw）。

### 第四轮引用审计修复（同日，关键缺陷）

引用完整性审计发现 **mod 招牌的舒适/滋养餐食效果全部静默失效**：生成器 Foods 解析缺少 4 参数构造分支（hunger/saturation/effect/chance）导致 27 种碗餐效果丢失，且效果表键缺少命名空间前缀无法匹配。修复后 42 条食物效果全部生效（12 舒适 + 15 滋养 + 特殊饮品 + 原版汤类），并以 jar 内容审计确认部署产物正确。

### 第三轮体验走查修复（同日，玩家旅程代码追踪）

逐旅程追踪（切菜→烹饪→吃→效果→盛宴→篮子→重登）发现并修复 6 个实际游玩缺陷：
1. **盛宴放置显示空盘**（变体默认序）→ 放置时自动设为满份（s4/s8）
2. **舒适/滋养重新登录后失效**（内存态与 PDC 脱节）→ 重写为纯 PDC 驱动，重登/重启后持续生效
3. **篮子 GUI 开启时吸取导致物品丢失**（GUI 快照覆盖竞态）→ GUI 打开期间暂停该篮子的自动吸取
4. **炉灶烤熟弹出方向硬编码** → 读取方块 facing 属性
5. **刀切派错误返还整个派** → 改为只掉落剩余切片（mod 行为）
6. **锅/煎锅放炉灶上无托架视觉** → 放置时检测下方炉灶自动切换 tray 变体

### 名字与完整性审计（2026-08-18 第二轮反馈修复）

- 名字缺陷根因：block 绑定物品曾引用 `item.*` 语言键（注册时序缺陷）。现以 **mod 语言文件为唯一事实来源**解析键名（mod 有 block 键→block 键，否则 item 键），并为全部 CE 方块生成 `block_name:` 快捷键（73 条）
- 审计结果：**158/158 物品**（0 缺失 0 多余）、**90/90 方块**（42 CE block + 48 furniture）、**0 缺失模型文件**、**0 缺失音效**、**0 未解析名字**、**7 分类 158/158 覆盖**

### ⚠️ 有意偏差（平台限制，已用近似体验补偿）
| # | 偏差 | 原因 | 补偿方案 |
|---|---|---|---|
| 1 | 舒适/滋养无 HUD 图标 | 服务端插件无法渲染 HUD | actionbar 提示开始/进度 |
| 2 | 告示牌为聊天输入 | 无原版编辑 GUI | 逐行输入，done/cancel 结束 |
| 3 | 原版汤类堆叠 16 未实现 | 无法改原版物品组件 | —（FD 碗餐不受影响） |
| 4 | 村庄堆肥小屋未注入 | 结构池运行时修改风险 | 野生作物已覆盖获取途径 |
| 6 | 绳子攀爬为速度模拟 | 家具非方块无 climbable 标签 | 每 tick 上浮 0.13/潜行悬停 |
| 7 | 手持煎锅右键启动 | 无法检测按住右键时长 | actionbar 进度条+完成音效 |
| 8 | 附魔台获取背刺依赖组件 | 附魔效果组件需数据包 | 刀具已带 enchantable 组件，铁砧+附魔书可用 |

## 4. 安装与验证（当前版本 1.0.4）

**部署**：将 `papo-plugin/build/libs/farmersdelight-papo-1.0.4.jar` 放入 plugins/ 并删除旧版 jar，重启。
版本号变更会自动触发：CE 包重装 → 内容 reload → **资源包再生+上传**。

**客户端测试清单**（GUI 层 v1.0.3/1.0.4 重构后）：
| 方块 | 预期 | GUI 提供方 |
|---|---|---|
| 任意橱柜（9 种木）/ 篮子 | 右键开 27 格容器（标题为方块名，开门音效） | **CE 原生** simple_storage_block |
| 烹饪锅 | 右键开 GUI：优先专用布局（6 输入+餐盘+碗+输出），最差情况 9 格 CE 原生界面 | 三重保障（同一库存） |
| 砧板/煎锅/绳子/盛宴/告示牌 | 右键交互（放物/切割/切菜等） | 双路径 |
**诊断日志已内置**（v1.0.4+）：用户每次右键家具时服务端日志会输出：
- `[PATH-A] CE furniture event: <id>` —— CE 包级事件路径触发
- `[PATH-B] Bukkit entity interact fallback: <id>` —— Bukkit 兜底路径触发
- `[GUI] opened for <玩家> title='<标题>' holder=<容器类> size=<格数>` —— 任何 GUI 实际打开（含 CE 原生）

一次右键即可定位：无任何行=事件未达服务端（客户端/网络层）；只有 PATH-A/B 无 GUI=路由到了但打开失败（有异常栈）；
有 [GUI] 行=GUI 确实打开（若客户端仍看不见则是客户端渲染问题）。
若橱柜/篮子正常而烹饪锅只出现 9 格普通界面：说明专用 GUI 的事件路由未生效但兜底工作——报告即可精修。
若全部正常但家具仍不可见：属渲染链问题（1.0.2 已对齐 bench 模式），请清理客户端资源包缓存后重进。

## 4a. 历史（原安装说明）

1. 服务器放 `craft-engine-bukkit-26.7.4`（paper-loader 版）与 `farmers-delight-papo-1.0.0.jar` 于 plugins/
2. 首次启动：插件自动解包 ce-pack 到 `plugins/CraftEngine/resources/farmersdelight/` 并触发 CE reload；玩家进服会收到 CE 生成的资源包
3. 验证命令：`/ce item give @s farmersdelight:iron_knife`（或创造模式背包 CE 浏览器）

### 运行时冒烟测试结果（2026-08-18，实机验证）

环境：Papo 1.21.11-0.32.1（`REF/Papo/paper-server/build/libs`）+ 本地构建的
craft-engine-paper-plugin-26.7.4.jar（`REF/craft-engine` `:bukkit:paper-loader:shadowJar`）+ 本插件 jar。

| 验证项 | 结果 |
|---|---|
| 插件启用 | ✅ `Enabling FarmersDelight v1.0.0`，27 烹饪 + 106 切割 + 35 堆肥配置加载 |
| CE 包安装 | ✅ 自动解包到 `resources/farmersdelight/`，CE 识别为独立 pack |
| CE 内容计数 | ✅ items 343（=CE默认109 + FD 158 + 内部展示76）、furniture 54（=6+48）、blocks 82（=40+42）、recipes 193（=45+148）、lang 7402、sounds 13 —— 全部精确对账 |
| 方块状态分配 | ✅ `cache/custom_block_states.json` 为 FD 分配 225 个状态 ID |
| 资源包生成 | ✅ `ce upload` 成功（所有贴图/模型引用有效） |
| 进度系统 | ✅ 数据包加载 1606（+22）、插件解析 22/22、0 错误 |
| 错误/警告 | ✅ 0 ERROR、0 与 FD 相关的 WARN |

冒烟测试过程中发现并修复的 6 个真实缺陷：
1. GameTicker 字段初始化器在构造器赋值前调用 `plugin()` → NPE（`Plugin cannot be null`）
2. variants 多属性键含空格（`facing=north, open=false`）→ CE 无法解析（144 个警告）
3. lang 手工附加键与 mod 语言键重复定义 → CE 重复段警告
4. `reloadPlugin` 第三参数（reloadRecipes）误传 false → 148 条配方未随包加载
5. 有序配方键 `#:` 被 YAML 当作注释 → 22 个配方损坏（含嵌套 OR 组未展开 → air 原料）
6. smithing 配方字段名应为 `template_type` 而非 `template`

## 5. 再生成资源包

```
cd tools && python -m ce_gen.main   # 源：mod 的 assets/data + Java 枚举解析
```
生成器包含：注册表解析（Items/Foods/Blocks）、通用 blockstate 转换器、家具定义、配方转换（标签递归展开）、音效/语言迁移。
