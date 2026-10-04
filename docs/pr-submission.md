# PR 提交材料

核对日期：2026-10-04。当前材料针对完整功能分支；此前 [PR #40](https://github.com/4o4E/EClean/pull/40) 已关闭且未合并，内容只涉及较早的统计和堆叠修复。本分支包含其改动，但功能范围和验证结果已扩展。

## 标题与正文

建议标题：

```text
feat: 添加世界规则、红石抑制和区块卸载，适配 1.8～26.x
```

正文使用仓库根目录 [PR.md](../PR.md)，已包含问题、改动、依赖来源、十版本测试矩阵、限制和 AI 使用声明，可完整复制到 GitHub PR 描述框。

## 提交目标

| 项目 | 值 |
| --- | --- |
| Base repository | `4o4E/EClean` |
| Base branch | `main` |
| 核对时上游提交 | `d92b4e2542d3963ce96a53e222eb1973d35b2edf` |
| Head repository | `Junnstoy/EClean` |
| Compare branch | `fix/compat-1.8-26-features` |
| 建议初始状态 | Draft，供维护者先审阅完整功能范围 |

[打开新 PR 比较页](https://github.com/4o4E/EClean/compare/main...Junnstoy:EClean:fix/compat-1.8-26-features?expand=1)。确认上述 base/head 后，填入标题和 `PR.md` 正文，选择创建草稿 PR。不要把新分支的 405 项断言填写为旧 PR #40 分支的验证结果。

当前功能分支此前通过 GitHub 集成创建草稿 PR 时返回 `403 Resource not accessible by integration`；截至本次核对，未检索到该完整功能分支的上游 PR。上述权限错误不影响准备和查看分支材料，需通过具备权限的 GitHub 会话提交。

## 附件与审阅入口

| 材料 | 用途 |
| --- | --- |
| [PR.md](../PR.md) | 可粘贴的 PR 正文 |
| [构建说明](build-notes.md) | EPlugin v1.4.0 标签、四个模块、本地构建准备、Java 8 产物身份 |
| [兼容说明](compatibility.md) | 能力检测、三项开关和区块保护路径 |
| [测试报告](compatibility-test-report.md) | 实测构建、JDK、结果和已知限制 |
| [测试脚本](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/integration/README.md) | 隔离测试服的运行方式与探针行为 |
| [测试 manifest](https://github.com/Junnstoy/EClean/blob/fix/compat-1.8-26-features/integration/test-manifest.json) | 各构建与测试 JAR 的 SHA-256 |
| `EClean-features-review.zip` | 同一测试 JAR、PR 材料、完整日志/TSV、JUnit XML、构建日志及校验值；提交时可作为附件 |

测试 JAR 的 SHA-256 为 `9dce75a770d03919147a328883473e4a5194400498673fd8f6e7c593c28fe7b3`。本次仅整理文档和附件，没有修改测试 JAR，也没有重新运行服务端测试。

## 建议审阅顺序

1. `build.gradle.kts`、`util/Compatibility.kt` 和统计/堆叠修复：确认 Java 8 与 API 能力回退。
2. `config/Rules.kt` 和三个清理入口：确认字段继承、实体/材料全局覆盖、保护条件及显式零上限。
3. `monitor/`、`maintenance/`：确认红石抑制语义、区块保护、未知状态处理及预算边界。
4. `command/FeatureSwitch.kt` 和重载流程：确认配置保存失败时保留运行状态、开关即时生效及首次备份。
5. 单元测试、`integration/` 与测试报告：对照实际覆盖项，区分已验证场景和待人工验证场景。

若维护者要求拆分，可按“兼容修复与构建基线 → 世界规则 → 红石 → 区块卸载 → 开关与矩阵验证”组织逻辑变更；当前后续兼容与开关提交跨越多个模块，不能直接把历史中间提交的分支宣称为已通过本轮同样的测试，拆分后应按最终组合重新验证。
