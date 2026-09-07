# Validation record: 0.1.0-dev.13

Frozen input source hashes verified. Public inventory: 64 source files / 57 Java files. Java is copied unchanged; public metadata and license packaging differ from internal development artifacts.

Internal GUI lifecycle A/B test: dev.11 reproduces the unloaded-config tooltip/reach exception; dev.13 creates, exits and reopens the same world within one process, observes config unloading, queries real item tooltips during TagsUpdatedEvent, and verifies loaded live switches after reentry. Normal Java exit and dimension saves confirmed. Public renamed artifact does not claim a separate GUI rerun.

Public clean build, isolated minimal server contracts and reproducible asset hashes are gated before publishing and in GitHub CI. Independent persistence probe remains isolated from the standard contract source set.

No dev.13 full-pack, multiplayer/server switching, new first-person image validation or performance benchmark. TanukiDecor was not installed in the minimal client; corresponding API/event failure was reproduced there. Historical combo/landing client-probe issues are not claimed resolved. No production deployment.