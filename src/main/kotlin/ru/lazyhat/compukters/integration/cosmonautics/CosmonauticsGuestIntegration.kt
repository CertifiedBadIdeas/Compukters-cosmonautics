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

import cosmonautics.CosmonauticsAddonContract
import cosmonautics.CosmonauticsCapabilityHandler
import cosmonautics.GuidanceSnapshot
import cosmonautics.OrbitSnapshot
import cosmonautics.PlanetMappingSnapshot
import cosmonautics.PlanetSnapshot
import cosmonautics.SpaceRegionSnapshot
import cosmonautics.SpaceStatus
import dev.devce.rocketnautics.RocketConfig
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper
import dev.devce.rocketnautics.content.RocketDimensions
import dev.devce.rocketnautics.content.orbit.DeepSpaceData
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.world.phys.Vec3
import ru.lazyhat.compukters.api.addon.AddonCallResult
import ru.lazyhat.compukters.api.addon.addonCompleted
import ru.lazyhat.compukters.api.addon.addonFailed
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersAddonHostFactory
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersAddonRegistry
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersComputerContext
import ru.lazyhat.compukters.lang.runtime.vm.HostFailureKind
import cosmonautics.Vector3d as GuestVector

internal object CosmonauticsGuestIntegration {
    fun register() {
        CompuktersAddonRegistry.register(
            CosmonauticsAddonContract.guestApi(CosmonauticsGuestIntegration::class.java),
            CompuktersAddonHostFactory { computer -> CosmonauticsAddonContract.host(CosmonauticsHost(computer)) },
        )
    }
}

private class CosmonauticsHost(
    private val computer: CompuktersComputerContext,
) : CosmonauticsCapabilityHandler {
    override fun checkpoint(): ByteArray = byteArrayOf()

    private fun checkThread() {
        check(computer.level.server.isSameThread) { "Space telemetry must be copied on the server thread" }
    }

    override fun status(): AddonCallResult<SpaceStatus> {
        checkThread()
        return addonCompleted(
            SpaceStatus(
                computer.level
                    .dimension()
                    .location()
                    .toString(),
                computer.level.gameTime,
                DeepSpaceHelper.isDeepSpace(computer.level),
            ),
        )
    }

    private fun manager(): DeepSpaceData? {
        val server = computer.level.server
        return if (DeepSpaceData.tooSoon(server) ||
            server.getLevel(RocketDimensions.DEEP_SPACE) == null
        ) {
            null
        } else {
            DeepSpaceData.getInstance(server)
        }
    }

    private fun instance(data: DeepSpaceData): DeepSpaceInstance? {
        if (!DeepSpaceHelper.isDeepSpace(computer.level) || !computer.level.hasChunkAt(computer.position)) return null
        val construction = Sable.HELPER.getContaining(computer.level, computer.position) as? ServerSubLevel ?: return null
        if (construction.isRemoved) return null
        val p = construction.logicalPose().position()
        if (!p.x.isFinite() || !p.y.isFinite() || !p.z.isFinite() || p.x < Int.MIN_VALUE || p.x > Int.MAX_VALUE || p.z < Int.MIN_VALUE ||
            p.z > Int.MAX_VALUE
        ) {
            return null
        }
        // Match upstream instance lookup, using construction world coordinates rather than plot coordinates.
        return data.getInstanceForPos(p.x.toInt(), p.z.toInt())?.takeIf {
            !it.isCorrupted && it.boundingBox().contains(Vec3(p.x, p.y, p.z)) && !it.position.isCorrupted
        }
    }

    private fun planet(data: DeepSpaceData): CubePlanet? {
        if (!DeepSpaceHelper.isDeepSpace(computer.level)) return data.universe.getPlanetByDimension(computer.level.dimension())
        val frame = instance(data)?.position?.frame ?: return null
        return data.universe.planets.firstOrNull { it.frame().name == frame.name }
    }

    override fun planet(): AddonCallResult<PlanetSnapshot> {
        checkThread()
        val data = manager() ?: return unavailable("Cosmonautics universe is not initialized")
        val planet = planet(data) ?: return unavailable("The current frame has no planet")
        val dimension = planet.linkedDimension() ?: return unavailable("The current planet has no linked dimension")
        if (!planet.radius().isFinite() || planet.radius() <= 0.0) return unavailable("Planet radius is invalid")
        return addonCompleted(
            PlanetSnapshot(
                computer.level.gameTime,
                planet.frame().name,
                planet.radius(),
                dimension.transitionHeight().toDouble(),
                dimension.allowedTransfer().allowToSpace(),
                dimension.allowedTransfer().allowToDimension(),
            ),
        )
    }

    override fun planetMapping(): AddonCallResult<PlanetMappingSnapshot> {
        checkThread()
        val data = manager() ?: return unavailable("Cosmonautics universe is not initialized")
        val inSpace = DeepSpaceHelper.isDeepSpace(computer.level)
        val instance = if (inSpace) instance(data) ?: return unavailable("Deep Space instance is unavailable") else null
        val planet = planet(data) ?: return unavailable("The current frame has no planet")
        val dimension = planet.linkedDimension() ?: return unavailable("The current planet has no linked dimension")
        val surface = computer.level.server.getLevel(dimension.key()) ?: return unavailable("Planet dimension is unavailable")
        val border = surface.worldBorder
        val rotation =
            NavigationObservation.rotation(planet.rotationDescription()) ?: return unavailable("Planet rotation model is invalid")
        val gravity = data.universe.getGravitySourceById(planet.frame().id) ?: return unavailable("Planet gravity source is unavailable")
        val mu = gravity.mu()
        val influenceRadius = gravity.roi()
        if (!mu.isFinite() || mu <= 0.0 || !influenceRadius.isFinite() || influenceRadius <= 0.0) {
            return unavailable("Planet gravity source has unsupported parameters")
        }
        val radius = planet.radius()
        val height = dimension.transitionHeight().toDouble()
        if (!radius.isFinite() || radius <= 0.0 || !border.centerX.isFinite() || !border.centerZ.isFinite() ||
            !border.size.isFinite() || border.size <= 0.0 || height < 0.0
        ) {
            return unavailable("Planet mapping geometry is invalid")
        }
        return addonCompleted(
            PlanetMappingSnapshot(
                computer.level.gameTime,
                instance?.position?.localUniverseTicks ?: data.universeTicks,
                planet.frame().name,
                dimension.key().location().toString(),
                radius,
                mu,
                influenceRadius,
                RocketConfig.SERVER.planetShape.get() == RocketConfig.PlanetShape.SPHERE,
                border.centerX,
                border.centerZ,
                border.size,
                height,
                dimension.allowedTransfer().allowToSpace(),
                dimension.allowedTransfer().allowToDimension(),
                rotation,
            ),
        )
    }

    override fun region(): AddonCallResult<SpaceRegionSnapshot> {
        checkThread()
        val data = manager() ?: return unavailable("Cosmonautics universe is not initialized")
        val instance = instance(data) ?: return unavailable("Deep Space construction/instance is unavailable")
        val construction =
            Sable.HELPER.getContaining(computer.level, computer.position) as? ServerSubLevel
                ?: return unavailable("Construction is unavailable")
        val position = construction.logicalPose().position()
        val bounds = instance.boundingBox()
        val center = instance.center
        val box = construction.boundingBox()
        val diagonal = box.size().length()
        if (!diagonal.isFinite() || diagonal < 0.0 || instance.sideLength <= 0 ||
            !listOf(center.x(), center.y(), center.z()).all { it.isFinite() }
        ) {
            return unavailable("Space region geometry is invalid")
        }
        var loaded = 0
        instance.collectLoadedSublevels(computer.level.server) { loaded++ }
        return addonCompleted(
            SpaceRegionSnapshot(
                computer.level
                    .dimension()
                    .location()
                    .toString(),
                computer.level.gameTime,
                instance.id,
                instance.position.localUniverseTicks,
                instance.position.frame.name,
                instance.position.timescale,
                construction.uniqueId.toString(),
                GuestVector(position.x, position.y, position.z),
                diagonal,
                GuestVector(center.x(), center.y(), center.z()),
                GuestVector(bounds.minX, bounds.minY, bounds.minZ),
                GuestVector(bounds.maxX, bounds.maxY, bounds.maxZ),
                instance.sideLength,
                loaded,
                instance.unloadedSublevels.size,
            ),
        )
    }

    override fun orbit(): AddonCallResult<OrbitSnapshot> {
        checkThread()
        val data = manager() ?: return unavailable("Cosmonautics universe is not initialized")
        val instance = instance(data) ?: return unavailable("Computer is not on a construction in an available Deep Space instance")
        val location = instance.position
        val orbit = location.currentOrbit
        val pv = location.currentPVCoords
        val p = pv.position
        val v = pv.velocity
        if (!OrbitObservation.valid(
                orbit.mu,
                orbit.a,
                orbit.e,
                orbit.i,
                listOf(p.x, p.y, p.z),
                listOf(v.x, v.y, v.z),
            )
        ) {
            return unavailable("Orbital observation is invalid")
        }
        return addonCompleted(
            OrbitSnapshot(
                computer.level.gameTime,
                instance.id,
                location.localUniverseTicks,
                location.frame.name,
                GuestVector(p.x, p.y, p.z),
                GuestVector(v.x, v.y, v.z),
                orbit.mu,
                orbit.a,
                orbit.e,
                orbit.i,
            ),
        )
    }

    override fun guidance(): AddonCallResult<GuidanceSnapshot> {
        checkThread()
        val data = manager() ?: return unavailable("Cosmonautics universe is not initialized")
        val instance = instance(data) ?: return unavailable("Computer is not on a construction in an available Deep Space instance")
        val location = instance.position
        val pv = location.currentPVCoords
        val p = pv.position
        val v = pv.velocity
        val directions =
            GuidanceDirections.from(GuestVector(p.x, p.y, p.z), GuestVector(v.x, v.y, v.z))
                ?: return unavailable("Orbital directions are invalid or the trajectory is nearly radial")
        return addonCompleted(
            GuidanceSnapshot(
                computer.level
                    .dimension()
                    .location()
                    .toString(),
                computer.level.gameTime,
                instance.id,
                location.localUniverseTicks,
                location.frame.name,
                directions.prograde,
                directions.radialOut,
                directions.orbitNormal,
            ),
        )
    }

    private fun <T> unavailable(message: String): AddonCallResult<T> = addonFailed(HostFailureKind.UNAVAILABLE, message)
}
