/*
 * The Compukters Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cosmonautics.space

import ru.lazyhat.compukters.api.contracts.GuestCall
import ru.lazyhat.compukters.api.contracts.GuestService
import kotlin.math.Quaterniond
import kotlin.math.Vector3d

/** Request tick and dimension. Deep Space membership alone does not imply a bound orbit. */
value class SpaceStatus(
    val dimension: String,
    val gameTick: Long,
    val inDeepSpace: Boolean,
)

/** Current planet; radius/transition altitude are metres. No result exists for a non-planet frame. */
value class PlanetSnapshot(
    val gameTick: Long,
    val frame: String,
    val radius: Double,
    val transitionHeight: Double,
    val allowToSpace: Boolean,
    val allowToDimension: Boolean,
)

/**
 * Upstream Deep Space instance, not an individual construction's solver motion.
 * Position (m) and velocity (m/s) use the named central-body frame. Mu uses m³/s²;
 * semiMajorAxis uses metres (negative for hyperbolic orbits), inclination radians.
 * universeTick is the instance's local upstream clock; gameTick is the request tick.
 */
value class OrbitSnapshot(
    val gameTick: Long,
    val instanceId: Long,
    val universeTick: Long,
    val frame: String,
    val position: Vector3d,
    val velocity: Vector3d,
    val gravitationalParameter: Double,
    val semiMajorAxis: Double,
    val eccentricity: Double,
    val inclination: Double,
)

/**
 * Directions from one Deep Space instance PV observation, expressed in Sable world axes.
 * The pinned Cosmonautics physics adds world velocity components directly to the current
 * orbital frame. These are directions, not world positions or construction solver velocities.
 * radialOut points away from the central body; orbitNormal is radialOut cross prograde.
 * The reference frame can change during propagation: do not retain directions across frame changes.
 */
value class GuidanceSnapshot(
    val dimension: String,
    val gameTick: Long,
    val instanceId: Long,
    val universeTick: Long,
    val frame: String,
    val prograde: Vector3d,
    val radialOut: Vector3d,
    val orbitNormal: Vector3d,
)

/**
 * Raw upstream rotation model at epochSeconds after the Cosmonautics arbitrary epoch.
 * Quaternion components use the JOML/Guest (x,y,z,w) adapter (-q1,-q2,-q3,q0).
 * Rate (rad/s) and acceleration (rad/s²) retain Orekit angular-coordinate conventions;
 * they are not Sable solver angular velocity. Guest prediction must reproduce rotationShiftedBy.
 */
value class PlanetRotationModel(
    val epochSeconds: Double,
    val orientation: Quaterniond,
    val rate: Vector3d,
    val acceleration: Vector3d,
)

/**
 * Read-only surface mapping configuration for the current linked planet.
 * dimension is the surface dimension, even when sampled in Deep Space.
 * universeTick uses the instance clock in space and the universe clock on the surface.
 * Border coordinates are surface blocks/metres; radius and transitionHeight use orbital metres.
 * spherical=false denotes the configured cube with half-size radius.
 * Mu uses m³/s²; influenceRadius is the finite positive upstream central-body ROI (m),
 * not a guarantee against entering another body's frame inside it.
 */
value class PlanetMappingSnapshot(
    val gameTick: Long,
    val universeTick: Long,
    val frame: String,
    val dimension: String,
    val radius: Double,
    val gravitationalParameter: Double,
    val influenceRadius: Double,
    val spherical: Boolean,
    val borderCenterX: Double,
    val borderCenterZ: Double,
    val borderSize: Double,
    val transitionHeight: Double,
    val allowToSpace: Boolean,
    val allowToDimension: Boolean,
    val rotation: PlanetRotationModel,
)

/**
 * Current construction and its allocated Deep Space region, in Sable world axes/metres.
 * constructionPosition is the logical pose origin, not COM. The return offset is position-center.
 * sideLength is the upstream physics coupling size; bounds include its additional one-block extent.
 * loadedConstructions includes the caller; unloadedConstructions counts upstream tracked pointers.
 * timeScale is the current upstream clock setting, not a promise of future rate or a game-tick delta.
 */
value class SpaceRegionSnapshot(
    val dimension: String,
    val gameTick: Long,
    val instanceId: Long,
    val universeTick: Long,
    val frame: String,
    val timeScale: Int,
    val constructionId: String,
    val constructionPosition: Vector3d,
    val constructionDiagonal: Double,
    val center: Vector3d,
    val minimum: Vector3d,
    val maximum: Vector3d,
    val sideLength: Int,
    val loadedConstructions: Int,
    val unloadedConstructions: Int,
)

@GuestService(bindings = "SpaceBindings")
interface Space {
    companion object {
        const val UNIVERSE_TICK_SECONDS: Double = 0.05
    }

    /** Configuration only; no trajectory prediction or target state is retained by the host. */
    @GuestCall(binding = "planetMapping")
    fun planetMapping(): PlanetMappingSnapshot

    /** Throws IllegalStateException outside a loaded, valid Deep Space construction/instance. */
    @GuestCall(binding = "region")
    fun region(): SpaceRegionSnapshot

    @GuestCall(binding = "status")
    fun status(): SpaceStatus

    /** Throws IllegalStateException when the current frame has no linked planet dimension. */
    @GuestCall(binding = "planet")
    fun planet(): PlanetSnapshot

    /** Throws IllegalStateException outside an available, initialized Deep Space instance. */
    @GuestCall(binding = "orbit")
    fun orbit(): OrbitSnapshot

    /**
     * Throws IllegalStateException outside Deep Space or for invalid/degenerate directions.
     * Rejects a nearly radial trajectory: sine of the position/velocity angle must exceed 1e-12.
     * One request observes all directions; a separate Sable snapshot is not atomic with it.
     */
    @GuestCall(binding = "guidance")
    fun guidance(): GuidanceSnapshot
}
