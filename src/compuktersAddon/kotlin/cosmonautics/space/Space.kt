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

public object Space {
    public fun status(): SpaceStatus = SpaceBindings.status()

    /** Throws IllegalStateException when the current frame has no linked planet dimension. */
    public fun planet(): PlanetSnapshot = SpaceBindings.planet()

    /** Throws IllegalStateException outside an available, initialized Deep Space instance. */
    public fun orbit(): OrbitSnapshot = SpaceBindings.orbit()
}

private object SpaceBindings {
    external fun status(): SpaceStatus
    external fun planet(): PlanetSnapshot
    external fun orbit(): OrbitSnapshot
}
