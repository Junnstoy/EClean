# 闲置区块卸载请求（默认关闭）

在 `config.yml` 设置 `chunk_unload.enable: true` 后，用 `/eclean unloadstats` 查看计数，需要 `eclean.admin` 权限。

本模块只尝试将已加载且符合条件的区块加入服务器的安全卸载队列。不删除区块文件、不移除实体、不关闭自动保存，不移除其他插件的加载票据，也不取消强制加载。

## 筛选条件

- 排除 `ignored_worlds` 中的完整世界名。
- 已加载，且检查时没有强制加载标记、插件加载票据或正在观察该区块的玩家。
- 不在任何玩家附近的保护半径内；半径为 `keep_radius` 和服务端视距的较大值，单位为区块。另通过 `Chunk.getPlayersSeeingChunk` 检查实际观察玩家，避免只依赖视距设置。不能使用已弃用的 `World.isChunkInUse`：实测 Paper 26.3 build 143 中该方法直接返回 `isChunkLoaded`，会错误跳过所有已加载候选。
- 首次满足条件后，至少经过 `idle_ticks` 游戏 tick 的观测时间。后续观察到受保护状态或区块重新加载事件会重置计时；这不是对两次检查之间所有活动的完整追踪。
- 插件票据或区块观察玩家 API 不存在时不启动该功能；检查出现异常或返回未知状态时跳过该区块。

## 预算和统计

每隔 `period_ticks` 检查最多 `scan_budget` 个候选，最多发出 `request_budget` 次请求。候选处理完后重新取得已加载区块列表；获取这个列表仍需遍历已加载区块，不受 `scan_budget` 限制。大量区块的服务器应测量此开销。

命令显示累计检查数、跳过数、请求尝试数和**成功入队数**。入队不代表已经卸载：服务端加载原因、事件监听器或随后的玩家活动仍可能影响最终结果。请求失败也重新等待闲置时间，避免不断重试。重载配置会清空队列、计数和计时。

本实现使用 `World.unloadChunkRequest(x, z)`，不使用不保存的强制卸载。服务器正常管理保存及卸载过程。

## 适用范围

现代 Minecraft 已通过加载票据管理区块，普通情况下不需要额外的定时卸载器。本功能无法清除由其他插件合法持有的加载票据，因此不能保证明显释放内存或提升 TPS。默认关闭，只有测试确认有收益再启用。普通 Bukkit/Paper 调度，不支持 Folia。

API 语义参考：[Paper World API](https://jd.papermc.io/paper/26.2/org/bukkit/World.html#unloadChunkRequest(int,int))。
