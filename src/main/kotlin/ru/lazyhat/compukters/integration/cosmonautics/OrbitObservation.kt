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

/** Validation is independent of Minecraft and never manufactures an orbit for unavailable data. */
internal object OrbitObservation {
    fun valid(
        mu: Double,
        a: Double,
        e: Double,
        inclination: Double,
        position: List<Double>,
        velocity: List<Double>,
    ): Boolean {
        if (!mu.isFinite() || mu <= 0.0 || !a.isFinite() || a == 0.0) return false
        if (!e.isFinite() || e < 0.0 || e == 1.0) return false
        if ((a > 0.0) != (e < 1.0)) return false
        if (!inclination.isFinite() || inclination < 0.0 || inclination > Math.PI) return false
        if (position.size != 3 || velocity.size != 3 || position.any { !it.isFinite() } || velocity.any { !it.isFinite() }) return false
        if (position.all { it == 0.0 }) return false
        val periapsis = a * (1.0 - e)
        return periapsis.isFinite() && periapsis > 0.0
    }
}
