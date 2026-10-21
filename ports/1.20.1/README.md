# 旧梦重铸 · Minecraft 1.20.1

Forge **0.3.0-dev.16** 的公开源码。运行代码、资源及契约测试与本次验收源码一致；本目录是独立 Gradle 工程。NeoForge 1.20.1 的构建开关保留，本次发布资产为 Forge 版。

新增隔墙瞬步锚点、旧版手动锁定与按住重试、SB 隔墙选敌，并调整幻影剑碰撞和评分冷却存储。Forge 47.4.10、重锋 1.9.65 的独立服务端已通过 19 项新增断言、AirTrick 19 项、DualMode 64 项、Legacy17 102 项及 Performance 正确性回归。本次没有新增真实客户端、光影、整包或高延迟多人验收。

## 构建

安装 **JDK 21** 和 **JDK 17**。Gradle 9.2.1 由 JDK 21 启动，模组使用 Java 17 工具链编译；Minecraft 1.20.1 运行也使用 Java 17。将 `JAVA_HOME` 指向 JDK 21；若 Gradle 未自动发现 JDK 17，在自己的 Gradle 用户配置中设置 `org.gradle.java.installations.paths`，无需修改仓库。

进入本目录后执行：

```powershell
pwsh -File ./prepare-dependencies.ps1
./gradlew.bat build --max-workers=1 -Ploader=forge
```

依赖准备脚本优先使用 `libs/` 和用户缓存，缺失时从 `dependencies.sha256.json` 中的官方地址直连下载，并校验文件大小和 SHA-256。可用 `-CacheDirectory <已有依赖目录> -Offline` 只复用本地副本。也可从该清单的官方页面手动取得重锋 JAR 并放入 `libs/`；构建会再次校验。

Linux/macOS 可用 `./gradlew` 替代 `./gradlew.bat`；依赖脚本需要 PowerShell 7，也可按上述方法手动准备依赖。首次 Gradle 构建需要访问官方 Maven 仓库。`--offline` 只适用于完整缓存；NeoForm 的游戏构件缓存可通过 `-PneoFormCacheDir=<目录>` 选择。

Forge 安装包输出为 `build/forge/libs/slashblade-legacy-compat-forge-1.20.1-0.3.0-dev.16.jar`。`-Ploader=neoforge` 构建 NeoForge 47.1.106 版，输出至 `build/libs/`。加载器版本和重锋 1.9.65 是构建锁定值；运行依赖范围保留在模组元数据中。

`contractsProbeJar persistenceProbeJar` 可生成独立测试探针。探针与 `-sources.jar` 都不是玩家安装包。`integration-20261021.flag` 选择新增断言及关联服务端回归；测试应使用独立世界。

## 源码与许可

`SOURCE-MANIFEST.sha256` 列出本子工程公开文件。仓库不含重锋依赖 JAR、构建输出、游戏运行副本、测试世界或内部运维脚本。仅 Gradle Wrapper JAR 随源码提供。

原创贡献和上游材料的许可分别见 [LICENSE.md](LICENSE.md) 与 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。旧版拔刀剑原始使用条件和署名完整保留。AI 协助说明见 [AI-GENERATED.md](AI-GENERATED.md)。
