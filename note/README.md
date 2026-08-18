# Farmer's Delight (Fabric 移植版) — 项目分析笔记

> 分析日期：2026-08-18
> 分析工具：codebase-analyzer（静态分析，未运行项目）
> 分析范围：整个仓库 `F:\Github\repo\farmers-delight-craftengine`

## 项目快照

| 项 | 值 |
|---|---|
| **项目** | Farmer's Delight 的 Fabric 移植版（原作为 Forge 模组，作者 vectorwing；移植版主要维护者 Zifiv） |
| **仓库命名** | 本地目录名为 `farmers-delight-craftengine`，但代码内容实为 `farmers-delight-fabric`（见 `gradle.properties:4-6`、`src/main/resources/fabric.mod.json` 的 `contact` 字段），仓库内**不含任何 CraftEngine 相关代码** |
| **Mod ID** | `farmersdelight`（`FarmersDelightMod.java:86`） |
| **Maven 组** | `com.nhoryzon.mc`，archivesBaseName `farmers-delight-fabric`（`gradle.properties:3-4`） |
| **当前版本** | `1.20.1-1.4.3`（`gradle.properties:2,9`；最新提交 `08a0ae2` release 准备） |
| **技术栈** | Java 17、Fabric Loader 0.14.21、Fabric API 0.85.0+1.20.1、yarn 映射 1.20.1+build.9、Gradle 8.1.1、fabric-loom 1.1.+（`build.gradle:1-8`、`gradle/wrapper/gradle-wrapper.properties`） |
| **代码规模** | 173 个 Java 文件（约 14,278 行）、1156 个 JSON、343 张 PNG、14 个 OGG 音频、5 个 NBT 结构 |
| **注册内容** | 90 个方块（`BlocksRegistry.java` 枚举项数）、158 个物品（`ItemsRegistry.java` 枚举项数） |
| **语言文件** | 29 个（`assets/farmersdelight/lang/`，含 zh_cn/zh_tw） |
| **Mixin** | 22 个通用 + 3 个客户端（`farmersdelight.mixins.json`） |
| **测试** | **无任何测试代码**（无 src/test，CI 的 `gradle test` 实际空跑） |
| **CI/CD** | CircleCI（`.circleci/config.yml`）：build+test → master 分支 release（gradle-release-plugin 自动打 tag/改版本）；deploy 与 SonarQube 任务已注释停用 |
| **分支策略** | 实际仅 `master`（CI 中 release 任务会 checkout `develop`，但当前仓库无 develop 分支——CI 配置与仓库现状不一致，见 `note/03-workflow.md`） |
| **贡献者** | 约 17 人；主力 KEVIN BEAUCORAL (150 commits)、CI Pipeline (49)、Zifiv (26) |
| **许可证** | MIT |

## 报告目录

| 报告 | 内容 |
|------|------|
| [01-architecture.md](01-architecture.md) | 分层架构、包职责、注册表体系、Mixin 清单、类继承体系、核心调用链、设计模式 |
| [02-operation-principles.md](02-operation-principles.md) | 模组启动序列、烹饪锅/砧板/煎锅/篮子等核心数据流、状态机、配置系统、错误处理 |
| [03-workflow.md](03-workflow.md) | 开发/CI/发布工作流、数据包（配方/战利品/世界生成）结构、核心业务流程决策树 |
| [04-ai-substitution.md](04-ai-substitution.md) | 各模块 AI 替代/辅助开发可行性评估与路线图 |
| [port/README.md](port/README.md) | **Papo (Paper 1.21.11) + CraftEngine 插件移植**：架构、安装、功能对照审计与偏差清单 |

## 核心发现（TL;DR）

1. **纯 Fabric 数据驱动型模组**：所有内容通过 16 个枚举式注册表类（`registry/` 包）在 `onInitialize` 中一次性注册（`FarmersDelightMod.java:104-118`），游戏性逻辑集中在「方块 + 方块实体」双层：方块处理交互与状态，方块实体在 Ticker 中处理每 tick 逻辑。
2. **三条烹饪管线**是模组的心脏：烹饪锅（6 输入槽无序配方匹配，`CookingPotBlockEntity.cookingTick:161`）、砧板（单输入 + 工具判定 + 概率产出，`CuttingBoardBlockEntity.processItemUsingTool:108`）、炉灶/煎锅（复用原版营火配方）。三者共享「lastRecipeID 缓存 + `RecipeManagerAccessorMixin.getAllForType` 直查」的性能优化模式。
3. **行为修改全靠 Mixin**：沃土种植、鸡/猪杂交、背刺附魔、汤碗堆叠、村庄结构池注入等 25 个 Mixin 是对原版的侵入面，升级 MC 版本时是最大的兼容风险点（其中 3 个 Migration mixin 已标记 `@Deprecated(forRemoval=true, since="1.4.0")`）。
4. **数据包占文件数大头**（1156 JSON）：293 配方（106 切割 + 27 烹饪锅 + 148 合成/熔炼 + 12 Create 联动）、100 战利品表、191 进度、19 世界生成、22 标签，另有 `minecraft:`/`c:`/`create:`/`dehydration:` 四个跨命名空间的兼容数据。
5. **已知问题/债务**：无测试；`CabinetBlockEntity.onClose:107` 疑似调用 `openContainer` 的笔误；`item/material/FlintMaterial.java` 为无引用死代码；`patch_brown/red_mushroom_colony` 两个世界生成 JSON 未被代码挂接；主类声明**未实现** Forge 版的「手持平底锅视觉烹饪」（`FarmersDelightMod.java:76-81`），也没有任何 Stencil 汤面渲染。

## 开发者快速上手

- **改内容**（新增食物/方块/配方）→ 动 `registry/` 枚举 + `assets/`（模型/贴图/语言）+ `data/`（配方/战利品/进度）。
- **改游戏逻辑** → `block/` + `entity/block/`（方块交互与每 tick 逻辑）、`item/`（物品行为）、`effect/`、`mixin/`（原版行为修改）。
- **改客户端表现** → `client/`（渲染器、HUD、粒子、界面）。
- **构建**：`gradlew build`（产物在 `build/libs/`）；**发布**：仅 master 分支上由 CI 执行 `gradle release` 自动升版本+打 tag。
- 详细蓝图见各分报告。
