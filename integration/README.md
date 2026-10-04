# 可重复的真实服务端测试

测试探针仅用于全新、可丢弃的测试服务器。会创建三个测试世界、修改测试配置、生成/清理实体与红石方块，并主动关闭服务端。**不要装入正式服。**

1. 按项目构建要求准备 Gradle 8.10、JDK 17 和 EPlugin 1.4.0 四个模块到 Maven 本地仓库，执行 `gradle test shadowJar`。
2. 执行 `python3 integration/setup-matrix.py`，从 Paper 官方 API 获取代表版本并校验官方 SHA-256，下载相应 Temurin JDK。
3. 执行 `python3 integration/prepare-servers.py`，仅完成 Paperclip 补丁准备。
4. 执行 `python3 integration/run-matrix.py`，或追加版本参数，例如 `1.8.8 26.3`。

Linux/x86_64、Python 3.12+、curl；运行材料放入 `.integration-work`。各版本使用单独的全新目录。测试脚本根据测试授权写入 `eula=true`，监听 127.0.0.1，关闭 mcstats、bStats 和 snooper。并行最多两个服务器，每个最大堆 1200 MiB。支持环境已有的 HTTPS 代理配置。

同一个 EClean JAR 放入全部服务端。探针以 1.8 API、Java 8 字节码编译。每个版本经历首轮与 JVM 重启两轮；输出 TSV 断言、完整日志、JAR 校验值、官方构建元数据和区块文件诊断。失败或未完成返回非零退出码。探针校验实际卸载事件、`isChunkLoaded=false`、修改后的方块在重新加载及重启后保留；“请求成功”不当作“卸载完成”。

红石通过定时切换真实红石块电源产生方块更新，验证原生 `BlockRedstoneEvent`、统计和抑制；不是模拟整个自然振荡电路。权限测试使用无权限 CommandSender 代理直接进入插件命令处理器。菜单覆盖构造和图标更新，不代替真实玩家点击测试。旧版不存在的加载票据/强加载能力按不适用处理。
