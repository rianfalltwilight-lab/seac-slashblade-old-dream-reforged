# Third-party sources and attribution

SEAC independent implementation is MIT. Legacy-derived code and assets retain the original terms; the project does not relicense those portions under MIT.

## SlashBlade mc1.7.10-r87

Active behavior and procedural-animation reference: Furia/flammpfeil and contributors, commit `83429584fc2ee437cb48c2d31d23f353cb72bd84`, [source](https://github.com/flammpfeil/SlashBlade/tree/83429584fc2ee437cb48c2d31d23f353cb72bd84). Original usage conditions are preserved verbatim in LICENSE-LEGACY17-README.txt, including the original shockwave-source credit to 蛍光塗料氏 and 無銘剣. Do not infer a project-wide license from the Forge template.

Combat, damage, rank, enchantment, repair, break, guard, phantom sword, formation and nine base special-attack classes adapt r87 formulas. LegacyBladeMesh, LegacyPhantomMesh and LegacyDriveMesh contain original procedural projectile mesh data. Bundled model/legacy17 trail.obj, trail.png, slashdim.obj and slashdim.png are original r87 effect resources. Installed held-blade models and textures remain external. Modern damage acceptance/cancellation precedes adapted health cuts; Upthrust counts only the owner's matching markers.

Earlier adapters remain traceable to the 1.12.2 commit `ba1ef8604c0971f68336b882b42a868df7f32f0b`; its terms are retained in LICENSE-LEGACY-README.txt. User-requested taunt and Godsteel Looting III are deliberate extensions.

## External dependencies

[SlashBlade: Resharpened](https://github.com/mrqx0195/SlashBlade_Resharped), M Mysterious Mountain Forging-shop Group and contributors: dependency metadata declares MIT code and art All Rights Reserved. Dependency JAR/models/textures are not embedded. Reproducible build uses 2.0.5-1.21.1, SHA-256 `5b60ff41d89d63e34fbae3d20d9663ae96aa4c594a10b9889a4a39905d44d1c4`.

NegoreRouse and Singularity Iteration integrations are optional external projects. Their registrations, models, textures, SA/SE and binaries are not redistributed here. Minecraft/Mojang client binaries, mappings and NeoForge binaries are not bundled. Names and trademarks remain with their respective owners.