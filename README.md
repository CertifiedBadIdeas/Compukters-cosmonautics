# Compukters: Cosmonautics

Independent Minecraft 1.21.1 / NeoForge addon for [Compukters](https://github.com/CertifiedBadIdeas/Compukters)
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
so the shared GameTest composition excludes Cosmonautics and this addon. No GameTest archive is provided yet;
future world integration tests must use a server with the required dimensions.

For a checkout outside the workspace, select `-PcompuktersDependencyMode=release` with a published SDK version through
`-PcompuktersRelease=<version>` or an extracted SDK through `-PcompuktersSdkDirectory=<path>`.

## Packaging

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
are not substituted for physical angular velocity. The pinned Deep Space coordinate contract is described below. The construction transfer integration restores
program/engine ownership for the same copied devices; asynchronous restoration does not pause physics.

Focused checks: `updateCompuktersAddonAbiLock`, `check`, and `developmentModJar`. Pure observation tests cover finite
elliptic/hyperbolic data and invalid numerical boundaries. Real surface/Deep Space observations require a world
with the Cosmonautics dimensions; a vanilla GameTest world is insufficient.


## Orbital guidance directions

`Space.guidance()` requires the same loaded-construction/Deep Space instance context as `orbit()`. One server-thread
request copies dimension, `gameTick`, instance ID, instance-local `universeTick`, current orbital frame name and
three unit `Vector3d` directions: `prograde` along velocity, `radialOut` away from the frame origin and `orbitNormal`
along `radialOut cross prograde`. Prograde and radial need not be perpendicular. Invalid/zero vectors and nearly
radial trajectories (sine of the position/velocity angle <= 1e-12) return `UNAVAILABLE` / Guest `IllegalStateException`.
Scaling before normalization supports subnormal and near-maximum finite components without squared-norm overflow.
A non-planet orbital frame does not require a linked Minecraft dimension for these directions.

**Pinned coordinate convention:** Cosmonautics 26.08.307's `SubLevelPhysicsSystemMixin` passes cancelled **world**
velocity components to `DeepSpaceInstance.applyVelocity`; the instance adds those components directly to its current
orbital-frame PV velocity. Thus the directions use the same component axes as Sable world thrust for this pinned
implementation; a new rotation between these axes would be incorrect. This is an upstream operational convention,
not a general mapping of arbitrary Orekit frame positions into Minecraft coordinates. Surface-to-space conversion
separately rotates the construction; this API is unavailable on the surface. Orbital frames can change during
propagation, so callers must check frame/instance/dimension identity rather than retaining directions across changes.

The same mixin transfers solver movement at the **instance boundary**. Inside the box it registers mass with zero
velocity transfer; the engine moves the construction locally first. Guidance observes instance orbital velocity,
not construction solver speed. Separate Sable and guidance requests remain non-atomic.

Evidence: pinned JAR bytecode for `SubLevelPhysicsSystemMixin`, `DeepSpaceInstance` and `SpaceTransitionHandler`,
with [upstream physics source](https://github.com/CosmonauticsTeam/Create-Cosmonautics/blob/main/src/main/java/dev/devce/rocketnautics/mixin/SubLevelPhysicsSystemMixin.java)
as a readable reference. `GuidanceDirectionsTest` checks signs, handedness, extreme finite magnitudes and unavailable
numerical states. The workspace `examples/orbital-prograde` uses standard rotations/vectors and a bounded creative
engine controller. VM numerical scenarios and compilation do not establish actual world convergence.

## Construction transfer

The pinned dimensional Sable warper captures computer resources before template copies and preserves bound peripheral
network members through destination loading and source plot removal. Retained handles require the same device instance
and exclusive process owner after restoration; a replacement or missing device stays stale. Computer checkpoint
publication remains asynchronous and physics continues. Pending world calls may report interruption rather than replaying.

`./gradlew-sandbox-dev-parallel-summary -p addons/cosmonautics runTransferTestServer` from the workspace runs the real
warp on an isolated dedicated server, including Deep Space. A vanilla GameTest server omits that dimension.
The fixture forces a destination chunk so an empty dimension keeps ticking while Guest compilation runs. The harness
uses a disposable world under the addon build directory, checks a retained Guest display handle after plot translation,
rejects a replacement, writes a PASS/FAIL result and stops the server. The Gradle task requires PASS.

To include the actual Propulsion engine, first build its `developmentModJar`, then pass
`-PcompuktersPropulsionTestMod=/absolute/path/to/compukters-propulsion-development-mod.jar` to
`runTransferTestServer`. The optional fixture loads the pinned Create Propulsion dependency, retains the creative
vector engine handle and its commands during a running polling loop, sets throttle to zero and releases control in
Deep Space, verifies zero physical thrust, and rejects a replacement engine through a retained observer handle.
This dependency is confined to the optional verification run, not the production addon.
