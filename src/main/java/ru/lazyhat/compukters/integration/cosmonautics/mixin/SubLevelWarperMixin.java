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

package ru.lazyhat.compukters.integration.cosmonautics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.egg.SubLevelWarper;
import dev.egg.registries.BlockEntityRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import ru.lazyhat.compukters.api.addon.minecraft.CompuktersPeripheralWorldIntegration;
import ru.lazyhat.compukters.integration.cosmonautics.CosmonauticsBlockTransfer;

import java.util.Collection;
import java.util.List;

/** Own the complete upstream save/load/remove transaction, rather than individual template copies. */
@Mixin(value = SubLevelWarper.class, remap = false)
public abstract class SubLevelWarperMixin {
    @Unique
    private static final ThreadLocal<CosmonauticsBlockTransfer> compukters$batch = new ThreadLocal<>();

    @WrapMethod(method = "WarpSubLevels")
    private static void compukters$transferBatch(
            Collection<SubLevel> levels, ServerSubLevelContainer source, ServerSubLevelContainer destination,
            Vector3d from, Vector3d to, Operation<Void> original) throws Exception {
        var previous = compukters$batch.get();
        try (var batch = new CosmonauticsBlockTransfer(List.copyOf(levels))) {
            compukters$batch.set(batch);
            for (var subLevel : levels) {
                CompuktersPeripheralWorldIntegration.prepareTransfer(source.getLevel(), CosmonauticsBlockTransfer.positions(subLevel));
            }
            original.call(levels, source, destination, from, to);
        } finally {
            if (previous == null) compukters$batch.remove();
            else compukters$batch.set(previous);
        }
    }

    @WrapOperation(method = "WarpSubLevels", at = @At(value = "INVOKE",
            target = "Ldev/egg/SubLevelTemplate;save(Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;)Lnet/minecraft/nbt/CompoundTag;"))
    private static CompoundTag compukters$prepareCopy(ServerLevelPlot plot, Operation<CompoundTag> original) {
        // Disassembly can add blocks after the initial batch preparation.
        CompuktersPeripheralWorldIntegration.prepareTransfer(plot.getSubLevel().getLevel(), CosmonauticsBlockTransfer.positions(plot.getSubLevel()));
        return original.call(plot);
    }

    @WrapOperation(method = "WarpSubLevels", at = @At(value = "INVOKE",
            target = "Ldev/egg/SubLevelTemplate;load(Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;Lnet/minecraft/nbt/CompoundTag;Ldev/egg/registries/BlockEntityRegistry$MoveInfo;)V"))
    private static void compukters$preserveMembers(
            ServerLevelPlot plot, CompoundTag tag, BlockEntityRegistry.MoveInfo move, Operation<Void> original) {
        var batch = compukters$batch.get();
        if (batch == null) throw new IllegalStateException("Cosmonautics transfer scope is missing");
        batch.begin(move);
        original.call(plot, tag, move);
    }
}
