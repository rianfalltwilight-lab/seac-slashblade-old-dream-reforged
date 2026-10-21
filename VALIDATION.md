# 当前验证：1.21.1 dev.10 / 1.20.1 Forge dev.16

2026-10-21 完成源码整合和隔离服验证。

| 发行版 | 实测环境 | 结果 |
| --- | --- | --- |
| 1.21.1 NeoForge 0.3.0-dev.10 | NeoForge 21.1.248 / Java 21 / 重锋 2.0.8 | 构建及隔离服契约通过 |
| 1.20.1 Forge 0.3.0-dev.16 | Forge 47.4.10 / Java 17 / 重锋 1.9.65 | 构建及隔离服契约通过 |

两版均使用打包后的主 JAR 通过新增整合 19 项、AirTrick 19 项、双模式 64 项、r87 核心 102 项，以及 Performance 正确性和回归检查；世界正常保存、服务正常退出。测试环境是实际验收组合，不等同于运行依赖的最低版本。

公开构建与上述验收包逐项比对运行类和游戏资源。公开发行整理了许可证及署名文件，因此整个 JAR 的 SHA-256 与内部验收包不同；非许可运行内容保持一致。发布附件的 release-manifest.json 记录两种哈希，SHA256SUMS.txt 用于核对下载文件。

未新增真实客户端操作、视角/光影、完整整包、高延迟多人或旧重锋 1.1.22/1.1.23 的运行验收，不据此声称 FPS/TPS 提升。本次 GitHub 同步未部署生产。

更新公告：[1.21.1 dev.10](docs/releases/0.3.0-dev.10.md) · [1.20.1 Forge dev.16](docs/releases/mc1.20.1-forge-0.3.0-dev.16.md)。以下保留历史记录，其版本和验证范围不自动适用于当前版本。

---
当前 dev.4 验证见 [更新说明](docs/releases/0.3.0-dev.4.md)。以下为历史记录。

当前 0.3.0-dev.3 验证见 [更新说明](docs/releases/0.3.0-dev.3.md)。以下是历史记录。

当前 0.3.0-dev.2 验证见 [更新说明](docs/releases/0.3.0-dev.2.md)。以下是历史记录。

当前 0.3.0-dev.1 验证见 [更新说明](docs/releases/0.3.0-dev.1.md)。以下是历史记录。

当前 0.2.0-dev.7 验证边界见 [更新说明](docs/releases/0.2.0-dev.7.md)。以下为历史记录。

当前 0.2.0-dev.5 的版本范围与验证见 [更新说明](docs/releases/0.2.0-dev.5.md)，以下为历史记录。

当前 0.2.0-dev.3 的更新与验证见 [Release 说明](docs/releases/0.2.0-dev.3.md)。以下保留历史验收范围。

# Validation: 0.2.0-dev.1

Frozen primary-project source archive SHA-256: 4de6237ca0afd43f3ef89f87661065b0cc1f5cc5fc1f2856bb023b69ebcff6b8. Every archived file checked against the frozen inventory. Public source: 137 files / 118 Java. Public metadata and license packaging differ from the internal JAR; Java implementation is unchanged.

Internal evidence applies to the frozen main + NR + SI combination: 26 blades, 13 SA / 15 SE entrypoints, new-world/config integration using 251 JARs, targeted GUI rendering/Gaia and reconnect/dimension component preservation. New-world integration is not production configuration/world acceptance. Load evidence is controlled FakePlayer traffic, not actual multiplayer networking. Details and limits are in the release notes.

Public CI tests the main mod with the locked Resharpened dependency and r87 contracts; optional external-addon tests skip when absent. The old 1.12/VMD assertions do not establish r87 acceptance. No GUI rerun of the public renamed JAR is claimed.

Production old-world upgrade/rollback, original-config full client pack, real dedicated multiplayer/high latency, full Gaia ritual, all shaders/addons and long-term performance remain unverified. No production deployment.
Public Git-export clean build and three development probe compilation tasks passed. Minimal r87 contracts success=true with 102 core checks plus separately reported groups, normal dimension saves and exit 0. Final packaging restores original OBJ newlines; all four r87 effect assets match the frozen input byte-for-byte. Runtime SHA-256: 9672686341614536dc2b09da694e3981fc4d000c22c909c9ab53afa2d20506c8. Sources SHA-256: 9cefab5c43799a5d9d15664457a0021d14f60579c003d48ab3ca9284b0963e47. CI reruns contracts on the final commit.
