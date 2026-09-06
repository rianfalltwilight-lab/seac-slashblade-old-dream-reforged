# SEAC SlashBlade Add-on: Old Dream Reforged

> An unofficial BOTH-side add-on for SlashBlade: Resharpened on Minecraft 1.21.1 / NeoForge, rebuilding selected combat behavior from the 1.12.2 era.

[简体中文](README.md)

The current version is **0.1.0-dev.10**. It is a development prerelease. Real-client feel, high-latency multiplayer, and production-pack acceptance remain incomplete, so test it against a backed-up world first.

## Compatibility matrix

| Component | Exact version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| SlashBlade: Resharpened | [2.0.5-1.21.1](https://modrinth.com/mod/slashblade-resharped/version/2.0.5-1.21.1) |
| This add-on | 0.1.0-dev.10 |
| Java | 21 |

Install the same add-on version on both client and server. The stable `slashblade_legacy_compat` mod ID is retained; the new formal display name does not change registry identity.

## Implemented scope

- Legacy broken-blade damage and melee reach, with authoritative line-of-sight and range checks.
- Legacy charge timing, default combo graph, immediate melee, rapid movement, aerial moves, and blade/scabbard transforms.
- SB summoned blade, legacy drive follow-ups, projectile interception, induction, and upthrust blast behavior.
- Gaia Guardian and general hostile-target selection compatibility.
- Earned durability repair after 1,000 Proud Souls and a valid sheathing completion.

This is a bounded modern compatibility implementation, not a claim of frame-perfect parity with every legacy animation, packet timing, or visual. See [VALIDATION.md](VALIDATION.md) for the tested boundary.

## Installation

1. Install the exact NeoForge and SlashBlade: Resharpened versions above.
2. Put the runtime JAR in both the client and server `mods` directories.
3. Back up the world and existing blade data before first use. Do not keep two add-on versions together.

Removing the add-on discards its in-flight projectile entities. SlashBlade ignores the compatibility data stored on blades, but removal and rollback should still be tested on a copy of the world.

## Build and contract tests

```powershell
./gradlew --no-daemon clean build
./gradlew --no-daemon runContracts
```

The build resolves the exact SlashBlade dependency from Modrinth Maven and checks its size and SHA-256 against [dependencies.sha256.json](dependencies.sha256.json). The dependency JAR is not redistributed in this repository or its release assets.

`runContracts` uses an isolated development server directory and exits automatically. It is not a substitute for real-client or multiplayer acceptance.

## License, provenance, and AI disclosure

SEAC's independent implementation is released under the [MIT License](LICENSE). Behavior, transforms, and exact mesh data derived from legacy SlashBlade remain subject to the original author's usage terms. The original text and file-level provenance are preserved in [LICENSE-LEGACY-README.txt](LICENSE-LEGACY-README.txt), [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), and [NOTICE](NOTICE).

OpenAI Codex materially assisted analysis, implementation support, testing, public-source audit, documentation, and release preparation. See [AI-GENERATED.md](AI-GENERATED.md) for the exact boundary.


## dev.8–dev.10

[Release notes / 累计更新与验收边界](docs/releases/0.1.0-dev.10.md)
