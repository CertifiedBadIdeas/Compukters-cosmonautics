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

import cosmonautics.Vector3d
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Rotation
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.RotationConvention
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedAngularCoordinates
import org.joml.Quaterniond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NavigationObservationTest {
    @Test
    fun `dated rotation preserves upstream vector mapping and raw rate convention`() {
        val rotation = Rotation(Vector3D(1.0, 2.0, -3.0), 1.2, RotationConvention.VECTOR_OPERATOR)
        val rate = Vector3D(0.1, -0.2, 0.3)
        val acceleration = Vector3D(-0.001, 0.002, 0.003)
        val coordinates = TimeStampedAngularCoordinates(AbsoluteDate.ARBITRARY_EPOCH.shiftedBy(-123.75), rotation, rate, acceleration)
        val observed = assertNotNull(NavigationObservation.rotation(coordinates))
        assertEquals(-123.75, observed.epochSeconds)
        assertEquals(Vector3d(0.1, -0.2, 0.3), observed.rate)
        assertEquals(Vector3d(-0.001, 0.002, 0.003), observed.acceleration)
        val q = observed.orientation
        val actual = Quaterniond(q.x, q.y, q.z, q.w).transform(org.joml.Vector3d(7.0, -4.0, 2.0))
        val expected = rotation.applyTo(Vector3D(7.0, -4.0, 2.0))
        assertEquals(expected.x, actual.x, 1e-12)
        assertEquals(expected.y, actual.y, 1e-12)
        assertEquals(expected.z, actual.z, 1e-12)
        // Reading the model never moves its reference epoch to the observation time.
        assertEquals(-123.75, coordinates.date.durationFrom(AbsoluteDate.ARBITRARY_EPOCH))
    }

    @Test
    fun `invalid model has no fabricated rotation or epoch`() {
        val epoch = AbsoluteDate.ARBITRARY_EPOCH
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(
                NavigationObservation.rotation(
                    TimeStampedAngularCoordinates(
                        epoch,
                        Rotation.IDENTITY,
                        Vector3D(invalid, 0.0, 0.0),
                        Vector3D.ZERO,
                    ),
                ),
            )
            assertNull(
                NavigationObservation.rotation(
                    TimeStampedAngularCoordinates(
                        epoch,
                        Rotation.IDENTITY,
                        Vector3D.ZERO,
                        Vector3D(0.0, invalid, 0.0),
                    ),
                ),
            )
            assertNull(
                NavigationObservation.rotation(
                    TimeStampedAngularCoordinates(
                        epoch,
                        Rotation(invalid, 0.0, 0.0, 0.0, false),
                        Vector3D.ZERO,
                        Vector3D.ZERO,
                    ),
                ),
            )
        }
        assertNull(
            NavigationObservation.rotation(
                TimeStampedAngularCoordinates(
                    epoch,
                    Rotation(0.0, 0.0, 0.0, 0.0, false),
                    Vector3D.ZERO,
                    Vector3D.ZERO,
                ),
            ),
        )
        assertNull(
            NavigationObservation.rotation(
                TimeStampedAngularCoordinates(
                    AbsoluteDate.FUTURE_INFINITY,
                    Rotation.IDENTITY,
                    Vector3D.ZERO,
                    Vector3D.ZERO,
                ),
            ),
        )
    }
}
