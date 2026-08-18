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

### ⚠️ 有意偏差（平台限制，已用近似体验补偿）
| # | 偏差 | 原因 | 补偿方案 |
|---|---|---|---|
| 1 | 舒适/滋养无 HUD 图标 | 服务端插件无法渲染 HUD | actionbar 提示开始/进度 |
| 2 | 告示牌为聊天输入 | 无原版编辑 GUI | 逐行输入，done/cancel 结束 |
| 3 | 原版汤类堆叠 16 未实现 | 无法改原版物品组件 | —（FD 碗餐不受影响） |
| 4 | 村庄堆肥小屋未注入 | 结构池运行时修改风险 | 野生作物已覆盖获取途径 |
| 5 | 进度系统未移植 | 自定义判据需数据包 | 后续可用 bundled datapack 补 |
| 6 | 绳子攀爬为速度模拟 | 家具非方块无 climbable 标签 | 每 tick 上浮 0.13/潜行悬停 |
| 7 | 手持煎锅右键启动 | 无法检测按住右键时长 | actionbar 进度条+完成音效 |
| 8 | 附魔台获取背刺依赖组件 | 附魔效果组件需数据包 | 刀具已带 enchantable 组件，铁砧+附魔书可用 |

## 4. 安装与验证

1. 服务器放 `craft-engine-bukkit-26.7.4`（paper-loader 版）与 `farmers-delight-papo-1.0.0.jar` 于 plugins/
2. 首次启动：插件自动解包 ce-pack 到 `plugins/CraftEngine/resources/farmersdelight/` 并触发 CE reload；玩家进服会收到 CE 生成的资源包
3. 验证命令：`/ce item give @s farmersdelight:iron_knife`（或创造模式背包 CE 浏览器）

## 5. 再生成资源包

```
cd tools && python -m ce_gen.main   # 源：mod 的 assets/data + Java 枚举解析
```
生成器包含：注册表解析（Items/Foods/Blocks）、通用 blockstate 转换器、家具定义、配方转换（标签递归展开）、音效/语言迁移。
