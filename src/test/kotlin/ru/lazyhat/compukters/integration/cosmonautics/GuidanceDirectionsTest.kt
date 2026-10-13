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
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GuidanceDirectionsTest {
    @Test
    fun `directions preserve world axes and orbital handedness`() {
        val directions = assertNotNull(GuidanceDirections.from(Vector3d(10.0, 0.0, 0.0), Vector3d(0.0, 0.0, -2.0)))
        assertEquals(Vector3d(1.0, 0.0, 0.0), directions.radialOut)
        assertEquals(Vector3d(0.0, 0.0, -1.0), directions.prograde)
        assertEquals(0.0, directions.orbitNormal.x, 1e-15)
        assertEquals(1.0, directions.orbitNormal.y, 1e-15)
        assertEquals(0.0, directions.orbitNormal.z, 1e-15)
    }

    @Test
    fun `scaled normalization handles subnormal and maximum finite components`() {
        for (magnitude in listOf(Double.MIN_VALUE, 1e-300, 1.0, Double.MAX_VALUE)) {
            val directions = assertNotNull(GuidanceDirections.from(Vector3d(magnitude, magnitude, 0.0), Vector3d(0.0, 0.0, magnitude)))
            assertEquals(1.0 / sqrt(2.0), directions.radialOut.x, 1e-15)
            assertEquals(1.0 / sqrt(2.0), directions.radialOut.y, 1e-15)
            assertEquals(1.0 / sqrt(2.0), directions.orbitNormal.x, 1e-15)
            assertEquals(-1.0 / sqrt(2.0), directions.orbitNormal.y, 1e-15)
            assertEquals(Vector3d(0.0, 0.0, 1.0), directions.prograde)
        }
    }

    @Test
    fun `invalid and nearly radial trajectories have no invented direction`() {
        val radial = Vector3d(1.0, 0.0, 0.0)
        for (invalid in listOf(
            Vector3d(0.0, 0.0, 0.0),
            Vector3d(Double.NaN, 1.0, 0.0),
            Vector3d(0.0, Double.POSITIVE_INFINITY, 0.0),
            Vector3d(0.0, 0.0, Double.NEGATIVE_INFINITY),
        )) {
            assertNull(GuidanceDirections.from(invalid, radial))
            assertNull(GuidanceDirections.from(radial, invalid))
        }
        assertNull(GuidanceDirections.from(radial, radial))
        assertNull(GuidanceDirections.from(radial, Vector3d(-1.0, 0.0, 0.0)))
        assertNull(GuidanceDirections.from(radial, Vector3d(1.0, 1e-300, 0.0)))
        assertNull(GuidanceDirections.from(radial, Vector3d(1.0, 0.5e-12, 0.0)))
        assertNotNull(GuidanceDirections.from(radial, Vector3d(1.0, 2e-12, 0.0)))
    }
}
