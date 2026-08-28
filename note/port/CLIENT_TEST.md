# 客户端验证指引（v1.0.4，约 3 分钟）

> 本文件面向持有 Minecraft 1.21.11 客户端的测试者。服务端一切可验证项已由自动化探针确认，
> 剩余的「右键 → GUI 显示」需要真实客户端闭环。

## 步骤

1. **部署**：把 `papo-plugin/build/libs/farmers-delight-papo-1.0.4.jar` 复制到服务器 `plugins/`，
   **删除**旧版 farmers-delight jar（1.0.0~1.0.3 任意版本），重启服务器。
   首启会自动：重装 CE 包 → reload → **再生资源包**（`plugins/CraftEngine/generated/resource_pack.zip` 应出现且时间戳为本次启动）。

2. **进入客户端**（离线模式服务器直接进；首次会提示接受资源包，选**接受并启用**；
   若之前拒绝过：选项→资源包→把服务器包设为启用，或删除客户端缓存后重进）。

3. **右键测试**（依次）：
   - 任意**橱柜**（如橡木橱柜）→ 预期：27 格容器界面 + 开门音效
   - **篮子** → 预期：27 格容器界面
   - **烹饪锅**（可先 `/fdplace cooking_pot ~ ~ ~` 放一个）→ 预期：专用布局（6 输入+餐盘+碗+输出）；最差情况 9 格普通界面（兜底）

4. **读日志**：运行 `tools/client-test/fd-watch.bat`（或直接看 `logs/latest.log`），关注三类行：
   ```
   [PATH-A] CE furniture event: farmersdelight:cooking_pot player=...
   [PATH-B] Bukkit entity interact fallback: farmersdelight:cooking_pot player=...
   [GUI] opened for ... title='...' holder=... size=...
   ```

## 结果判读

| 现象 | 结论 | 下一步 |
|---|---|---|
| 全部 GUI 正常 | 完成 | — |
| 橱柜/篮子正常，锅只有 9 格普通界面 | 兜底生效，前两条路由未触发 | 把日志三行发给助手，修事件路由 |
| 某方块右键无反应且日志无 PATH 行 | 交互包未达服务端 | 检查准星是否对准方块/判定区 |
| 有 PATH 行但无 [GUI] 行 | 打开调用抛异常 | 日志会带异常栈，发给助手 |
| 有 [GUI] 行但界面看不见 | 客户端渲染问题 | 清客户端资源包缓存重进 |

## 快速排障

- 家具不可见：删除客户端该服务器资源包缓存后重进（1.0.2 已切换为 CE bench 同构渲染链）
- 资源包未更新：服务器控制台执行 `ce reload pack`


## 附魔台实测（1.3.1+，验证偏差表⑦是否已随 1.2.x 附魔注册方式修复）

1. `/ce item give @s farmersdelight:iron_knife` ×2，另备青金石+等级
2. 放置附魔台（周围建议 15 书架），手持刀具打开附魔台
3. 预期：三个候选中出现「背刺」（Backstabbing / 背刺）
4. 附魔后手持刀具背对生物攻击：伤害应 ×(1.2+0.2×等级) 并有暴击音
5. 若候选未出现：记录附魔台界面截图，反馈给开发者（备选路径=数据包附魔重做，
   见 deviation-decisions.md ⑦）
