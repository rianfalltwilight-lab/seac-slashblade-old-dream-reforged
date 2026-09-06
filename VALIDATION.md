# Validation record

## Candidate identity

- Formal name: **SEAC拔刀剑附属 旧梦重铸**
- Version: `0.1.0-dev.7`
- Mod ID: `slashblade_legacy_compat`
- Minecraft / NeoForge / Java: `1.21.1` / `21.1.248` / `21`
- Locked SlashBlade: Resharpened dependency: `2.0.5-1.21.1`, 3,880,588 bytes, SHA-256 `5b60ff41d89d63e34fbae3d20d9663ae96aa4c594a10b9889a4a39905d44d1c4`

The release workflow rebuilds from the tagged public source, reruns the isolated runtime contracts, verifies the dependency identity, checks that contract classes are absent from the runtime JAR, and generates a manifest plus SHA-256 list.

## Verified behavior

- Clean Java 21 / Gradle 9.2.1 / ModDevGradle 2.0.144 build completed successfully.
- Minimal isolated NeoForge server contracts completed with `success: true` for broken damage/reach, wall and range authority, SB lifecycle, charge windows, hostile targeting, combo and movement rules, drive/additional attacks, projectile guard/upthrust, and sheathing repair.
- A separate locked 246-mod server combination completed the same contracts with `success: true`, including the Gaia Guardian cases.
- Resource validation confirmed all declared mixin classes exist and development contract classes are excluded from the runtime JAR.
- The prior frozen internal candidate JAR was 89,592 bytes with SHA-256 `2e72c4e5caa15cd591a33d6e715121b8ac17140785820883552d28432fb0c025` before the public display-name and build-input changes.

## Not verified

- Real-client first-person and third-person feel, frame-level visual parity, shader blending, and first-person positioning.
- Two-client synchronization, high latency, packet duplication, reconnect/restart behavior, and long-duration performance.
- Production world, player data, full client pack, and live-server deployment.
- Complete parity with every historical SlashBlade feature. The contracts cover only the behavior documented in this repository.

This is an **R (research/development) prerelease**, not an F/I/P acceptance. Publishing it does not deploy it to a server or player pack.
