# SEAC SlashBlade Old Dream Reforged

![Old Dream Reforged cover](src/main/resources/cover.png)

**[⬇ Download 0.2.0-dev.5 runtime JAR](https://github.com/rianfalltwilight-lab/seac-slashblade-old-dream-reforged/releases/download/v0.2.0-dev.5/SEAC-SlashBlade-Old-Dream-Reforged-1.21.1-0.2.0-dev.5.jar)**

For Minecraft 1.21.1. Place the downloaded JAR in mods.

Version **0.2.0-dev.5**, development prerelease. Active reference: **SlashBlade mc1.7.10-r87**. Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21. Required Resharpened dependency is tested at 2.0.5-1.21.1; relaxed version metadata does not guarantee compatibility with other internal APIs.

Restores legacy combos, combat formulas, swords/formations, nine base arts, enchantment/repair/break/anvil and blade-soul behavior, retaining modern protection cancellation and explicit adaptations. Install matching client/server versions. Separate NR/SI candidates are not bundled.

Build and isolated r87 contracts: `./gradlew --no-daemon clean build runContracts`. Dependencies are fetched with fixed hashes. Probe JARs are development-only. Back up mods, configuration and worlds; validate upgrades and rollbacks on copies. Removing this mod alone is not guaranteed safe for new combo states or in-flight entities.

See [release notes](docs/releases/0.2.0-dev.5.md), [validation](VALIDATION.md), [third-party attribution](THIRD-PARTY-NOTICES.md) and [AI disclosure](AI-GENERATED.md). Independent code is MIT; legacy-derived portions retain original r87 and 1.12 terms.