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

package ru.lazyhat.compukters.integration.cosmonautics

import cosmonautics.PlanetRotationModel
import cosmonautics.Quaterniond
import cosmonautics.Vector3d
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedAngularCoordinates

/** Copies numerical configuration only; never advances a live orbit or retains server objects. */
internal object NavigationObservation {
    fun rotation(value: TimeStampedAngularCoordinates): PlanetRotationModel? {
        val q = value.rotation
        val rate = value.rotationRate
        val acceleration = value.rotationAcceleration
        val epoch = value.date.durationFrom(AbsoluteDate.ARBITRARY_EPOCH)
        if (!listOf(epoch, q.q0, q.q1, q.q2, q.q3, rate.x, rate.y, rate.z, acceleration.x, acceleration.y, acceleration.z)
                .all { it.isFinite() }
        ) {
            return null
        }
        val norm = q.q0 * q.q0 + q.q1 * q.q1 + q.q2 * q.q2 + q.q3 * q.q3
        if (!norm.isFinite() || kotlin.math.abs(norm - 1.0) > 1e-10) return null
        return PlanetRotationModel(
            epoch,
            Quaterniond(-q.q1, -q.q2, -q.q3, q.q0),
            Vector3d(rate.x, rate.y, rate.z),
            Vector3d(acceleration.x, acceleration.y, acceleration.z),
        )
    }
}
