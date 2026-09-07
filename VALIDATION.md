# Validation record: 0.1.0-dev.13

Frozen input source hashes verified. Public inventory: 64 source files / 57 Java files. Java is copied unchanged; public metadata and license packaging differ from internal development artifacts.

Internal GUI lifecycle A/B test: dev.11 reproduces the unloaded-config tooltip/reach exception; dev.13 creates, exits and reopens the same world within one process, observes config unloading, queries real item tooltips during TagsUpdatedEvent, and verifies loaded live switches after reentry. Normal Java exit and dimension saves confirmed. Public renamed artifact does not claim a separate GUI rerun.

Public clean build, isolated minimal server contracts and reproducible asset hashes are gated before publishing and in GitHub CI. Independent persistence probe remains isolated from the standard contract source set.

No dev.13 full-pack, multiplayer/server switching, new first-person image validation or performance benchmark. TanukiDecor was not installed in the minimal client; corresponding API/event failure was reproduced there. Historical combo/landing client-probe issues are not claimed resolved. No production deployment.
Public Git-export clean build, all three probe compilation tasks and applicable minimal server contracts passed; all dimensions saved and exit 0. Runtime SHA-256: fce83f6a05ae9a6645bcca709f7edee7dd9c49eb3179214eca6540e5ba579eaa. Sources SHA-256: fde110e29eeb12e0bd3fd26679e428176f234cf55e0a51a639138fee1563c98c.
