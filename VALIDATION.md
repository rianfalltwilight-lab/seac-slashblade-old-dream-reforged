# Validation record: 0.1.0-dev.10

Public source contains 51 files, including 45 Java files. Stable mod ID: slashblade_legacy_compat. Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21; locked SlashBlade 2.0.5-1.21.1 dependency remains unchanged.

## Public candidate verification

Clean Java 21 build and all isolated contracts (including scex.dev9Audit=true) passed in a new test directory on 2026-09-06. SI integration tests explicitly skip without the optional third-party mod. Runtime excludes contract classes and external dependency binaries.

An initial run in a reused test world failed the taunt contract with present=false for its target entity. The fresh-directory run passed. Existing-world test repeatability is not claimed, and the failed log is retained internally. CI runs in a fresh checkout.

Runtime: 107139 bytes; SHA-256: 1e156254291e2db91317a4d34b153d19e6e77440de844969d0804f1c4eb1d2aa

Sources JAR: 52769 bytes; SHA-256: c4c04b58744e2a7dfdc67e01adc2c06b05a033004ab0fd1b0f700e9bbb4dde48

Public metadata and bundled license notices differ from the internal development artifact, so their JAR hashes are not expected to match. Main Java source is copied unchanged from the authoritative dev.10 source.

## Prior development evidence

Authoritative dev.10 reports record successful minimal and locked full-mod contracts. Of 67 named blades, 65 passed three-combo checks, 2 had no BladeState and were skipped. Ten SI electric blades passed basic energy and serialization checks. These are prior internal results, not the scope of public CI. No third-party JARs, production data or internal input-path manifests are distributed.

## Limits

Real first/third-person visuals, high-latency multiplayer, actual network packets, production-world uninstall, and scaled performance remain unverified. Allocation changes have no measured performance claim. No production server or player pack is deployed by publishing this prerelease. See [release notes](docs/releases/0.1.0-dev.10.md).
A clean Git archive export also passed build and contracts. Hashes above refer to that LF-normalized public export.
