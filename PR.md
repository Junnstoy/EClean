## 目的与范围

在保持 EClean 面向 Minecraft 1.8.x～26.x 的兼容目标下，补充按世界清理规则、红石活动统计及高频抑制、闲置区块卸载，并为三个功能提供独立的持久化开关。

本分支也包含 [#40](https://github.com/4o4E/EClean/pull/40) 中的 26.x 世界统计及物品堆叠修复。#40 已关闭且未合并；本次提交在这些修复之上完成了旧版兼容和代表版本实服验证。三个功能均可独立控制，旧配置不必补齐新增字段。

## 主要修改

### 跨版本兼容与既有问题

- 世界统计改为检查 API 能力，避开 EPlugin 版本解析不接受 `26.3.build.*` 导致的 `mcVer!!` 空指针，同时正确识别旧 `1.x` 版本的强加载能力。
- 垃圾桶取出和合并物品遵守物品自身的最大堆叠数。
- 编译 API 基线设为 Spigot 1.8.8；为乘客、书与笔、音效、强加载、插件票据和区块观察玩家提供兼容处理。清理时排除旧版同一 tick 内仍留在区块列表中的失效实体，防止密集实体多删。
- 固定 Kaml 0.55.0，避免传递依赖带入 Java 11 类；打包时检查整个 JAR，拒绝高于 Java 8 的类。服务端本身仍使用相应版本要求的 Java。

### 三个功能与开关

| 功能 | 行为 | 开关及默认值 |
| --- | --- | --- |
| 按世界清理规则 | 逐字段按 **实体/材料规则 → 世界规则 → 默认规则** 解析；省略或 `null` 继承，`false`、`0`、空集合可显式覆盖 | `/eclean worldrules on\|off\|status`；默认开启，无覆盖项时沿用原规则 |
| 红石统计及高频抑制 | 按方块统计 `BlockRedstoneEvent`；`report` 只报告，`suppress` 对超过阈值的位置抑制电流上升、放行下降，不拆除方块 | `/eclean redstone on\|off\|status`；默认关闭 |
| 闲置区块卸载 | 有闲置等待和扫描/请求预算；检查玩家、强加载和插件票据后，通过 `World.unloadChunkRequest` 请求服务端处理保存与卸载 | `/eclean chunkunload on\|off\|status`；默认关闭 |

实体/材料规则作用于所有世界；`world_rules` 关闭时跳过世界及实体/材料覆盖项，恢复默认规则并保留覆盖配置。原有总开关、世界排除和定时清理逻辑继续生效。

开关均需 `eclean.admin`，保存成功后才修改运行配置并立即启停相关任务；重载与开关在主线程串行。首次命令保存前备份为 `config.yml.before-feature-switch`。配置经序列化保存会重新排版并移除 YAML 注释。

统计命令保留：`/eclean redstone stats [世界名]`、`/eclean unloadstats`；旧的 `/eclean redstone [世界名]` 写法仍可使用。

### EPlugin 依赖来源

保持 EClean 原有坐标 `top.e404:eplugin-*:1.4.0`。本轮验证使用原作者 [EPlugin v1.4.0 标签源码](https://github.com/4o4E/EPlugin/tree/5e575fcba1342e7efca02669bb27ae68f709128b) 构建 `core`、`menu`、`serialization`、`hook-placeholderapi` 四个模块，框架源码未修改。

**本 PR 不升级到 EPlugin main，也不依赖 fork 中的版本解析修复分支。** 本地构建对 Kotlin、Java 目标及编译依赖做了准备性调整，因此不将验证产物表述为未经重建的官方 EPlugin 二进制。Kaml 的版本约束和兼容处理位于 EClean；这不代表 EPlugin 的全部模块都已验证可用于 Java 8。详见 [构建说明](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/docs/build-notes.md)。

## 验证结果

2026-10-04 的最终有效运行：**60 项单元测试通过，405 项实服断言通过**。十个版本使用同一个 EClean JAR，每个版本均完成独立服务端测试和完整 JVM 重启。

| Minecraft | Paper build | Java | 实服断言 |
| --- | --- | --- | --- |
| 1.8.8 | 445 | 1.8.0_504 | 39/39 |
| 1.12.2 | 1620 | 1.8.0_504 | 39/39 |
| 1.13.2 | 657 | 1.8.0_504 | 40/40 |
| 1.16.5 | 794 | 1.8.0_504 | 41/41 |
| 1.17.1 | 411 | 17.0.20.1 | 41/41 |
| 1.18.2 | 388 | 17.0.20.1 | 41/41 |
| 1.20.4 | 499 | 17.0.20.1 | 41/41 |
| 1.20.6 | 151 | 21.0.12.1 | 41/41 |
| 1.21.11 | 132 | 21.0.12.1 | 41/41 |
| 26.3 | 148（beta） | 25.0.4.1 | 41/41 |

覆盖规则优先级与保护条件、开关权限及保存失败、重载/重启持久化、原生红石事件、实际区块卸载，以及修改后的方块在重新加载和 JVM 重启后保留。菜单测试覆盖构造和图标更新。单元测试还覆盖物品自定义堆叠上限、预算、拒绝请求和未知保护状态。

1.12.2 首轮曾因固定等待时间不足出现 2 项失败；调整测试探针为按实际卸载完成状态轮询后，使用同一插件 JAR 重测 39/39 通过。最终统计不包含初次失败运行；完整调整记录见 [测试报告](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/docs/compatibility-test-report.md)，测试脚本和元数据见 [integration](https://github.com/Junnstoy/EClean/tree/fix/compat-1.8-26-features/integration)。

测试 JAR：`EClean-1.21.0.jar`，SHA-256：

```text
9dce75a770d03919147a328883473e4a5194400498673fd8f6e7c593c28fe7b3
```

此文件保留项目版本号 `1.21.0`，属于适配分支测试产物。

## 行为限制与审阅重点

- 区块保护按 API 能力选择。对有插件票据 API、却无区块观察玩家 API 的服务端，仅处理无玩家世界；本轮对应 1.16.5、1.17.1、1.18.2、1.20.4。检查异常或状态未知则跳过。没有取消其他插件票据或强加载。
- 卸载统计中的成功数表示请求入队；实际卸载由服务端决定。关闭开关不会撤回已提交的请求。获取候选列表仍需遍历已加载区块，扫描预算不限制这一步的开销。
- 红石统计是事件次数，不是 CPU 耗时或电路周期数。抑制不保证整个机器停止，也不保证冷却结束后时钟自行恢复；不承诺 TPS 或内存收益。
- 实服矩阵验证了表列 Paper 构建中的指定场景，未覆盖每个补丁版本、Spigot 实服、Folia、混合核心、真实在线玩家观察/距离保护、真实玩家 GUI 点击、全部自然红石电路、第三方插件组合或长期性能。

功能配置与语义见 [世界规则](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/docs/world-rules.md)、[红石](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/docs/redstone.md)、[区块卸载](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/docs/chunk-unload.md)、[兼容说明](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/docs/compatibility.md)。这是一条汇总分支，可按维护者的审阅安排拆分。

## AI 使用声明

本 PR 由提交者委托使用 AI（OpenAI ChatGPT / Codex）分析问题、修改代码、编写并执行构建与测试脚本，以及准备 PR 说明和提交材料。测试结论以实际运行日志、逐项断言和产物校验值为依据。AI 的参与及自动化测试通过不等于已经完成独立人工审阅，也不代表原作者认可；请维护者审查代码、依赖和行为后决定是否合并。
