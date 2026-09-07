# Validation record: 0.1.0-dev.12

Frozen dev.12 archive is verified against its source input SHA-256 manifest. Public inventory: 62 files, 55 Java. Public metadata and bundled notices differ from the internal JAR, but Java implementation is copied unchanged. Third-party dependencies, internal paths and logs are excluded.

Internal targeted GUI camera comparison passed for two blades and eight views each: maximum combined model-view/pose component difference reduced from 1.68033495 to 1.62811223e-7 (threshold 2e-5), with image review and normal process/save exit. This does not constitute a fresh GUI test of the public renamed artifact.

Two initial dev.11 integrated-client baseline runs failed landing and third-click combo assertions before camera checks. Their cause remains unresolved; the independent camera probe does not waive those failures. Public minimal server contracts cannot resolve client synchronization failures.

Full pack, multiplayer, shaders/camera replacement mods, left dominant hand, other FOV, all blades and performance are not validated for dev.12. No production deployment. See the release notes for scope and retained issues.
Public Git-export clean build, three probe JAR compilation tasks and all applicable minimal server contracts passed with normal saves and exit 0. Runtime SHA-256: 188dceab1b5b9be77c8e6e4427b41fba1dff81cac1c5e87163202a15ee5ad18b. Sources SHA-256: 213b7ffa6f9da07fe6a8339242ceff7d605c4eb12c59fd51426d2c037fe33b6d. Initial public run failed because the independent persistence probe entrypoint was included in the standard contracts source set without its mod metadata. The public build now isolates that probe in its own source set; the fresh rerun passed. No production Java was modified.
