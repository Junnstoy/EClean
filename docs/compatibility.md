# Minecraft 1.8～26.x 兼容调整

目标是同一份 Java 8 字节码的 EClean JAR 跨版本加载，服务器本身仍使用各版本所需的 Java。具体测试结果见 [测试报告](compatibility-test-report.md)。测试覆盖代表性 Paper 版本，不等于测试了每个补丁版、Spigot、混合核心或 Folia。

## 回退判断

| 项目 | 处理 |
| --- | --- |
| EPlugin 年份版本号解析修复 | 保留。兼容传统 `1.x.y` 和 `26.x`，无需撤销。该修改位于 EPlugin 独立分支，EClean 不依赖它。 |
| EClean 的 EPlugin 依赖 | 保持 `top.e404:eplugin-*:1.4.0`，没有升级到 EPlugin 主分支的新坐标 `top.e404.eplugin:*:1.4.0-SNAPSHOT`，无需整体回退框架。 |
| YAML 解析依赖 | Kaml 0.60 带入 Java 11 的 SnakeYAML KMP 类，旧包有 260 个 major 55 类；EClean 固定为 Kaml 0.55.0，隔离 SnakeYAML Engine 包。EClean 向 KtxConfig 显式传入 Yaml，避开框架默认格式的新版构造器。这只验证 EClean 使用的框架路径，不代表 EPlugin 全部模块均支持 Java 8。 |
| EPlugin 本地构建调整 | 从 v1.4.0 标签构建四个实际依赖模块，仅属于构建准备，没有提交到 EPlugin 主分支。 |
| 26.x 统计及物品堆叠修复 | 保留。`ItemStack.maxStackSize` 在 1.8 可用，现代版本可返回自定义上限。新接口改为反射检查与调用。 |
| 三个新功能 | 保留，增加独立、持久化开关。红石和区块卸载默认关闭；世界覆盖规则默认开启，无覆盖项时原行为不变。 |

EClean 编译基线设为 Spigot API 1.8.8，打包任务检查所有依赖类，拒绝字节码 major > 52；字节码检查不能替代实际运行测试。

## API 兼容

- 乘客保护：新版本 `getPassengers`、旧版本 `getPassenger`；检查失败保留实体。
- 书与笔：识别 `WRITABLE_BOOK` 和 `BOOK_AND_QUILL`。
- 菜单音效：查找现代名称及旧版别名。
- 强制加载、插件票据、区块观察玩家：按接口能力检测，避免旧版链接失败。
- 密集实体：排除无效实体，避免 1.8 同一 tick 内已移除实体仍留在区块列表中，挤占额度导致多删活体。
- 重载和开关在主线程串行执行；保存成功后才修改内存状态，临时文件替换避免部分写入。

## 指令

| 指令 | 配置项 | 关闭效果 |
| --- | --- | --- |
| `/eclean worldrules on\|off\|status` | `world_rules` | 跳过世界和实体/材料覆盖项，使用原默认规则；保留覆盖内容 |
| `/eclean redstone on\|off\|status` | `redstone.enable` | 停止统计，释放活动和抑制状态 |
| `/eclean chunkunload on\|off\|status` | `chunk_unload.enable` | 停止扫描，清空候选与闲置计时 |

均需 `eclean.admin`，修改立即生效并保存 `config.yml`，重启保留。首次命令保存前备份为 `config.yml.before-feature-switch`；序列化会重新排版并移除 YAML 注释。`status` 区分配置与实际运行状态。关闭不撤销已交给服务端的卸载请求，也不自动重启已停止的红石时钟。

统计命令为 `/eclean redstone stats [世界名]` 和 `/eclean unloadstats`。旧写法 `/eclean redstone [世界名]` 保留；世界名若与开关词相同，使用 `stats <世界名>`。

## 区块保护路径

1. 有 `getPlayersSeeingChunk`：检查实际观察玩家、强加载、插件票据、玩家距离。
2. 尚无插件票据 API 的旧版：旧版 `isChunkInUse` 加玩家距离；已存在的强加载标记仍检查。
3. 有插件票据但没有观察玩家 API：只处理**无玩家的世界**。这些版本的 `isChunkInUse` 可能只是 `isChunkLoaded`，不能当作玩家检查。

路径按 API 能力选择，`status` 显示实际方式。检查异常或状态未知则跳过。只使用服务端安全卸载请求，不删除区块文件、不移除插件票据、不取消强加载，也不使用不保存的强制卸载。
