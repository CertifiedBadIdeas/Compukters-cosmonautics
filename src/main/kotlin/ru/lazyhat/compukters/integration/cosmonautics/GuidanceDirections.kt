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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Pure numerical boundary; no Minecraft state, frame transform or fabricated fallback direction. */
internal data class GuidanceDirections(
    val prograde: Vector3d,
    val radialOut: Vector3d,
    val orbitNormal: Vector3d,
) {
    companion object {
        fun from(
            position: Vector3d,
            velocity: Vector3d,
        ): GuidanceDirections? {
            val radial = unit(position) ?: return null
            val prograde = unit(velocity) ?: return null
            val cross =
                Vector3d(
                    radial.y * prograde.z - radial.z * prograde.y,
                    radial.z * prograde.x - radial.x * prograde.z,
                    radial.x * prograde.y - radial.y * prograde.x,
                )
            // Unit inputs bound products; reject a numerically ill-defined orbital plane.
            val sine = sqrt(cross.x * cross.x + cross.y * cross.y + cross.z * cross.z)
            if (sine <= 1e-12) return null
            return GuidanceDirections(prograde, radial, unit(cross) ?: return null)
        }

        private fun unit(value: Vector3d): Vector3d? {
            if (!value.x.isFinite() || !value.y.isFinite() || !value.z.isFinite()) return null
            val scale = max(abs(value.x), max(abs(value.y), abs(value.z)))
            if (scale == 0.0) return null
            val x = value.x / scale
            val y = value.y / scale
            val z = value.z / scale
            val length = sqrt(x * x + y * y + z * z)
            return Vector3d(x / length, y / length, z / length)
        }
    }
}
