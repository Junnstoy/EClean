# 按世界配置清理规则

旧配置继续有效。`living`、`drop`、`chunk` 原有字段作为默认规则；新增字段可以完全省略。

## 优先级

每个字段分别使用：**实体/物品规则 → 世界规则 → 原有默认规则**。

- `worlds` 的键是区分大小写的完整世界名，不是正则。
- `living.entities`、`chunk.entities` 的键是完整 Bukkit 实体类型名，如 `ZOMBIE`。
- `drop.materials` 的键是完整 Bukkit 物品类型名，如 `DIAMOND`。
- 实体/物品规则作用于所有世界。若只想在特定世界修改某种实体的数量，使用该世界的 `chunk.worlds.<世界>.limit`。
- 字段省略或 `null` 表示继承；`false`、`0`、空字符串、空列表、空映射都是明确设置。
- 世界的 `match` 列表和 `limit` 映射整体替换默认值，不合并。`limit: {}` 禁用该世界的通用密集实体规则，但显式实体上限仍有效。
- `enable`、`disable_world`、定时周期和全服完成消息保持原有行为，不参与逐字段继承。全服/定时清理仍遵守总开关和世界排除；显式指定世界的手动命令保持原有行为，可在被排除的世界执行。

## 生物清理

以下字段合并到已有的 `living` 段，不要创建重复的 YAML 顶级键：

```yaml
living:
  # 原有默认设置
  enable: true
  is_black: true
  match: [ZOMBIE]
  settings:
    name: false
    lead: false
    mount: false

  worlds:
    world_nether:
      match: [BLAZE, GHAST]
      settings:
        name: true

  entities:
    VILLAGER:
      clean: false
    ZOMBIE:
      clean: true
      settings:
        name: false
```

`clean` 覆盖黑/白名单对该类型的选择结果；保护条件仍单独生效。例如 `clean: true` 不自动解除命名保护，必须另外设置 `settings.name: true`。`settings.lead`、`settings.mount` 同理。玩家始终不进入生物清理。

世界规则支持 `is_black`、`match`、`settings.name/lead/mount`。实体规则支持 `clean`、`settings.name/lead/mount`。

## 掉落物清理

```yaml
drop:
  # 保留其他原有设置
  worlds:
    world:
      lore: true
      enchant: true
  materials:
    DIAMOND:
      clean: false
    COBBLESTONE:
      clean: true
      lore: false
```

世界规则支持 `is_black`、`match`、`enchant`、`lore`、`written_book`；物品规则支持 `clean` 以及后三个保护字段。保护字段的 `true` 表示保护，与生物 `settings` 中 `true` 表示允许清理的原有含义不同。

该功能只改变主动清理，不改变 `trashcan.despawn` 的自然消失物品回收规则。`written_book` 继续使用原有实现：保护含页面的 `WRITABLE_BOOK`（1.8～1.12 对应 `BOOK_AND_QUILL`）。

## 密集实体清理

```yaml
chunk:
  # 默认：匹配的实体共同占用一个额度
  limit:
    ZOMBIE|SKELETON: 10
    SHEEP: 20
  worlds:
    world_nether:
      limit:
        ZOMBIE|SKELETON: 5
      count: 30
      format: "{chunk}中{entity}的数量较多({count})"
  entities:
    ZOMBIE:
      limit: 2
    VILLAGER:
      clean: false
```

本例所有世界的僵尸每区块最多保留 2 个符合保护条件的候选；它们不再占用默认或世界正则组的额度。下界世界骷髅最多保留 5 个，其他世界最多保留 10 个。

- 世界规则支持 `limit`、`settings`、提醒阈值 `count` 和提醒格式 `format`。`format: ""` 关闭该世界的提醒。
- 实体规则支持 `limit`、`settings`、`clean`。`clean: false` 完全排除该类型；`clean: true` 仅允许参与检查，仍需数量规则才能清理。
- `limit: 0` 清理全部符合保护条件的候选；负数上限/提醒阈值在读取配置时拒绝。
- 只有设置了显式 `limit` 的实体才脱离正则分组；仅覆盖 `settings` 时继续使用世界/默认分组。
- 保留原有正则组处理次序。多个正则重叠时，后面的规则仍可继续检查前面未删除的候选；建议避免重叠。
- 玩家始终排除，包含显式 `PLAYER` 和匹配所有实体的正则。

## 重载与验证

`world_rules` 是世界和实体/材料覆盖项的总开关。`/eclean worldrules on|off|status` 立即修改并保存，需要 `eclean.worldrules`（`eclean.admin` 自动继承）。关闭只跳过覆盖项，保留原始配置，清理仍按原默认规则执行。

使用已有 `/eclean reload` 重载配置。规则按每次清理解析，不保留跨重载缓存。可先在测试世界执行 `/eclean clean entity <世界>`、`/eclean clean drop <世界>`、`/eclean clean chunk <世界>` 检查效果。

已有 26.x 兼容修复继续保留。本功能不包含红石统计、自动拆除、区块卸载或 Folia 调度适配。

对应占位符及授权说明见 [权限与占位符](permissions-placeholders.md)。
