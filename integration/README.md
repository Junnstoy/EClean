# 可重复的真实服务端测试

测试探针仅用于全新、可丢弃的测试服务器。会创建三个测试世界、修改测试配置、生成/清理实体与红石方块，并主动关闭服务端。**不要装入正式服。**

1. 按项目构建要求准备 Gradle 8.10、JDK 17 和 EPlugin 1.4.0 四个模块到 Maven 本地仓库，执行 `gradle test shadowJar`。
2. 执行 `python3 integration/setup-matrix.py`，从 Paper 官方 API 获取代表版本并校验官方 SHA-256，下载相应 Temurin JDK 及固定版本的 PlaceholderAPI（校验 SHA-256）。
3. 执行 `python3 integration/prepare-servers.py`，仅完成 Paperclip 补丁准备。
4. 执行 `ECLEAN_TEST_PAPI=1 python3 integration/run-matrix.py`，验证安装 PlaceholderAPI 的十个版本。Java 8 服务端使用 PAPI 2.11.6，其余使用 2.12.3。
5. 不设置该变量时不安装 PAPI，例如 `python3 integration/run-matrix.py 1.8.8 26.3`，验证可选依赖缺席。两种模式均可追加要运行的版本。

可通过 `ECLEAN_JAVA_17_HOME`（或 8 / 21 / 25）指定本地 JDK；setup 跳过对应下载，prepare 与 run 使用同一路径。本轮 Java 17 使用 Temurin 17.0.16+8。须在三个脚本中保持相同环境设置。

Linux/x86_64、Python 3.12+、curl；运行材料放入 `.integration-work`。各版本使用单独的全新目录。测试脚本根据测试授权写入 `eula=true`，监听 127.0.0.1，关闭 mcstats、bStats 和 snooper。并行最多两个服务器，每个最大堆 1200 MiB。支持环境已有的 HTTPS 代理配置。

同一个 EClean JAR 放入全部服务端。探针以 1.8 API、Java 8 字节码编译。每个版本经历首轮与 JVM 重启两轮；输出 TSV 断言、完整日志、JAR 校验值、官方构建元数据和区块文件诊断。失败或未完成返回非零退出码。探针校验实际卸载事件、`isChunkLoaded=false`、修改后的方块在重新加载及重启后保留；“请求成功”不当作“卸载完成”。

红石通过定时切换真实红石块电源产生方块更新，验证原生 `BlockRedstoneEvent`、统计和抑制；不是模拟整个自然振荡电路。权限测试使用 CommandSender 代理直接进入插件命令处理器，并通过真实 Bukkit PermissibleBase / PermissionAttachment 验证独立授权、admin 继承、显式子权限拒绝和 Tab 补全。PAPI 模式调用真实扩展 API，验证 17 个新占位符、旧占位符、未知键、异步读取、开关即时刷新、计数、PAPI 重载及 JVM 重启。菜单覆盖构造和图标更新，不代替真实玩家点击测试。旧版不存在的加载票据/强加载能力按不适用处理。

测试探针先等待新生成的目标区块激活，再保存基线、写入未保存标记并启用卸载。在事件支持 Cancellable 的旧版服务端，EClean 尚未开始发出请求前，暂时取消目标区块的自然卸载；现代版本不调用已移除的取消接口。强加载预热在正式卸载检查前解除，其他保护测试区块仍保留强加载/插件票据。上述准备只属于探针，不包含在生产 JAR 中。

运行器除检查 TSV 外，还拒绝包含插件/探针事件处理错误、启停错误及链接错误的运行；普通离线测试服的认证网络错误与超平坦生成配置提示单独保留在完整日志中。

固定实体数量的场景会将随机生成的鸡骑士解乘并设为成年僵尸，避免触发乘客保护而干扰计数；乘客保护另有显式骑乘场景，不被此准备步骤替代。
