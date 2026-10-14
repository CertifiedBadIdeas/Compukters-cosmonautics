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

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayerFactory
import ru.lazyhat.compukters.impl.registry.CompuktersRegistry
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralBindingResult
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorItem
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralConfiguratorServer
import ru.lazyhat.compukters.minecraft.peripheral.PeripheralWorldPositions

/** Real configurator actions at absolute world/plot positions, with persisted exact-device identity. */
internal object TransferNetworkFixture {
    fun bind(
        helper: GameTestHelper,
        vararg positions: BlockPos,
    ) {
        val player = player(helper, positions.first())
        val stack = player.mainHandItem
        val item = stack.item as PeripheralConfiguratorItem
        item.use(helper.level, player, InteractionHand.MAIN_HAND)
        for (position in positions) {
            player.setPos(requireNotNull(PeripheralWorldPositions.project(helper.level, Vec3.atCenterOf(position))))
            val result = PeripheralConfiguratorServer.bind(player, InteractionHand.MAIN_HAND, position, Direction.WEST)
            helper.assertTrue(
                result in listOf(PeripheralBindingResult.CREATED, PeripheralBindingResult.SELECTED, PeripheralBindingResult.BOUND),
                "Could not bind $position: $result",
            )
        }
        helper.assertTrue(item.selectedNetwork(stack) != null, "Configurator did not select a network")
    }

    private fun player(
        helper: GameTestHelper,
        position: BlockPos,
    ) = FakePlayerFactory.getMinecraft(helper.level).also {
        it.setShiftKeyDown(false)
        it.setPos(requireNotNull(PeripheralWorldPositions.project(helper.level, Vec3.atCenterOf(position))))
        it.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(CompuktersRegistry.PERIPHERAL_CONFIGURATOR_ITEM.get()))
    }
}
