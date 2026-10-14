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

package ru.lazyhat.compukters.integration.cosmonautics;

import dev.egg.registries.BlockEntityRegistry;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralWorldIntegration;

import java.util.LinkedHashMap;
import java.util.List;

/** Public because Mixin moves the caller into the upstream warper class. */
public final class CosmonauticsBlockTransfer implements AutoCloseable {
    private final List<SubLevel> sources;
    private AutoCloseable transfer;

    public CosmonauticsBlockTransfer(List<SubLevel> sources) {
        this.sources = sources;
    }

    public static List<BlockPos> positions(SubLevel level) {
        return level.getPlot().getLoadedChunks().stream()
                .flatMap(holder -> holder.getChunk().getBlockEntitiesPos().stream())
                .map(BlockPos::immutable).toList();
    }

    public void begin(BlockEntityRegistry.MoveInfo move) {
        if (transfer != null) return;
        var mapping = new LinkedHashMap<BlockPos, BlockPos>();
        for (var source : sources) {
            var destination = move.oldToNewSubLevelMap().get(source.getUniqueId());
            if (destination == null) throw new IllegalStateException("Cosmonautics omitted a source plot translation");
            for (var position : positions(source)) {
                mapping.put(position, position.offset(destination.second()).immutable());
            }
        }
        transfer = CompuktersPeripheralWorldIntegration.beginTransfer(move.oldDimension(), move.newDimension(), mapping);
    }

    @Override
    public void close() throws Exception {
        if (transfer != null) transfer.close();
    }
}
