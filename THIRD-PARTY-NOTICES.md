# Third-party sources and attribution

## Legacy SlashBlade

- Project: [Furia/flammpfeil SlashBlade](https://github.com/flammpfeil/SlashBlade)
- Behavior baseline: commit `ba1ef8604c0971f68336b882b42a868df7f32f0b` from the 1.12.2 line.
- Permission text: the repository's `src/main/resources/readme.txt`, preserved verbatim as [LICENSE-LEGACY-README.txt](LICENSE-LEGACY-README.txt), permits use at the user's own risk and disclaims the author's responsibility.

The following scope is derived from that historical implementation:

- `LegacyBladeMesh.java` contains the exact 26-vertex / 48-triangle SB mesh extracted from `RenderSummonedBlade`.
- `LegacyBladePoseMixin.java` maps legacy blade and scabbard transform equations from `LayerSlashBlade`.
- `LegacyMove.java`, `LegacyCombat.java`, `LegacyDrive.java`, `LegacyAdditionalAttack.java`, `LegacyProjectileGuard.java`, `LegacyUpthrust.java`, and `LegacySheathingRepair.java` adapt documented legacy behavior and timing to the modern API.
- Related mixins and contract tests exercise those adaptations.

Furia/flammpfeil's credit and usage terms must remain with redistributions of these portions. The project does not claim that an unrelated Forge template license relicenses the entire legacy project.

## SlashBlade: Resharpened

- Project: [SlashBlade: Resharpened](https://github.com/MARYT-Studio/SlashBlade_Resharped)
- Locked release: [2.0.5-1.21.1](https://modrinth.com/mod/slashblade-resharped/version/2.0.5-1.21.1)
- Modrinth Maven coordinate: `maven.modrinth:8T53F0sy:cw0UX6eX`
- Locked SHA-256: `5b60ff41d89d63e34fbae3d20d9663ae96aa4c594a10b9889a4a39905d44d1c4`

Resharpened is a compile-time and runtime dependency and is not embedded. Its published metadata identifies code as MIT and art resources as All Rights Reserved. No Resharpened models, textures, sounds, or dependency JAR are included here.

## Other platforms and names

Minecraft, NeoForge, Botania, and all third-party names and trademarks remain the property of their respective owners. Their dependencies and assets are not redistributed by this repository.
