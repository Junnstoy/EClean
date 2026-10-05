# 功能权限与 PlaceholderAPI

三个功能分别授权。每个权限同时允许该功能的开关、状态和统计；命令帮助及 Tab 补全也按相同权限过滤。

| 权限 | 允许的命令 | 默认值 |
| --- | --- | --- |
| `eclean.worldrules` | `/eclean worldrules on\|off\|status` | false，由 `eclean.admin` 继承 |
| `eclean.redstone` | `/eclean redstone on\|off\|status`、`/eclean redstone stats [世界名]`，以及旧写法 `/eclean redstone [世界名]` | false，由 `eclean.admin` 继承 |
| `eclean.chunkunload` | `/eclean chunkunload on\|off\|status`、`/eclean unloadstats` | false，由 `eclean.admin` 继承 |

`eclean.admin` 保持默认 `op`，在 `plugin.yml` 声明上述三个子权限。普通玩家可以只获得其中一个；显式将子权限设为 false 会拒绝该功能，不额外通过 admin 或 OP 绕过拒绝。原有管理命令仍使用 `eclean.admin`，垃圾桶权限 `eclean.trash` 不变。

## 新增 17 个占位符

需安装 PlaceholderAPI。EClean 内置注册扩展，无需另外下载 eCloud 扩展；没有安装 PlaceholderAPI 时，三个功能和权限仍可使用。原有七个占位符保持不变。

| 占位符 | 返回值及含义 |
| --- | --- |
| `%eclean_worldrules_enabled%` | `true` / `false`，世界及实体/材料覆盖规则的配置开关 |
| `%eclean_worldrules_worlds%` | `living.worlds`、`drop.worlds`、`chunk.worlds` 中世界名的去重数量；关闭覆盖规则时仍保留该数量，不代表已加载世界数 |
| `%eclean_redstone_enabled%` | `true` / `false`，红石监控的配置开关 |
| `%eclean_redstone_running%` | `true` / `false`，红石监控是否实际运行 |
| `%eclean_redstone_mode%` | 配置的 `report` / `suppress`；关闭功能时仍显示配置值 |
| `%eclean_redstone_tracked%` | 当前仍保留的方块监控记录数 |
| `%eclean_redstone_events%` | 本次运行累计接收的有效电流变化事件数，包含达到容量上限后未跟踪的事件；忽略世界及电流未变化事件不计入 |
| `%eclean_redstone_untracked%` | 本次运行因记录容量上限而未跟踪的事件数 |
| `%eclean_redstone_suppressed%` | 本次运行 EClean 实际将上升电流改回旧值的事件数；后续监听器仍可能修改事件 |
| `%eclean_chunkunload_enabled%` | `true` / `false`，闲置区块卸载的配置开关 |
| `%eclean_chunkunload_running%` | `true` / `false`，卸载扫描是否实际运行 |
| `%eclean_chunkunload_protection%` | 当前服务端的区块保护能力路径，见下表 |
| `%eclean_chunkunload_scanned%` | 本次运行累计检查的候选次数，重复检查同一区块也计数 |
| `%eclean_chunkunload_skipped%` | 本次运行因不满足安全检查而跳过的次数；尚未达到闲置时间的候选不计入 |
| `%eclean_chunkunload_attempted%` | 本次运行累计调用服务端卸载请求的次数 |
| `%eclean_chunkunload_accepted%` | 服务端接受的卸载请求数，**不代表实际已卸载区块数** |
| `%eclean_chunkunload_pending%` | EClean 本轮候选队列中尚待检查的数量，**不是服务端卸载队列长度** |

`chunkunload_protection` 返回以下原始字符串，功能关闭时仍可显示服务端能力：

| 返回值 | 含义 |
| --- | --- |
| `player viewers` | 支持区块观察玩家 API，结合玩家距离、强加载和插件票据检查 |
| `empty worlds only (viewer API unavailable)` | 有插件票据但缺少观察玩家 API，仅处理无玩家世界 |
| `legacy isChunkInUse + player radius` | 使用旧版 `isChunkInUse` 和玩家距离，兼顾可用的强加载能力 |
| `unavailable` | 缺少所需安全检查 API，不启动卸载扫描 |

## 更新与重置

新增占位符均为全服只读数据，不需要传入玩家，也没有单独的读取权限。计分板、菜单或聊天插件若需要限制谁能看到这些数值，应在显示端控制。

EClean 在主线程每 20 tick 发布一次不可变快照；占位符读取只访问快照，不遍历世界或区块，可供异步调用。低 TPS 时实际刷新间隔会超过一秒。命令切换或 EClean 配置重载成功后立即刷新；同次批量读取若跨越一次发布，可能取得相邻两份快照。

停止、重新启动服务、配置重载和重复执行 `on` 都会清空对应运行计数；关闭后计数为 0，不跨重启保存历史。`enabled` 表示配置，`running` 表示运行状态，两者可能不同。插件禁用时取消采样任务并清空快照；`/papi reload` 后扩展继续有效。未知占位符不解析。

例如在游戏中验证：

```text
/papi parse me %eclean_worldrules_enabled%
/papi parse me %eclean_redstone_suppressed%
/papi parse me %eclean_chunkunload_accepted%
```

这些统计不表示 CPU 耗时、TPS 收益或真实已释放的内存。兼容范围和实测场景见 [测试报告](compatibility-test-report.md)。
