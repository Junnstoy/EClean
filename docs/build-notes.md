# 构建来源与测试产物

本说明对应 2026-10-04 的十版本测试。实现提交为 `1cb6777bd400f961044a5807b18cd2590633fc7e`，完整探针和测试记录提交为 `47ad12754ab831d656318f046ecef5e950fccb17`；后续 PR 材料整理只更新文档。

## EPlugin 的来源

- 上游仓库：[4o4E/EPlugin](https://github.com/4o4E/EPlugin)。
- 使用标签：`v1.4.0`，提交 `5e575fcba1342e7efca02669bb27ae68f709128b`。
- 依赖坐标：`top.e404:eplugin-core:1.4.0`、`top.e404:eplugin-menu:1.4.0`、`top.e404:eplugin-serialization:1.4.0`、`top.e404:eplugin-hook-placeholderapi:1.4.0`。
- 没有使用 EPlugin 当前 main，也没有使用 fork 的 `fix/year-based-minecraft-version` 分支。独立解析修复可以保留，但不构成本 PR 的前置依赖。

本轮环境未能从项目配置的公开仓库取得所需 EPlugin 1.4.0 产物，因此先从上述标签源码构建四个模块到本地 Maven 仓库。框架源码未改；以下为本地构建准备，未提交到 EPlugin main：

| 项目 | 本轮设置 |
| --- | --- |
| 模块 | 只包含上述四个实际使用模块 |
| Kotlin 构建插件 | 2.1.21 |
| Java 输出目标 | 1.8 |
| PlaceholderAPI 编译依赖 | 2.11.6 |
| 发布任务 | `publishToMavenLocal`，保留原 group、artifact 和 version |

这属于从官方标签源码重建依赖，不能表述为使用未经改动的官方发布二进制，也不能表述为已将 EPlugin 全框架适配到 Java 8。

## EClean 构建

使用 Gradle 8.10 / JDK 17，编译 API 为 Spigot 1.8.8，Kotlin/Java 输出目标为 Java 8。构建环境使用 Maven 本地仓库、Maven Central、Spigot snapshots、PlaceholderAPI 和 Paper Maven 仓库；依赖解析的 JVM 属性设为 17，以解析测试所需依赖，最终产物仍单独检查 Java 8 字节码。

在上述四个 EPlugin 模块已可从本地 Maven 解析、仓库及 JVM 解析属性配置完成后，在 EClean 根目录执行：

```bash
gradle test shadowJar
```

EClean 严格固定 Kaml 0.55.0，并隔离 SnakeYAML Engine 包。配置构造时显式传入兼容的 `Yaml`，避免调用 EPlugin 默认格式的新版构造器。此验证仅针对 EClean 实际使用的框架路径。

打包任务检查全部 `.class`，发现 major > 52 即失败。本轮 JAR 含 1925 个 major 52 类、32 个 major 49 类，没有更高字节码；同一 JAR 又在真实 Java 8 服务端完成验证。EPlugin 运行依赖已经内嵌，无需额外安装 EPlugin 插件。

## 测试与复现边界

测试流程见 [integration/README.md](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/integration/README.md)。探针会修改和清理测试世界并主动停止服务端，仅用于独立、可丢弃的测试服务器。

脚本选择所列 Minecraft 版本在执行时下载接口返回的构建，并下载相应 Java 大版本的当前 Temurin JDK，因此日后执行不保证仍取得本轮完全相同的版本。对照本轮结果应使用 [test-manifest.json](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/integration/test-manifest.json) 中的 Paper build、SHA-256 和 Java 版本；测试包另保留各版本的 `selected.json`。重新构建产生不同哈希时，应记录为新的测试产物，不能沿用本轮哈希声称已验证。

本轮结果为 60 项单元测试和 405 项最终有效实服断言；早期失败及重测已在 [测试报告](compatibility-test-report.md) 单列。整理 PR 材料时重新核对了归档中的全部校验值、JUnit XML、最终 TSV 和分支中的 manifest，没有重新运行服务端测试。

## 产物身份

- 文件：`EClean-1.21.0.jar`；插件描述保留项目原版本号，不是上游新发布版本。
- SHA-256：`9dce75a770d03919147a328883473e4a5194400498673fd8f6e7c593c28fe7b3`。
- 测试包包含构建日志、JUnit XML、实服首轮/重启日志、逐项 TSV、构建元数据和校验值；不分发 Minecraft 服务端或 JDK。
