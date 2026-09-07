# Validation record: 0.1.0-dev.12

Frozen dev.12 archive is verified against its source input SHA-256 manifest. Public inventory: 62 files, 55 Java. Public metadata and bundled notices differ from the internal JAR, but Java implementation is copied unchanged. Third-party dependencies, internal paths and logs are excluded.

Internal targeted GUI camera comparison passed for two blades and eight views each: maximum combined model-view/pose component difference reduced from 1.68033495 to 1.62811223e-7 (threshold 2e-5), with image review and normal process/save exit. This does not constitute a fresh GUI test of the public renamed artifact.

Two initial dev.11 integrated-client baseline runs failed landing and third-click combo assertions before camera checks. Their cause remains unresolved; the independent camera probe does not waive those failures. Public minimal server contracts cannot resolve client synchronization failures.

Full pack, multiplayer, shaders/camera replacement mods, left dominant hand, other FOV, all blades and performance are not validated for dev.12. No production deployment. See the release notes for scope and retained issues.