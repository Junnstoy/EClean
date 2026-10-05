# 红石活动统计及可选高频抑制

`/eclean redstone on|off|status` 修改或查询开关，立即保存并生效。`/eclean redstone stats [世界名]` 查看统计；权限 `eclean.redstone`（`eclean.admin` 自动继承）。

配置见 `config.yml` 的 `redstone` 段。默认 `enable: false`，启用后使用 `/eclean redstone [世界名]` 查看最多 10 个活动点，需要 `eclean.redstone`（`eclean.admin` 自动继承）权限。

- 统计的是 `BlockRedstoneEvent` 中新旧电流不同的事件。每个方块独立计数；不是电路周期数，也不是 CPU 耗时。某些方块更新并不触发该事件。
- 使用游戏 tick 窗口；服务器低 TPS 时，100 tick 会超过现实时间的 5 秒。
- 连续 `consecutive_windows` 个窗口中，每窗事件数都**大于** `max_changes` 才触发。空闲窗口打断连续计数。
- `report` 只统计和后台报警，不修改世界。每窗口最多输出 10 个位置，其余只输出数量；同一位置按冷却间隔限制重复报警。
- `suppress` 在识别后进入 `cooldown_ticks` 冷却，尝试把该位置的电流上升保持在旧值，允许下降沿。不会拆除或替换方块。
- 抑制不是取消所有红石或物理更新，不能保证整套机器停止、降低多少 MSPT、或恢复后保持原有时序。其他插件也可能修改同一事件。此模式需要服主先在测试服验证具体机器。
- Paper 26.3 build 143 实测中，双侦测器自激时钟在一次上升沿被抑制后停止，冷却结束后没有自行重启。`suppress` 不是保证自动恢复的临时暂停；电路可能需要玩家手动重新触发。
- `ignored_worlds` 使用完整世界名；这些世界不统计、不干预。
- 内存记录上限为 `max_tracked_blocks`（1..100000）。达到上限后，新位置的事件不记录也不干预；命令显示累计未跟踪事件数。空闲记录自动过期，只保存坐标，不持有方块或区块引用。
- 重载配置会取消旧任务并清空统计、冷却。停止插件也会释放状态。

该实现面向普通 Bukkit/Paper 调度，不宣称 Folia 支持。自动破坏红石元件不包含在内。

对应占位符及授权说明见 [权限与占位符](permissions-placeholders.md)。
