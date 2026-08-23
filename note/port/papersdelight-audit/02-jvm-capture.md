# PapersDelight JVM 层动态捕获 — 基础设施与首轮发现

> 日期：2026-08-23 晚
> 方法：自写探针插件 `tools/fd-probe`（Byte Buddy retransform + bootstrap sink + Bukkit 事件追踪），
> 对 REF 插件（PapersDelight-vNext PRE-1，混淆类 `NMSL_.*` / `cn.dg32z.*`）做方法入口级调用流捕获。

## 基础设施（已验证可用）

- **构建**：`tools/fd-probe`（Gradle，Java 21 工具链，byte-buddy 1.17.5 shaded）
  - 产物 1 `fd-probe-sink.jar`：JDK-only 日志接收器，运行期 `appendToBootstrapClassLoaderSearch` 注入 bootstrap，
    advice 内联代码在【被插桩类】的加载器内解析它（避免类双载状态分裂）
  - 产物 2 `fd-probe-0.1.0.jar`：主插件（内嵌 sink jar 资源 + shaded byte-buddy）
- **服务器启动需加** `-XX:+EnableDynamicAgentLoading`（自附加 agent）
- **命令**：`/fdprobe inst`（安装插桩）`/fdprobe arm <ms>`（开窗捕获后自动 disarm+flush）
  `/fdprobe status` `/fdprobe ev on|off` `/fdprobe mark <text>`
- **产物**：`plugins/FdProbe/capture.log`（方法流，含线程号+纳秒时间戳+参数摘要）、`events.log`（Bukkit 事件流）
- **验证**：`pd reload` 一次触发捕获 855 行完整方法流，0 错误，JNIC 保护未拦截 retransform

## 首轮发现（pd reload 流）

1. **命令链**：`NMSL_.dmexecute → b33onCommand → b33d(sender,"papersdelight.reload")`（权限检查）
2. **配方来源确认为空**：重载结束打印的配方注册结构 `ut[v1={}, vQ=[], vb=[], vt=[], vg=[], vl=[], vX=[]]` —— 8 个空集合
3. **`NMSL_.nyQ(plugins\PapersDelight\delights, true)`**：插件会扫描 `plugins/PapersDelight/delights/` 目录
   —— legacy delights 附属格式（config: `compatibility_legacy_delights: true`）
4. **官方更新日志（文档仓库）确认 delights 附属系统**：
   - 启动扫描 `delights/` 下的 `delight.yml`，按 `priority` 排序加载各附属 tags（多附属 tags.yml 合并）
   - 每附属 `delight.yml` 设 `advancement: true` 可启用独立进度树（内置农夫/饺子/末地三棵）
   - 附属（饺子乐事/末地乐事）经付费售后群分发，无公开下载 → 格式需自行喂样例逆向
5. **新格式 = CE 配置解析器**：插件向 CraftEngine 注册 `papersdelight_recipes` / `papersdelight_enchantments` /
   `papersdelight_advancements` / `advanced_tags` 四个解析器（generation 1）—— CE 包 YAML 里应有同名配置段
6. **Folia 兼容**：内部用 `FoliaAsyncScheduler`（io.papermc.threadedregions）调度
7. **进度树**：`n_y(logger, {}, 关闭未使用的进度树)` —— 空配置时关闭未用进度树

## 官方文档（已下载 REF/papersdelight-docs/）

- 行为契约：advanced_crop（age/grow_speed/light_requirement/is_bone_meal_target/bone_meal_age_bonus "1~3"/
  soils[{block,growth_modifier,bonemeal_chance}]）、double_crop、roped_crop、horizontal_double_block、
  pairable_block、farmland、high_temperature、custom-effect-functions、is-sneaking-condition
- 安装文档提及 CE 26.7.4 时代；实测组合用 26.8.1 通过

## 下一步

- 喂样例逆向 `papersdelight_recipes` 解析器 schema（在 CE 包加测试段 → arm + reload → 看报错收敛格式）
- delights/ 目录实验：放最小 delight.yml 观察解析流
- 逐模块捕获（锅/砧板/煎锅/炉灶/篮子/作物/效果/附魔）→ 规格文档
