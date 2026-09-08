# Validation: 0.2.0-dev.1

Frozen primary-project source archive SHA-256: 4de6237ca0afd43f3ef89f87661065b0cc1f5cc5fc1f2856bb023b69ebcff6b8. Every archived file checked against the frozen inventory. Public source: 137 files / 118 Java. Public metadata and license packaging differ from the internal JAR; Java implementation is unchanged.

Internal evidence applies to the frozen main + NR + SI combination: 26 blades, 13 SA / 15 SE entrypoints, new-world/config integration using 251 JARs, targeted GUI rendering/Gaia and reconnect/dimension component preservation. New-world integration is not production configuration/world acceptance. Load evidence is controlled FakePlayer traffic, not actual multiplayer networking. Details and limits are in the release notes.

Public CI tests the main mod with the locked Resharpened dependency and r87 contracts; optional external-addon tests skip when absent. The old 1.12/VMD assertions do not establish r87 acceptance. No GUI rerun of the public renamed JAR is claimed.

Production old-world upgrade/rollback, original-config full client pack, real dedicated multiplayer/high latency, full Gaia ritual, all shaders/addons and long-term performance remain unverified. No production deployment.