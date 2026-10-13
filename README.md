# Compukters: Cosmonautics

Independent Minecraft 1.21.1 / NeoForge addon scaffold for [Compukters](https://github.com/CertifiedBadIdeas/Compukters)
and [Create: Cosmonautics](https://modrinth.com/mod/create-cosmonatics/version/HuCMavv8).

The mod currently has an empty NeoForge entry point. It provides no Guest API, peripherals, spacecraft controls,
commands or mixins. There is no Guest bundle or ABI lock until the first real Guest API is introduced.

## Development

Run Gradle on JDK 25 with a Java 21 toolchain available. In a Compukters workspace, initialize the submodules and run
`./gradlew-sandbox-dev-parallel-summary stageAddonDevelopmentDependencies` at the workspace root first. Then run here:

```sh
./gradlew-sandbox-dev-parallel-summary check developmentModJar
./gradlew-sandbox-dev-parallel runClient
```

The standalone development runtime pins Cosmonautics 26.08.307 (`HuCMavv8`), Create 6.0.10, Sable 2.0.6 and the
Aeronautics 1.3.2 bundle, including Simulated and Offroad. Cosmonautics supplies both `rocketnautics` and
`dimensional_sable`. Loom development runs explicitly load the nested Aeronautics and Sable dependencies.
The shared `addons/dev` client/server stand also composes this addon with the other independent Compukters addons.
Cosmonautics requires its Deep Space dimension on server ticks. Vanilla GameTest servers create only their test world,
so the shared GameTest composition excludes Cosmonautics and this scaffold. No GameTest archive is provided yet;
future world integration tests must use a server with the required dimensions.

For a checkout outside the workspace, select `-PcompuktersDependencyMode=release` with a published SDK version through
`-PcompuktersRelease=<version>` or an extracted SDK through `-PcompuktersSdkDirectory=<path>`.

## Packaging and future API

`buildProductionJar` verifies the independent addon archive; `stageDistributionJar` stages it under `build/distribution`.
Addon version `1.0` follows the first-party `x.y` policy. The archive name includes the selected Compukters line and
metadata bounds compatibility to that line. Upstream mods and Compukters implementations remain external dependencies.

When adding real Guest operations, apply `ru.lazyhat.compukters.addon`, register `cosmonautics`, add Guest sources and
an ABI lock, and use the SDK Host/Guest contracts. Until then, the SDK API dependencies are compile-only and the mod
performs no registration beyond its NeoForge entry point. No tagged Guest-bundle release pipeline is configured yet.
