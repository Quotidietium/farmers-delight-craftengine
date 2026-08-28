# 移植主线收敛报告（1.3.1，2026-08-28）

> 目标：将 Farmer's Delight mod（farmers-delight-fabric 1.20.1-1.4.3，本仓库 src/）
> 复现为 Papo（Paper 1.21.11）+ CraftEngine 插件。规格仲裁：**mod 源码为唯一规格**，
> REF（papersdelight 反编译/CE 包 v0.10.6）仅作实现方法参考。
> 本报告为移植主线的收敛总结；逐迭代细节见 note/release/ 与 git 历史。

## 版本轨迹

| 版本 | 内容 |
|---|---|
| 1.0.0-1.0.5 | 全量重写（158 物品/42 方块/48 家具/148 配方/22 进度）+ GUI 五轮收敛 |
| 1.0.6-1.1.0 | 煎锅渲染五轮修正（bench 同构/轴心语义） |
| 1.2.0 | REF 源码对照：机制违规修复（堆肥/砧板/煎锅/锅）、锅漏斗+CopyMeal、作物精确公式、原版作物上沃土、锅 GUI 进度条+配方书、REF 模型覆盖层 |
| 1.2.1 | 性能三轮 R1-R3（配方匹配 4-8x、ticker 2.15x，benchmark/ 量化） |
| 1.2.2 | 炉灶滋滋环境音 + 堆肥比较器（comparator_signal 行为） |
| 1.3.0 | 烹饪锅家具→真实 CE 方块（mod 比较器公式+全功能迁移+legacy 兼容） |
| 1.3.1 | 派比较器 + 盛宴×5 迁块（取食/份数/掉落表全按 mod）——**比较器矩阵闭合** |
| 1.4.0 | 背刺改数据包附魔——附魔台获取确定性实现，偏差⑦闭合 |

## 验证证据链

- 每轮服务器冒烟：内容对账（items 357 / blocks 91 / furniture 48 / recipes 193 /
  advancements 22/22）、0 错误、资源包生成上传全绿
- 交互一致性红线：R1 配方匹配 40,000 次模糊 parity gate PASS；
  机制改动均以 mod 源码逐行钉死后实现
- 性能：`benchmark/` 服务器内基准 + `-Dfd.legacy-tick` 同构建 A/B
  （ticker 11.2→5.2ms/脉冲；报告 note/report/perf/）

## 剩余偏差（7 项，处置材料 note/port/deviation-decisions.md）

①HUD 图标 ②告示牌编辑 GUI ③原版汤堆叠 ④村庄结构注入 ⑤绳子攀爬 ⑥煎锅按住检测
⑦附魔台背刺。**⑦复审更新**：1.2.x 起 Paper RegistryEvent 注册 + supportedItems
恰好为 CE 刀具底层材质 + 刀具 enchantable:14 组件，附魔台三要素已齐备，
应可在附魔台出现背刺候选——1.0.0 时代的「依赖数据包组件」判断已过时，
状态从「永久偏差」改为「**待真人客户端进附魔台实测**」（实测步骤见
note/port/CLIENT_TEST.md §附魔台）。其余 6 项为原版客户端/注册表/世界生成层
硬限制，建议关闭（⑤如坚持可按 deviation-decisions.md 的迁移路径排期）。

## 待办

1. 真人客户端回归（note/port/CLIENT_TEST.md）：GUI/交互/附魔台三组步骤
2. 偏差表处置：⑦已于 1.4.0 实现闭合；剩余 6 项为硬限制偏差，待确认关闭
   （⑤绳子迁移路径已在 deviation-decisions.md 备案，如需可另行排期）
