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
import net.minecraft.gametest.framework.GameTestRegistry
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.nio.file.Files
import java.nio.file.Path

/** Dedicated test run retains the real dimension registry, unlike vanilla GameTestServer. */
@EventBusSubscriber(modid = "compukters_cosmonautics")
object TransferTestServer {
    private var deadline = Long.MAX_VALUE

    @JvmStatic
    @SubscribeEvent
    fun start(event: ServerStartedEvent) {
        if (!java.lang.Boolean.getBoolean("compukters.cosmonautics.transferTest")) return
        deadline = System.nanoTime() + 600_000_000_000L
        event.server.execute {
            if (GameTestRegistry.findTestFunction("retainedhandlecrosseswarpedplot").isEmpty) {
                finish(event.server, false)
                return@execute
            }
            event.server.commands.performPrefixedCommand(
                event.server.createCommandSourceStack(),
                "test run retainedhandlecrosseswarpedplot",
            )
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun timeout(event: ServerTickEvent.Post) {
        if (System.nanoTime() > deadline) finish(event.server, false)
    }

    @JvmStatic
    @SubscribeEvent
    fun stopped(event: ServerStoppedEvent) {
        if (!java.lang.Boolean.getBoolean("compukters.cosmonautics.transferTest")) return
        val serverThread = Thread.currentThread()
        // Upstream mod initialization pools can outlive the server. Exit only after its owner has finished,
        // so Minecraft's shutdown hook cannot deadlock trying to join the thread invoking System.exit.
        Thread({
            serverThread.join()
            val result = Path.of("transfer-test-result.txt")
            val passed = Files.isRegularFile(result) && Files.readString(result).trim() == "PASS"
            kotlin.system.exitProcess(if (passed) 0 else 1)
        }, "Cosmonautics transfer test exit").apply {
            isDaemon = true
            start()
        }
    }

    fun finish(
        server: MinecraftServer,
        passed: Boolean,
    ) {
        if (!java.lang.Boolean.getBoolean("compukters.cosmonautics.transferTest")) return
        deadline = Long.MAX_VALUE
        Files.writeString(Path.of("transfer-test-result.txt"), if (passed) "PASS\n" else "FAIL\n")
        LogUtils.getLogger().info("Cosmonautics transfer GameTest result: {}", if (passed) "PASS" else "FAIL")
        server.halt(false)
    }
}
