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

import dev.devce.rocketnautics.content.RocketDimensions
import dev.devce.rocketnautics.content.orbit.DeepSpaceData
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates
import dev.egg.SubLevelWarper
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.joml.Vector3d
import ru.lazyhat.compukters.impl.registry.CompuktersRegistry
import ru.lazyhat.compukters.minecraft.computer.ComputerBlockEntity
import ru.lazyhat.compukters.minecraft.computer.ComputerPeripheralIdentity
import ru.lazyhat.compukters.minecraft.display.DisplayBlockEntity
import ru.lazyhat.compukters.minecraft.peripheral.ComputerPeripheralLookup
import ru.lazyhat.compukters.minecraft.peripheral.ComputerPeripheralNames
import net.minecraft.util.Unit as MinecraftUnit

@EventBusSubscriber(modid = "compukters_cosmonautics", bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
object CosmonauticsTransferGameTests {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        event.register(CosmonauticsTransferGameTests::class.java)
    }

    @JvmStatic
    @GameTest(template = "bastion/mobs/empty", templateNamespace = "minecraft", timeoutTicks = 20_000)
    fun retainedHandleCrossesWarpedPlot(helper: GameTestHelper) {
        val propulsion = java.lang.Boolean.getBoolean("compukters.cosmonautics.propulsionTest")
        val source = helper.level
        val created = mutableListOf<Pair<ServerSubLevelContainer, ServerSubLevel>>()
        val destination = requireNotNull(source.server.getLevel(RocketDimensions.DEEP_SPACE))
        val alreadyForced =
            destination.forcedChunks.contains(
                net.minecraft.world.level.ChunkPos
                    .asLong(0, 0),
            )
        // Plot tickets retain Sable chunks, but an empty vanilla dimension still stops ticking after 300 ticks.
        // Compilation may exceed that window; emulate an active destination dimension throughout this fixture.
        destination.setChunkForced(0, 0, true)

        fun cleanup() {
            if (!alreadyForced) destination.setChunkForced(0, 0, false)
            for ((container, body) in created) {
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                if (!body.isRemoved) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            }
        }
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(info: GameTestInfo) = Unit

                override fun testPassed(
                    info: GameTestInfo,
                    runner: GameTestRunner,
                ) {
                    cleanup()
                    TransferTestServer.finish(source.server, true)
                }

                override fun testFailed(
                    info: GameTestInfo,
                    runner: GameTestRunner,
                ) {
                    cleanup()
                    TransferTestServer.finish(source.server, false)
                }

                override fun testAddedForRerun(
                    info: GameTestInfo,
                    rerun: GameTestInfo,
                    runner: GameTestRunner,
                ) = Unit
            },
        )
        val sourceContainer = requireNotNull(SubLevelContainer.getContainer(source))
        val destinationContainer = requireNotNull(SubLevelContainer.getContainer(destination))
        sourceContainer.physicsSystem().setPaused(true)
        destinationContainer.physicsSystem().setPaused(true)

        fun assemble(
            level: net.minecraft.server.level.ServerLevel,
            origin: BlockPos,
            computer: Boolean,
        ): ServerSubLevel {
            val positions = if (computer) listOf(origin, origin.east(), origin.below(), origin.east().below()) else listOf(origin)
            val allPositions = if (computer && propulsion) positions + origin.below(2) else positions
            allPositions.forEach { level.getChunk(it) }
            level.setBlockAndUpdate(
                origin,
                if (computer) CompuktersRegistry.COMPUTER.get().defaultBlockState() else Blocks.STONE.defaultBlockState(),
            )
            if (computer) {
                level.setBlockAndUpdate(origin.east(), CompuktersRegistry.DISPLAY.get().defaultBlockState())
                level.setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState())
                level.setBlockAndUpdate(origin.east().below(), Blocks.STONE.defaultBlockState())
            }
            if (computer && propulsion) {
                level.setBlockAndUpdate(
                    origin.below(2),
                    BuiltInRegistries.BLOCK
                        .get(
                            ResourceLocation.fromNamespaceAndPath("createpropulsion", "creative_vector_thruster"),
                        ).defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.UP),
                )
            }
            return SubLevelAssemblyHelper.assembleBlocks(
                level,
                origin,
                allPositions,
                BoundingBox3i(origin.x, origin.y - 2, origin.z, origin.x + 1, origin.y + 1, origin.z + 1),
            )
        }
        // Real assembly initializes plot chunks and the physics body; allocation alone does neither.
        val occupied = assemble(destination, BlockPos(0, 128, 0), false)
        created += destinationContainer to occupied
        destinationContainer.addForceLoadTicket(occupied, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
        val ship = assemble(source, helper.absolutePos(BlockPos(2, 3, 2)), true)
        created += sourceContainer to ship
        sourceContainer.addForceLoadTicket(ship, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
        var level = source
        var position = ship.plot.centerBlock
        val screenPosition = position.east()
        if (propulsion) {
            TransferNetworkFixture.bind(helper, position, screenPosition, position.below(2))
            ComputerPeripheralNames.setName(
                source,
                ComputerPeripheralIdentity("propulsion", position.below(2), "creative_vector_thruster"),
                "warp-engine",
            )
        } else {
            TransferNetworkFixture.bind(helper, position, screenPosition)
        }
        ComputerPeripheralNames.setName(source, ComputerPeripheralIdentity("compukters-display", screenPosition, "text"), "warp-screen")
        val originalComputer = source.getBlockEntity(position) as ComputerBlockEntity
        val computerId = originalComputer.computerId()
        val screenId = (source.getBlockEntity(screenPosition) as DisplayBlockEntity).screenIdentity()
        val guest = TransferGuestComputerScenario(helper, { position }, { level }, propulsion)
        val sequence = helper.startSequence()
        guest.prepare(sequence, program(propulsion))
        guest.run(sequence, "transfer")
        guest.awaitMarker(sequence, "before-warp", waitingForInput = !propulsion)
        sequence.thenExecute {
            val oldPosition = position
            val existing = destinationContainer.allSubLevels.map { it.uniqueId }.toSet()
            try {
                val data = DeepSpaceData.getInstance(source.server)
                val planet = requireNotNull(data.universe.getPlanetByDimension(source.dimension()))
                val gravity = requireNotNull(data.universe.getGravitySourceById(planet.frame().id))
                val region = data.claimNewInstance(16)
                val radius = planet.radius() * 3.0
                region.position.init(
                    data.universe,
                    planet.frame().name,
                    TimeStampedPVCoordinates(
                        data.universeTime,
                        Vector3D(radius, 0.0, 0.0),
                        Vector3D(0.0, kotlin.math.sqrt(gravity.mu() / radius), 0.0),
                    ),
                )
                SubLevelWarper.WarpSubLevel(ship, destination, Vector3d(region.center))
            } finally {
                destinationContainer.allSubLevels.filter { it.uniqueId !in existing }.forEach { created += destinationContainer to it }
            }
            helper.assertTrue(ship.isRemoved, "source construction was not retired")
            val moved = destinationContainer.allSubLevels.single { it !== occupied }
            level = destination
            position = moved.plot.centerBlock
            destinationContainer.addForceLoadTicket(moved, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
            helper.assertTrue(position != oldPosition, "fixture did not exercise plot translation")
            helper.assertTrue(
                (destination.getBlockEntity(position) as ComputerBlockEntity).computerId() == computerId,
                "computer identity changed",
            )
            helper.assertTrue(
                (destination.getBlockEntity(position.east()) as DisplayBlockEntity).screenIdentity() == screenId,
                "screen identity changed",
            )
            helper.assertTrue(
                ComputerPeripheralLookup.find(destination, position, "compukters-display", "warp-screen").identity?.anchor ==
                    position.east(),
                "network member address did not follow the warp",
            )
        }
        if (!propulsion) {
            guest.awaitMarker(sequence, "before-warp")
            guest.resume(sequence)
        }
        guest.awaitMarker(sequence, "after-warp", uniqueMarker = "before-warp")
        sequence.thenExecute {
            helper.assertTrue(
                (destination.getBlockEntity(position.east()) as DisplayBlockEntity).displayColor(1, 0) == 0x654321,
                "retained handle did not write destination",
            )
            if (propulsion) {
                val engineBlock = destination.getBlockState(position.below(2))
                destination.removeBlock(position.below(2), false)
                destination.setBlockAndUpdate(position.below(2), engineBlock)
            }
            destination.removeBlock(position.east(), false)
            destination.setBlockAndUpdate(position.east(), CompuktersRegistry.DISPLAY.get().defaultBlockState())
        }
        guest.resume(sequence)
        guest.awaitMarker(sequence, "replacement-rejected")
        sequence.thenSucceed()
    }

    private fun program(propulsion: Boolean): String {
        val setup =
            if (propulsion) {
                """
            val engine = CreativeVectorThruster.named("warp-engine")
            engine.setVector(0.2, -0.1)
            engine.setThrustKn(5.0)
            engine.setThrottle(0.5)
            """
            } else {
                ""
            }
        val wait =
            if (propulsion) {
                """
            try {
                while (!Space.status().inDeepSpace) {
                    engine.state()
                    Tasks.sleepTicks(1)
                }
            } catch (failure: Exception) {
                if (!Space.status().inDeepSpace) throw failure
            }
            """
            } else {
                "readln()"
            }
        val cutoff =
            if (propulsion) {
                """
            check(engine.state().customThrust) { "Custom thrust was not restored" }
            check(engine.state().throttle == 0.5) { "Throttle was not restored" }
            check(kotlin.math.abs(engine.state().targetVectorX - 0.2) < 1e-5) { "Steering X was not restored" }
            check(kotlin.math.abs(engine.state().targetVectorY + 0.1) < 1e-5) { "Steering Y was not restored" }
            engine.setThrottle(0.0)
            check(engine.state().throttle == 0.0) { "Cutoff throttle was not applied" }
            // Match rocket-ascent's finally: releasing control clears the upstream shutdown envelope.
            engine.close()
            // The control handle is now closed; a fresh read-only handle observes its cleanup.
            val engineObserver = CreativeVectorThruster.named("warp-engine")
            check(engineObserver.state().currentThrustKn == 0.0) { "Physical thrust remains after control release" }
            println("engine-restored-cutoff")
            """
            } else {
                ""
            }
        val rejectReplacement =
            if (propulsion) {
                """
            var engineStale = false
            try { engineObserver.state() } catch (failure: IOException) { engineStale = true }
            check(engineStale) { "Replacement engine accepted the original handle" }
            """
            } else {
                ""
            }
        return """
            import compukter.display.GraphicalDisplay
            import compukter.io.IOException
            import compukter.concurrent.Tasks
            import cosmonautics.space.Space
            ${if (propulsion) "import propulsion.thrusters.CreativeVectorThruster" else ""}
            fun main(args: Array<String>) {
                val screen = GraphicalDisplay.named("warp-screen")
                screen.pixel(0, 0, 0x123456)
                $setup
                val surfaceMapping = Space.planetMapping()
                check(surfaceMapping.dimension == Space.status().dimension)
                check(surfaceMapping.radius > 0.0 && surfaceMapping.borderSize > 0.0)
                check(surfaceMapping.gravitationalParameter > 0.0 && surfaceMapping.influenceRadius > surfaceMapping.radius)
                check(Space.UNIVERSE_TICK_SECONDS == 0.05)
                var regionUnavailable = false
                try { Space.region() } catch (failure: IllegalStateException) { regionUnavailable = true }
                check(regionUnavailable) { "Surface accepted a Deep Space region" }
                println("before-warp")
                $wait
                check(Space.status().inDeepSpace)
                val region = Space.region()
                val orbit = Space.orbit()
                val mapping = Space.planetMapping()
                check(region.instanceId == orbit.instanceId && region.frame == orbit.frame)
                check(mapping.frame == orbit.frame && mapping.dimension == surfaceMapping.dimension)
                check(mapping.gravitationalParameter == orbit.gravitationalParameter)
                check(region.sideLength > 0 && region.loadedConstructions == 1 && region.unloadedConstructions == 0)
                check(region.constructionDiagonal > 0.0)
                check((region.constructionPosition - region.center).length < 1.0)
                check(region.minimum.y < region.center.y && region.center.y < region.maximum.y)
                check(mapping.rotation.orientation.w >= -1.0 && mapping.rotation.orientation.w <= 1.0)
                check(mapping.rotation.epochSeconds == surfaceMapping.rotation.epochSeconds)
                $cutoff
                screen.pixel(1, 0, 0x654321)
                println("after-warp")
                readln()
                var stale = false
                try { screen.pixel(2, 0, 1) } catch (failure: IOException) { stale = true }
                check(stale)
                $rejectReplacement
                println("replacement-rejected")
                readln()
            }
            """.trimIndent()
    }
}
