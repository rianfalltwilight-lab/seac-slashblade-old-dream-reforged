# Validation record: 0.1.0-dev.11

57 source files, including 51 Java files. Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21; unchanged locked SlashBlade 2.0.5-1.21.1 dependency.

Public Java sources match authoritative dev.11. Public identity and bundled license notices differ from the internal candidate; binary hashes therefore differ. Public export build/contracts and fixed asset hashes are checked before publishing and again by CI.

Authoritative internal dev.11 evidence: clean build, 4 JSON resources, 17 declared mixins, minimal contracts with successful stop/save and exit 0; real GUI client checks for two named blades, first/third-person animations, three-step right-click combos, slash direction and actual landing. API-generated inputs and script-triggered initial helm action are not hardware-input tests. No third-party dependencies or private manifests are redistributed.

Full-pack multiplayer/high latency, all blades/SA/SE, extended combat and rollback world compatibility were not rerun. Optional Botania/SI checks skip in the public minimal environment. Targeted internal client results do not imply a fresh GUI test of the public renamed JAR. No production deployment or measured performance improvement is claimed.

See [release notes](docs/releases/0.1.0-dev.11.md). Historical dev.10 full-mod evidence is not represented as a dev.11 full-mod pass.