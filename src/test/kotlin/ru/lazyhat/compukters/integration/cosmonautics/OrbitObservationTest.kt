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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrbitObservationTest {
    private fun valid(
        a: Double = 1000.0,
        e: Double = 0.1,
        mu: Double = 1e5,
        i: Double = 0.0,
        p: List<Double> = listOf(900.0, 0.0, 0.0),
        v: List<Double> = listOf(0.0, 11.0, 0.0),
    ): Boolean = OrbitObservation.valid(mu, a, e, i, p, v)

    @Test
    fun `elliptic and hyperbolic observations remain distinct and valid`() {
        assertTrue(valid())
        assertTrue(valid(e = 0.0))
        assertTrue(valid(a = -1000.0, e = 1.5))
        assertFalse(valid(a = 1000.0, e = 1.5))
        assertFalse(valid(a = -1000.0, e = 0.5))
        assertFalse(valid(e = 1.0))
    }

    @Test
    fun `invalid or unrepresentable observations are unavailable`() {
        for (number in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(valid(a = number))
            assertFalse(valid(e = number))
            assertFalse(valid(mu = number))
            assertFalse(valid(i = number))
            assertFalse(valid(p = listOf(number, 1.0, 0.0)))
            assertFalse(valid(v = listOf(0.0, number, 0.0)))
        }
        assertFalse(valid(mu = 0.0))
        assertFalse(valid(a = 0.0))
        assertFalse(valid(e = -0.1))
        assertFalse(valid(i = Math.PI + 0.01))
        assertFalse(valid(p = listOf(0.0, 0.0, 0.0)))
        assertFalse(valid(a = -Double.MAX_VALUE, e = 3.0))
    }
}
