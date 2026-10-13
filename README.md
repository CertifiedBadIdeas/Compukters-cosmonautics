# Compukters: Cosmonautics

Independent Minecraft 1.21.1 / NeoForge addon scaffold for [Compukters](https://github.com/CertifiedBadIdeas/Compukters)
and [Create: Cosmonautics](https://modrinth.com/mod/create-cosmonatics/version/HuCMavv8).

The addon exposes request-only space telemetry through Guest Kotlin `cosmonautics.space.Space`.
It does not yet control Cosmonautics engines or provide a launch autopilot.

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

## Guest telemetry

Declare `cosmonautics` in the project's addon list. `Space.status()` returns the request server `gameTick`,
Minecraft dimension ID and `inDeepSpace`. Deep Space membership does not prove orbital insertion.

`Space.planet()` returns the current linked planet frame name, radius in metres, transition altitude in metres,
and upstream transfer permissions. On the surface it resolves the planet linked to the current dimension; in space
it resolves the planet of the current instance's orbital frame. A non-planet frame or planet without a linked
Minecraft dimension is unavailable. Radius and transition altitude are upstream values, not a measured local terrain height.

`Space.orbit()` requires the computer to be on a loaded Sable construction inside an initialized Deep Space
instance. It returns instance ID, request `gameTick`, instance-local `universeTick`, named central-body frame,
position (m), velocity (m/s), gravitational parameter (m³/s²), signed semi-major axis (m), eccentricity and
inclination (radians). These are **instance** position/velocity and current osculating elements, not an individual
construction's Sable solver pose/velocity. A retained value snapshot does not change after upstream advances.
The orbital clock can have a different timescale from the server tick. Separate telemetry requests are not atomic.

`bound` identifies elliptic rather than hyperbolic motion. `periapsisRadius()` measures distance from the frame
origin; subtract a matching planet's radius to estimate periapsis altitude. `apoapsisRadius()` rejects unbound
motion and unrepresentable results. A bound orbit alone is not safe: compare its periapsis with the relevant
planet/transition boundary. Planet shape and upstream atmosphere/transfer rules still matter.

Missing, corrupt or uninitialized observations throw catchable `IllegalStateException`; no unavailable fields
are represented by NaN, infinity or fabricated zero values. `status()` remains usable while the universe initializes.
The adapter reads upstream state on the server thread without advancing physics, propagating orbits or installing subscriptions.

Use Sable `Physics.snapshot()` for construction attitude and solver angular velocity. Euler angles and their derivatives
are not substituted for physical angular velocity. The first telemetry API does not expose the rotation connecting
orbital frame axes to Sable world axes; orbital guidance must not directly feed orbital velocity into world-space steering.
It also makes no guarantee of uninterrupted program/engine ownership across dimension transfers.

Focused checks: `updateCompuktersAddonAbiLock`, `check`, and `developmentModJar`. Pure observation tests cover finite
elliptic/hyperbolic data and invalid numerical boundaries. Real surface/Deep Space observations require a world
with the Cosmonautics dimensions; a vanilla GameTest world is insufficient.
