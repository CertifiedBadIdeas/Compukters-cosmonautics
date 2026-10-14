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

import kotlin.math.Quaterniond
import kotlin.math.Vector3d

/** Request tick and dimension. Deep Space membership alone does not imply a bound orbit. */
public value class SpaceStatus(public val dimension: String, public val gameTick: Long, public val inDeepSpace: Boolean)

/** Current planet; radius/transition altitude are metres. No result exists for a non-planet frame. */
public value class PlanetSnapshot(
    public val gameTick: Long,
    public val frame: String,
    public val radius: Double,
    public val transitionHeight: Double,
    public val allowToSpace: Boolean,
    public val allowToDimension: Boolean,
)

/**
 * Upstream Deep Space instance, not an individual construction's solver motion.
 * Position (m) and velocity (m/s) use the named central-body frame. Mu uses m³/s²;
 * semiMajorAxis uses metres (negative for hyperbolic orbits), inclination radians.
 * universeTick is the instance's local upstream clock; gameTick is the request tick.
 */
public value class OrbitSnapshot(
    public val gameTick: Long,
    public val instanceId: Long,
    public val universeTick: Long,
    public val frame: String,
    public val position: Vector3d,
    public val velocity: Vector3d,
    public val gravitationalParameter: Double,
    public val semiMajorAxis: Double,
    public val eccentricity: Double,
    public val inclination: Double,
) {
    public val bound: Boolean get() = semiMajorAxis > 0.0 && eccentricity < 1.0

    /** Distance from the central-body origin, not altitude above a planet surface. */
    public fun periapsisRadius(): Double = semiMajorAxis * (1.0 - eccentricity)

    /** Hyperbolic trajectories have no apoapsis. */
    public fun apoapsisRadius(): Double {
        if (!bound) throw IllegalStateException("An unbound orbit has no apoapsis")
        val radius = semiMajorAxis * (1.0 + eccentricity)
        if (!(radius > 0.0 && radius <= Double.MAX_VALUE)) throw IllegalStateException("Apoapsis exceeds Double range")
        return radius
    }
}

/**
 * Directions from one Deep Space instance PV observation, expressed in Sable world axes.
 * The pinned Cosmonautics physics adds world velocity components directly to the current
 * orbital frame. These are directions, not world positions or construction solver velocities.
 * radialOut points away from the central body; orbitNormal is radialOut cross prograde.
 * The reference frame can change during propagation: do not retain directions across frame changes.
 */
public value class GuidanceSnapshot(
    public val dimension: String,
    public val gameTick: Long,
    public val instanceId: Long,
    public val universeTick: Long,
    public val frame: String,
    public val prograde: Vector3d,
    public val radialOut: Vector3d,
    public val orbitNormal: Vector3d,
)

/**
 * Raw upstream rotation model at epochSeconds after the Cosmonautics arbitrary epoch.
 * Quaternion components use the JOML/Guest (x,y,z,w) adapter (-q1,-q2,-q3,q0).
 * Rate (rad/s) and acceleration (rad/s²) retain Orekit angular-coordinate conventions;
 * they are not Sable solver angular velocity. Guest prediction must reproduce rotationShiftedBy.
 */
public value class PlanetRotationModel(
    public val epochSeconds: Double,
    public val orientation: Quaterniond,
    public val rate: Vector3d,
    public val acceleration: Vector3d,
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
public value class PlanetMappingSnapshot(
    public val gameTick: Long,
    public val universeTick: Long,
    public val frame: String,
    public val dimension: String,
    public val radius: Double,
    public val gravitationalParameter: Double,
    public val influenceRadius: Double,
    public val spherical: Boolean,
    public val borderCenterX: Double,
    public val borderCenterZ: Double,
    public val borderSize: Double,
    public val transitionHeight: Double,
    public val allowToSpace: Boolean,
    public val allowToDimension: Boolean,
    public val rotation: PlanetRotationModel,
)

/**
 * Current construction and its allocated Deep Space region, in Sable world axes/metres.
 * constructionPosition is the logical pose origin, not COM. The return offset is position-center.
 * sideLength is the upstream physics coupling size; bounds include its additional one-block extent.
 * loadedConstructions includes the caller; unloadedConstructions counts upstream tracked pointers.
 * timeScale is the current upstream clock setting, not a promise of future rate or a game-tick delta.
 */
public value class SpaceRegionSnapshot(
    public val dimension: String,
    public val gameTick: Long,
    public val instanceId: Long,
    public val universeTick: Long,
    public val frame: String,
    public val timeScale: Int,
    public val constructionId: String,
    public val constructionPosition: Vector3d,
    public val constructionDiagonal: Double,
    public val center: Vector3d,
    public val minimum: Vector3d,
    public val maximum: Vector3d,
    public val sideLength: Int,
    public val loadedConstructions: Int,
    public val unloadedConstructions: Int,
)

public object Space {
    /** One orbital universe tick is 0.05 seconds after the upstream arbitrary epoch. */
    public const val UNIVERSE_TICK_SECONDS: Double = 0.05

    /** Configuration only; no trajectory prediction or target state is retained by the host. */
    public fun planetMapping(): PlanetMappingSnapshot = SpaceBindings.planetMapping()

    /** Throws IllegalStateException outside a loaded, valid Deep Space construction/instance. */
    public fun region(): SpaceRegionSnapshot = SpaceBindings.region()

    public fun status(): SpaceStatus = SpaceBindings.status()

    /** Throws IllegalStateException when the current frame has no linked planet dimension. */
    public fun planet(): PlanetSnapshot = SpaceBindings.planet()

    /** Throws IllegalStateException outside an available, initialized Deep Space instance. */
    public fun orbit(): OrbitSnapshot = SpaceBindings.orbit()

    /**
     * Throws IllegalStateException outside Deep Space or for invalid/degenerate directions.
     * Rejects a nearly radial trajectory: sine of the position/velocity angle must exceed 1e-12.
     * One request observes all directions; a separate Sable snapshot is not atomic with it.
     */
    public fun guidance(): GuidanceSnapshot = SpaceBindings.guidance()
}

private object SpaceBindings {
    external fun planetMapping(): PlanetMappingSnapshot
    external fun region(): SpaceRegionSnapshot
    external fun status(): SpaceStatus
    external fun planet(): PlanetSnapshot
    external fun orbit(): OrbitSnapshot
    external fun guidance(): GuidanceSnapshot
}
