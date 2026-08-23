# PapersDelight 黑盒审计 — 环境装配与首启记录

> 日期：2026-08-23
> 目标：以 REF 两件套（PapersDelight-vNext PRE-1.jar + 农夫乐事 CE内容包 v0.10.6）的**实测行为**为验收标准，
> 黑盒测试其全部功能后修改本项目（papo-plugin + ce_gen）对齐。jar 已混淆，**禁止反编译**，只做实机黑盒观测。

## 决策记录（用户已确认）

1. **规格仲裁**：REF 实测行为为准；仓库内原 mod 源码（farmers-delight-fabric 1.20.1）仅作机制理解参考。
2. **CraftEngine 版本**：`craft-engine-paper-plugin-26.8.1.jar`（用户放入实测环境，REF 组合即搭配此版实测通过）。
3. **环境**：`F:\paper-test-1.21.11`（Paper 1.21.11-132，离线模式，RCON 25575 密码 balatro220）。
4. **测试期间隔离的插件**（移至 `quarantine-fd-testing/`，可还原）：InteractionVisualizer、
   EvilEvents.jar + evilevents-1.0.0.jar、Slimefun 全家 6 jar（Slimefun/FoxyMachines/InfinityExpansion/
   LiteXpansion/SlimyTreeTaps/TranscEndence）、balatro-0.4.62。保留：EssentialsX、LuckPerms、Vault。
5. **专用世界**：`server.properties` 的 `level-name` 改为 `world_fd`（原 world/ 不动，事后改回）。

## 装配内容

- `plugins/PapersDelight-vNext PRE-1.jar`（复制自 REF/）
- `plugins/CraftEngine/resources/[本体内容] 农夫乐事 CE内容包 v0.10.6/`（复制自 REF/）
- `plugins/craft-engine-paper-plugin-26.8.1.jar`（用户放置）

## 首启结果（fd_boot_01.log / logs/latest.log）

| 项 | 结果 |
|---|---|
| 服务器 | ✅ Done (22.658s)，world_fd 新世界创建 |
| CraftEngine 26.8.1 | ✅ 加载，自动经 aliyun 镜像下载依赖；挂钩 LuckPerms/Vault |
| REF 内容包 | ✅ `已加载的包：[本体内容] 农夫乐事 CE内容包 v0.10.6。默认命名空间：farmersdelight` |
| PapersDelight | ❌ **许可证未配置**：`未配置许可证密钥，插件无法启动。请在 config.yml 中设置 license 或在 plugins/PapersDelight/ 下创建 license.dat` |
| zip file closed 异常 | 下游连锁：插件因许可证失败被禁用（classloader 关闭），CE reloadPlugin 遍历其注册的 `papersdelight_recipes` 解析器时读已关闭 jar 报错。**许可证到位即消失** |
| 其它 | 3 处 `No key layers in MapLike[{}]`（ServerMain 数据包阶段 1 次 + 启用阶段 2 次，待许可证后复查是否与 REF 有关） |

## 已获得的规格信息（PapersDelight 生成的配置，明文可读）

`plugins/PapersDelight/`：`config.yml`（config-version 11）、`gui.yml`、`lang/{en_us,zh_cn}.yml`。

config.yml 要点：
- `license`（空则不启动）、`lang: zh_cn`、`compatibility_legacy_delights: true`（旧 delights/ 格式兼容，后续移除）
- **热源体系**（厨锅/煎锅共用）：`heat_sources` 列表按 `material + states` 精确匹配，字段含
  `heat_source`（默认 true）/ `tray`（岩浆/营火/火焰等非炉灶热源需铁栅托盘才加热）/ `conductor`（传导器，
  如漏斗把下方热源传导到上方厨锅）/ `ce_block_tag`（按 CE 方块 tag 整组匹配）
- 默认热源：MAGMA_BLOCK、LAVA(tray)、FIRE(tray)、SOUL_FIRE(tray)、LAVA_CAULDRON、CAMPFIRE（lit=true 热+tray；lit=false 非热但 tray 视觉仍生效）
- 插件向 CE 注册自定义配置解析器 `papersdelight_recipes`（generation 1）——REF 包的 recipes.yml 之外还有插件侧配方格式

## 待办（被阻断）

- **等用户提供许可证密钥**（填 config.yml 的 license 或放 license.dat），之后重启做干净加载验证，进入黑盒基线盘点。
