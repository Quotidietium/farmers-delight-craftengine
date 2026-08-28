# 移植主线完成度审计（对照目标逐句）

> 完成日期：2026-08-28 ｜ 基线：commit 6c4f16b（1.4.1 已发布）
> 目标原文要求与证据逐条对照。状态：✅ 完成 / 🔶 平台硬限制（永久偏差，补偿在位）/ ⏳ 依赖外部资源（真人客户端）

## 目标要求对照

### 「参考 REF\papersdelight.jar 反编译后的源代码」
✅ `REF/papersdelight.jar`（无加密早期版）→ Vineflower 反编译 101 文件 → `REF/papersdelight-src/`
✅ 两份盘点（源码功能清单 16 行为/管理器调度表；CE 包 v0.10.6 全量配置清单）
✅ REF 实现方法被采纳的位置：comparator 行为回调签名、种子重定向、GUI 资产、
   炉灶环境音参数、数据包附魔等价物（commit 3e7272b/1370feb/7d11a6a/924937a）

### 「结合新版的文档」
✅ `REF/papersdelight-docs/` 行为契约（advanced_crop/double_crop/roped_crop 等 10 篇）
   用于核对行为参数（grow_speed/soils/bone_meal_age_bonus 等公式与默认值）

### 「继续从 MOD 移植到当前插件」
✅ 1.2.0：堆肥/砧板/煎锅/锅四处 mod 违规修复（mod 源码逐行钉死）
✅ 1.2.0：锅漏斗三面交互、CopyMeal/CopySkillet、同型续煮、残留弹出、经验小数进位
✅ 1.2.0：作物生长改原版 CropBlock 精确公式（肥力环/十字减半/1/(⌊25/f⌋+1)/光照 9）
✅ 1.2.0：锅 GUI 22 段进度条+热源指示+配方书（列表+详情）
✅ 1.2.2：炉灶滋滋环境音（lit 随机间隔）+ 堆肥比较器（8−阶段）
✅ 1.3.0/1.3.1：锅与盛宴×5 家具→真实 CE 方块迁移（存储/交互/掉落/GUI/比较器全功能，
   legacy 家具路径兼容）；派比较器（3−bites）——**mod 比较器 6 类全部闭合**
✅ 1.4.0：背刺改数据包附魔（附魔台候选确定性；成本曲线/等级/权重按 mod；
   服务端验证 `Backstabbing datapack enchantment: resolved`）
✅ 1.4.1：高级作物 8/8（茎×2 走 CE 内建 stem_block+attached×2、火炬花/瓶子草
   骨粉固定+1、掉落表按原版），mod「任意作物可种沃土」mixin 全覆盖

### 「材质方面请你根据 REF 下的 CE 包，对现有的 CE 包的模型做调整」
✅ REF 编辑过的 76 个模型+39 贴图 vendor 进 `tools/ce_gen/assets_ref`，
   生成时覆盖 mod 原始资产（盛宴美术/托架/榻榻米/沃土耕地模板/display parent）
✅ REF 自创内容（竹篮重制）按「mod 为准」仲裁排除

### 「项目整体可以参考 REF 下的已有项目」
✅ CE 源码（REF/craft-engine 26.7.4）确认行为回调/API 可用性（comparator/stem_block/
   state: 外观/事件无 item() 访问器等决策均有源码依据）

### 「持续迭代，直到完美复现」
✅ 16 轮功能/性能迭代（1.0.0→1.4.1，14 个 GitHub Release，全部含 jar 附件）
✅ 冒烟：blocks 97 / items 336 / furniture 48 / recipes 193 / 进度 22/22 / 0 错误
✅ 红线量化：R1 parity gate 40000/0；机制改动均先以 mod 源码钉死再实现；
   性能 A/B 用 `-Dfd.legacy-tick` 同构建对照
✅ 性能：ticker 11.2→5.2ms/脉冲（2.15x）、配方匹配 4-8x（benchmark/ 可复现）
✅ 收敛材料：final-report.md（版本轨迹/验证链/偏差终局）、deviation-decisions.md
   （7 项逐项论证）、CLIENT_TEST.md（三组真人步骤 + 一键回归工具 regression.py）

## 偏差终局（无未决项）

| 项 | 结论 |
|---|---|
| ①HUD 图标 ②告示牌编辑 GUI ③原版汤堆叠 ④村庄结构注入 ⑥煎锅按住检测 | 原版客户端/注册表/世界生成层硬限制，永久偏差，补偿在位 |
| ⑤绳子攀爬 | mod 无此功能（RopeBlock extends PaneBlock 无 climbable）——我方实现为附加功能，非差距 |
| ⑦附魔台背刺 | 1.4.0 数据包附魔实现，服务端实证 resolved；客户端显示层为最后记录点 |

## 遗留（不阻塞主线，记录在案）

1. 真人客户端回归（CLIENT_TEST.md 三组步骤 / `python tools/client-test/regression.py`）：
   附魔台候选显示、交互手感——结果仅用于记录。
2. ⑤绳子 CE 方块迁移路径已备案（deviation-decisions.md），如需排期为独立迭代。
