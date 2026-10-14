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

import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestSequence
import ru.lazyhat.compukters.core.device.computer.ProgramComputerState
import ru.lazyhat.compukters.lang.runtime.vm.TerminalKey
import ru.lazyhat.compukters.lang.runtime.vm.TerminalKeyAction
import ru.lazyhat.compukters.lang.runtime.vm.TerminalModifier
import ru.lazyhat.compukters.lang.runtime.vm.TerminalState
import ru.lazyhat.compukters.minecraft.computer.ComputerBlockEntity
import java.util.concurrent.CompletableFuture

/** Uses the public computer input boundary, actual Guest editor, compiler worker and addon-enabled VM. */
internal class TransferGuestComputerScenario(
    private val helper: GameTestHelper,
    private val position: () -> BlockPos,
    private val level: () -> net.minecraft.server.level.ServerLevel,
    private val propulsion: Boolean = false,
) {
    private val computer: ComputerBlockEntity get() = level().getBlockEntity(position()) as ComputerBlockEntity
    private var operation: CompletableFuture<*>? = null
    private var snapshot: CompletableFuture<TerminalState?>? = null
    private var lastObservation = "no terminal snapshot completed"

    fun prepare(
        sequence: GameTestSequence,
        source: String,
    ) {
        sequence.thenExecute { computer.prepareTerminalAsync() }
        awaitText(sequence, ">\n")
        val addons = if (propulsion) "\"cosmonautics\", \"propulsion\"" else "\"cosmonautics\""
        writeFile(sequence, "compukter.toml", "format = 3\nname = \"transfer-gametest\"\naddons = [$addons]\n")
        // The real editor repeats indentation on newline; these fixtures contain no significant leading whitespace.
        writeFile(sequence, "main.kt", source.lines().joinToString("\n") { it.trimStart() })
        sequence.thenExecute { operation = command("kotlinc main.kt -o scenario") }
        awaitOperation(sequence)
        awaitText(sequence, "compiled: /home/scenario")
    }

    fun run(
        sequence: GameTestSequence,
        phase: String,
    ) {
        sequence.thenExecute { computer.prepareTerminalAsync() }
        awaitText(sequence, ">\n")
        sequence.thenExecute { operation = command("scenario $phase") }
        awaitOperation(sequence)
    }

    fun awaitMarker(
        sequence: GameTestSequence,
        marker: String,
        waitingForInput: Boolean = true,
        uniqueMarker: String? = null,
    ) = awaitText(sequence, marker + "\n", waitingForInput, uniqueMarker)

    fun resume(sequence: GameTestSequence) {
        sequence.thenExecute { operation = computer.submitCanonicalLineAsync("continue".toCharArray()) }
        awaitOperation(sequence)
    }

    private fun writeFile(
        sequence: GameTestSequence,
        path: String,
        text: String,
    ) {
        sequence.thenExecute { operation = command("edit $path") }
        awaitOperation(sequence)
        awaitText(sequence, "Compukters edit")
        sequence.thenExecute { operation = computer.submitTerminalTextAsync(text) }
        awaitOperation(sequence)
        sequence.thenExecute {
            operation =
                computer.submitTerminalKeyAsync(
                    TerminalKey.S,
                    TerminalKeyAction.PRESS,
                    setOf(TerminalModifier.CONTROL),
                )
        }
        awaitOperation(sequence)
        sequence.thenExecute {
            operation =
                computer.submitTerminalKeyAsync(
                    TerminalKey.X,
                    TerminalKeyAction.PRESS,
                    setOf(TerminalModifier.CONTROL),
                )
        }
        awaitOperation(sequence)
        awaitText(sequence, ">\n")
    }

    private fun command(value: String): CompletableFuture<Boolean> =
        computer.submitTerminalTextAsync(value).thenCompose { accepted ->
            check(accepted) { "computer rejected command text: $value" }
            computer.submitTerminalKeyAsync(TerminalKey.ENTER, TerminalKeyAction.PRESS)
        }

    private fun awaitOperation(sequence: GameTestSequence) {
        sequence.thenWaitUntil {
            helper.assertTrue(operation?.isDone == true, "computer input operation is pending")
            val result = operation!!.join()
            helper.assertTrue(result != false, "computer rejected the input operation")
        }
    }

    private fun awaitText(
        sequence: GameTestSequence,
        expected: String,
        waitingForInput: Boolean = true,
        uniqueMarker: String? = null,
    ) {
        var deadline = Long.MAX_VALUE
        sequence.thenExecute {
            deadline = System.nanoTime() + (if (expected == "after-warp\n") 30_000_000_000L else 120_000_000_000L)
            LogUtils.getLogger().info("Cosmonautics GameTest awaiting terminal checkpoint: {}", expected.trim())
        }
        sequence.thenWaitUntil {
            if (System.nanoTime() > deadline) {
                val failure = IllegalStateException("expected $expected; $lastObservation")
                LogUtils.getLogger().error("Cosmonautics Guest checkpoint failed", failure)
                helper.testInfo.fail(failure)
                helper.assertTrue(false, requireNotNull(failure.message))
            }
            if (snapshot == null) snapshot = computer.terminalFullStateAsync()
            helper.assertTrue(snapshot!!.isDone, "terminal snapshot is pending; expected $expected; $lastObservation")
            val state = snapshot!!.join()
            snapshot = null
            val text = terminalText(state)
            lastObservation = "state=${computer.runtimeState}; terminal=$text"
            if (text.contains("Uncaught exception:")) {
                val failure = IllegalStateException("expected $expected; $lastObservation")
                LogUtils.getLogger().error("Cosmonautics Guest checkpoint failed", failure)
                helper.testInfo.fail(failure)
                helper.assertTrue(false, requireNotNull(failure.message))
            }
            helper.assertTrue(
                (!waitingForInput || computer.runtimeState == ProgramComputerState.WaitingForInput) && text.contains(expected),
                "expected $expected; state=${computer.runtimeState}; terminal=$text",
            )
            if (uniqueMarker != null) {
                helper.assertTrue(text.split(uniqueMarker + "\n").size == 2, "program entry ran more than once: $text")
            }
        }
    }

    private fun terminalText(state: TerminalState?): String {
        if (state == null) return "<no terminal>"
        return (0 until state.height).joinToString("\n", postfix = "\n") { y ->
            buildString {
                repeat(state.width) { x -> appendCodePoint(state.cells[y * state.width + x].codePoint) }
            }.trimEnd()
        }
    }
}
