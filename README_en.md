# SlashBlade Reborn — Old Dream Reforged

An unofficial client/server addon for SlashBlade: Resharpened, restoring legacy combos, phantom swords, formations, base special attacks, rank, forging, blade breaking and repair while retaining modern damage-protection interfaces.

Previously published as **SEAC SlashBlade Old Dream Reforged**. The repository address and mod ID `slashblade_legacy_compat` remain the same.

| Minecraft | Loader | Development release |
| --- | --- | --- |
| 1.21.1 | NeoForge | [0.3.0-dev.10](https://github.com/rianfalltwilight-lab/seac-slashblade-old-dream-reforged/releases/tag/v0.3.0-dev.10) |
| 1.20.1 | Forge | [0.3.0-dev.16](https://github.com/rianfalltwilight-lab/seac-slashblade-old-dream-reforged/releases/tag/mc1.20.1-forge-v0.3.0-dev.16) |

Install only the matching runtime JAR on both client and server. Remove the previous addon JAR. Sources JARs and source ZIPs are for development; contract probes are not player release assets.

This update improves lock-on, phantom-sword AirTrick anchors, collision handling and combat bookkeeping. See the [1.21.1 notes](docs/releases/0.3.0-dev.10.md), [1.20.1 Forge notes](docs/releases/mc1.20.1-forge-0.3.0-dev.16.md) and [validation scope](VALIDATION.md).

The repository root builds Minecraft 1.21.1 with Java 21: `./gradlew --no-daemon build`. Run the isolated development contracts with `./gradlew --no-daemon runContracts`. The [1.20.1 port](ports/1.20.1) has separate build instructions. Exact build inputs are pinned independently of runtime dependency ranges.

Project license: **MIT-and-Third-Party-Licenses**. See [LICENSE.md](LICENSE.md), [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), [NOTICE](NOTICE), and [AI-GENERATED.md](AI-GENERATED.md). Third-party dependency binaries are not included in the source repository.

[中文](README.md)