# 研发候选记录：0.1.0-dev.7

- 需求/上游功能基线/批准差异：以 **SEAC拔刀剑附属 旧梦重铸** 为正式名称，公开源码并发布 GitHub 预发布版；保留 `slashblade_legacy_compat` 注册身份。行为基线为旧版 SlashBlade 1.12.2，现代运行目标为 SlashBlade: Resharpened `2.0.5-1.21.1`。
- 复用的源码、构建和验证能力；必须新增的缺口：复用已冻结的 33 个 Java 文件、隔离服务端契约和全模组组合证据；新增公开 Maven 依赖、精确哈希门禁、公开源码审计、稳定平坦世界夹具、CI、Release、许可边界和 AI 披露。
- 上游版本/commit/原 JAR SHA256/许可证与授权来源：旧版 SlashBlade commit `ba1ef8604c0971f68336b882b42a868df7f32f0b`，原作者日文使用条款见 `LICENSE-LEGACY-README.txt`；重锋 `2.0.5-1.21.1` 为外部 MIT 代码/保留权利美术依赖，JAR SHA-256 `5b60ff41d89d63e34fbae3d20d9663ae96aa4c594a10b9889a4a39905d44d1c4`。
- 本地源码路径/commit+dirty diff或source ZIP SHA256：公开仓库 tag `v0.1.0-dev.7`；精确 commit 和源码 ZIP SHA-256 由 Release manifest 与 SHA-256 清单记录。
- 候选modId/内部版本/文件名/字节/SHA256：`slashblade_legacy_compat` / `0.1.0-dev.7` / `SEAC-SlashBlade-Old-Dream-Reforged-1.21.1-0.1.0-dev.7.jar` / 90,984 / `167c19e3978577e0a2818191b182ed8e71ca3880bcfcb0aec06e3ffedd89f255`。
- Java/Gradle/插件/MC/NF/锁依赖/环境manifest SHA256：Java `21.0.12+8`、Gradle `9.2.1`、ModDevGradle `2.0.144`、Minecraft `1.21.1`、NeoForge `21.1.248`；锁依赖见 `dependencies.sha256.json`，GitHub Release manifest 记录发布环境和提交。
- 构建工作目录/实际命令/退出码/独立重建hash：从 Git commit 的纯净导出副本执行 `./gradlew --no-daemon clean build runContracts`，exit `0`；随后再次 `clean build`，运行 JAR 哈希仍为 `167c19e3978577e0a2818191b182ed8e71ca3880bcfcb0aec06e3ffedd89f255`。

| case / 功能或兼容合同 | 运行步骤、期望/实际 | 输入hash / run / 证据 | 结果 |
| --- | --- | --- | --- |
| 直接玩法及原缺陷 | 自动契约覆盖断刀伤害/距离、旧连段、SB、追加攻击、投射物与收刀修复；真实玩家手感未测 | `runContracts` / `VALIDATION.md` | PASS（自动）；真实客户端 NOT_TESTED |
| 资源/配方/注册 | 校验 Mixin 类、metadata、39 项源码资源，运行 JAR 不含 contracts | 运行 JAR SHA-256 如上 | PASS |
| 能源/物流/协议/事件 | 不涉及能源物流；服务端输入、目标筛选、实体保存/命中和事件路径有契约 | 隔离平坦世界 `127.0.0.1:25791` | PASS / 能源物流 N/A |
| 客户端GUI/视觉/双人 | 最小客户端曾可启动；第一/三人称画面、双人、高延迟未验收 | `VALIDATION.md` | NOT_TESTED |
| 最终整包/旧世界/真实卸载 | 锁定的 246 模组服务端组合契约通过；生产世界、客户端整包和卸载中的在途实体未验收 | 既有冻结全模组证据 | PASS（组合）；生产/旧世界 NOT_TESTED |
| 停止后全维度保存/exit0/非forced及重启 | 契约完成后正常停止，overworld/nether/end 全部保存，exit 0；未做生产重启 | 最终纯净导出运行 | PASS（隔离）；生产 N/A |

性能：未做长时、高并发或多人负载测试；不据此声明生产性能。

交付范围：R 研发预发布候选；不是 F 完整玩法验收、I 最终整包验收或 P 生产验收。

独立审查身份/输入hash/发现及处置：未做非实现者独立审查。公开前完成自动源码门禁、JAR 条目差异审计和两次纯净重建；发现随机地形会使距离契约波动，已改为完整参数的隔离平坦世界并在两个全新目录复验通过。

| 端侧 | 精确新增/替换/删除/保留路径 | 新/旧hash | 配套依赖/禁止并存 | 回滚文件与存档要求 |
| --- | --- | --- | --- | --- |
| 客户端与服务端 | 发布运行 JAR；本次仅 GitHub 发布，未部署 | 新 hash 如上；旧内部候选 `2e72c4e5…0c025` | 必须精确配套重锋 `2.0.5-1.21.1`，禁止并存另一版本本附属 | 部署前备份世界与刀数据；卸载会丢失在途附属实体，先在副本验证 |

维护接收/部署授权引用/实际部署hash/客户端更新与持续观察回执：仅获 GitHub 公开发布授权；没有生产部署或玩家更新授权，发布不构成生产绿灯。
