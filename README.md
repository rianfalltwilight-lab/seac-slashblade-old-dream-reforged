# SEAC拔刀剑附属 旧梦重铸

> 面向 Minecraft 1.21.1 / NeoForge 的非官方 SlashBlade: Resharpened 双端附属，重铸部分 1.12.2 时代的拔刀剑战斗机制。

[English](README_en.md)

当前版本为 **0.1.0-dev.11**。这是开发预览版，尚未完成真实客户端手感、多人高延迟和生产整包验收，请先在备份世界中测试。

## 兼容矩阵

| 组件 | 精确版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| SlashBlade: Resharpened | [2.0.5-1.21.1](https://modrinth.com/mod/slashblade-resharped/version/2.0.5-1.21.1) |
| 本附属 | 0.1.0-dev.11 |
| Java | 21 |

客户端和服务端必须安装同一版本。`modId` 继续使用 `slashblade_legacy_compat`，正式名称变更不会改动已有注册身份。

## 已实现内容

- 恢复断刀伤害与近战距离，同时保留服务端视线和范围判定。
- 恢复旧蓄力窗口、默认刀连段、即时近战、冲刺、空中动作和刀鞘姿态。
- 恢复 SB 幻影飞刀、旧剑气追加攻击、投射物拦截、诱导与上挑爆破。
- 修复盖亚守护者及通用敌对目标筛选兼容。
- 恢复达到 1000 耀魂后的击杀收益累计与有效收刀耐久结算。

这些能力是有边界的现代兼容实现，并不宣称与所有旧版动作、网络时序或画面逐帧等价。详细验证范围见 [VALIDATION.md](VALIDATION.md)。

## 安装

1. 安装表中精确版本的 NeoForge 与 SlashBlade: Resharpened。
2. 将本附属运行 JAR 同时放入客户端和服务端的 `mods` 目录。
3. 首次使用前备份世界和现有刀数据；不要与其他版本的本附属并存。

卸载会丢失仍在世界中的本附属飞刀/剑气实体。刀上的兼容数据会被重锋忽略，但仍建议先在世界副本中验证卸载与回滚。

## 构建与契约测试

```powershell
./gradlew --no-daemon clean build
./gradlew --no-daemon runContracts
```

构建会从 Modrinth Maven 获取精确版本的重锋依赖，并按 [dependencies.sha256.json](dependencies.sha256.json) 校验大小和 SHA-256。依赖 JAR 不在本仓库或本项目产物中重新分发。

`runContracts` 使用隔离的开发服务端目录并自动退出，不连接生产世界。它不能替代真实客户端与多人验收。

## 许可证、来源与 AI 披露

SEAC 独立实现部分以 [MIT License](LICENSE) 发布。源自旧版 SlashBlade 的行为、变换和精确网格数据继续受原作者使用条款约束；原文与逐项来源见 [LICENSE-LEGACY-README.txt](LICENSE-LEGACY-README.txt)、[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) 和 [NOTICE](NOTICE)。

OpenAI Codex 实质参与了分析、实现辅助、测试、公开源码审计、文档与发布准备，完整边界见 [AI-GENERATED.md](AI-GENERATED.md)。


## dev.11

[Release notes / 累计更新与验收边界](docs/releases/0.1.0-dev.11.md)
