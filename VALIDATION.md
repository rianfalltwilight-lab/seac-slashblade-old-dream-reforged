# Validation: 0.2.0-dev.1

Frozen primary-project source archive SHA-256: 4de6237ca0afd43f3ef89f87661065b0cc1f5cc5fc1f2856bb023b69ebcff6b8. Every archived file checked against the frozen inventory. Public source: 137 files / 118 Java. Public metadata and license packaging differ from the internal JAR; Java implementation is unchanged.

Internal evidence applies to the frozen main + NR + SI combination: 26 blades, 13 SA / 15 SE entrypoints, new-world/config integration using 251 JARs, targeted GUI rendering/Gaia and reconnect/dimension component preservation. New-world integration is not production configuration/world acceptance. Load evidence is controlled FakePlayer traffic, not actual multiplayer networking. Details and limits are in the release notes.

Public CI tests the main mod with the locked Resharpened dependency and r87 contracts; optional external-addon tests skip when absent. The old 1.12/VMD assertions do not establish r87 acceptance. No GUI rerun of the public renamed JAR is claimed.

Production old-world upgrade/rollback, original-config full client pack, real dedicated multiplayer/high latency, full Gaia ritual, all shaders/addons and long-term performance remain unverified. No production deployment.
Public Git-export clean build and three development probe compilation tasks passed. Minimal r87 contracts success=true with 102 core checks plus separately reported groups, normal dimension saves and exit 0. Final packaging restores original OBJ newlines; all four r87 effect assets match the frozen input byte-for-byte. Runtime SHA-256: 9672686341614536dc2b09da694e3981fc4d000c22c909c9ab53afa2d20506c8. Sources SHA-256: 9cefab5c43799a5d9d15664457a0021d14f60579c003d48ab3ca9284b0963e47. CI reruns contracts on the final commit.
