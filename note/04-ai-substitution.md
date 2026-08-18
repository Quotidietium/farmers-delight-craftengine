# 04 — AI 辅助开发与替代可行性评估

> 评估对象：本仓库（Farmer's Delight Fabric 移植版）的**开发工作流**各环节。Minecraft 模组的"运行时"无法被 AI 替代（游戏内逻辑必须由 Java 代码执行），因此评估聚焦于：哪些开发任务可交给 AI 全自动完成、哪些适合 AI 起草+人工审核、哪些必须人工主导。

评分维度（各 1-5，总分 30）：确定性 / 输入结构化 / 安全风险 / 领域复杂度 / 上下文需求 / 重复性。
分级：24-30 🤖 完全 AI 化；15-23 🧑‍💻 AI 辅助；6-14 👤 人工主导。

## 1. 模块级评估

### 1.1 数据包内容编写（配方/战利品/进度/标签 JSON）— 🤖 24/30

| 维度 | 分 | 依据 |
|---|---|---|
| 确定性 5 | JSON schema 固定（`CookingPotRecipeSerializer.java:29-47`、`CuttingBoardRecipeSerializer.java:41-61`） |
| 结构化 5 | 输入=「想做什么菜」，输出=严格 schema |
| 安全 4 | 错误仅导致配方加载失败（JsonParseException），易回滚 |
| 领域 3 | 需懂 MC 物品 ID/标签体系与原版/模组内容对照 |
| 上下文 4 | 需引用 `data/` 现有 293 配方的模式与 lang 键 |
| 重复 3 | 新增一批食物时高度重复 |

**方案**：AI 按现有 JSON 模式成套生成 `recipes/cutting/`、`recipes/cooking/`、`loot_tables/blocks/`、`advancements/recipes/`、`lang/en_us.json`+`zh_cn.json`、`models/*/`。已有 `craftengine-skill` 类似的「Wiki+模板驱动生成」先例可复用思路。
**人工审查点**：物品 ID 是否存在、平衡性数值、`vanilla_crates_enabled` 等条件引用。

### 1.2 注册表扩容（registry/ 枚举 + blockstates/models）— 🤖 23/30（接近线）

- 模式极其规整：`BlocksRegistry`/`ItemsRegistry` 枚举项 `(path, Supplier)` + 对应 blockstate/model/lang JSON（90 方块/158 物品全走同一模板）。
- AI 可根据「新增方块 X」自动补齐：枚举项、blockstate JSON、block model、item model、loot table、lang 键、（如需）tag 归类。
- 审查点：渲染层注册（`BlocksRegistry.registerRenderLayer`）、可燃物注册是否需要。

### 1.3 版本移植 / Mixin 维护 — 🧑‍💻 17/30

- 这是本仓库历史最高频工作（git log 大量 1.19→1.20 迁移提交）。yarn 映射改名导致编译错误**定位高度机械**（确定性 4），但 Mixin 注入点语义（`BeforeInc` 字节码匹配、`@ModifyVariable argsOnly` 等）要求深入 Mixin 机制（领域复杂度 2），且改错会崩游戏于运行期而非编译期（安全 2）。
- **方案**：AI 起草映射名替换与注入点适配 → 人工在 `runClient` 实测三条烹饪管线 + 25 个 Mixin 覆盖的游戏行为。
- 已有可参照资产：3 个 `@Deprecated` Migration mixin 标注了「since 1.4.0」的移除路径，AI 可自动完成删除。

### 1.4 新方块/方块实体游戏逻辑 — 🧑‍💻 19/30

- 有大量可套用的模板：`SyncedBlockEntity` + `ItemStackInventory` + Ticker + `onUse` 分支模式（见 `note/02-operation-principles.md` §3-6 的标准链路）。
- AI 起草类骨架与 NBT 读写，人工审查：`canPlaceAt`/邻居更新边界、双端逻辑划分（server/client）、同步策略。
- 复杂度来自 MC 生命周期语义（随机刻 vs 计划刻 vs Ticker），错误表现为诡异的世界损坏（安全 2）。

### 1.5 Bug 修复与代码审计 — 🧑‍💻 16/30

- 本报告已发现的可验证清单（可直接作为 AI 修复任务输入）：
  1. `CabinetBlockEntity.onClose:107-111` 疑似误调 `openContainer`；
  2. `item/material/FlintMaterial.java` 死代码删除；
  3. `CookingPotScreenHandler.java:84` 客户端 PropertyDelegate 尺寸对齐；
  4. `worldgen/configured_feature/patch_{brown,red}_mushroom_colony.json` 未挂接——补 placed_feature + BiomeModifications 或删除；
  5. CircleCI release 的 develop 分支问题。
- 无测试兜底（安全 2），修复必须附人工游戏内验证步骤。

### 1.6 架构决策 / 平衡性设计 / MC 版本升级策略 — 👤 12/30

- 涉及项目方向（如是否跟进 1.21、是否实现 Forge 版未移植的手持视觉烹饪、数值平衡），输入开放、后果长期，AI 仅提供选项分析。

## 2. ROI 优先级矩阵

| | 低难度 | 高难度 |
|---|---|---|
| **高收益** | 🥇 **数据包成套生成**（1.1）、**注册表扩容脚手架**（1.2）、**lang 多语言补齐** | 🥈 **Mixin/版本迁移辅助**（1.3）、**BE 逻辑模板化起草**（1.4） |
| **低收益** | 🥉 已知死代码/疑似 bug 清单执行（1.5 前两项） | 📋 暂缓：从零补测试体系（依赖 runClient 手工验证的现状短期难改） |

## 3. 推荐路线图

### Phase 1 — Quick Win（立即可做）
1. 用 AI 按 `data/` 现有模式批量生成新内容（配方+语言+模型+进度四件套），人工只审 ID 与数值。
2. 执行 1.5 中的死代码删除与 `onClose` 修复（一次 commit 一个，便于回滚）。

### Phase 2 — Strategic（本季度）
3. 沉淀「方块/BE 脚手架」提示模板：输入=功能描述，输出=`block/`+`entity/block/`+registry+loot+lang 骨架（以 `note/02` 的标准链路为规范）。
4. 版本迁移辅助流：编译错误清单 → AI 按 yarn diff 批量改写 → 人工聚焦 25 个 Mixin 的语义复核。

### Phase 3 — Transformative（远期）
5. 为三条烹饪管线补最小可测抽象（把 `cookingTick` 的配方匹配/产出计算提取为纯函数），使 AI 生成单测成为可能。
6. 视项目方向决定是否引入自动化游戏内冒烟测试（如 GameTest 框架）。

## 4. 关键约束（对任何 AI 辅助流程）

- **禁止运行项目验证**：静态分析阶段不得执行 `runClient`；涉及运行验证的任务必须输出「人工验证步骤清单」。
- **数据契约**：改 `data/` 必须对照 `note/03-workflow.md` §2 的「代码↔数据契约速查表」双向同步（尤其 tag ↔ `TagsRegistry`、rarity ↔ `chanceWild*`）。
- **版本号纪律**：涉及发布时 `gradle.properties#version` 与 `note/release/<版本号>.md` 需同步（gradle-release-plugin 也会自动改 version，避免双写冲突）。
