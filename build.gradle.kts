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

import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("org.jmailen.kotlinter")
}

group = "ru.lazyhat.compukters"
val compuktersSdk = Properties().apply {
    File(gradle.extra["compuktersSdkDirectory"].toString()).resolve("sdk.properties").inputStream().use(::load)
}
val addonSdkVersion = compuktersSdk.getProperty("sdkVersion")
base { archivesName.set("compukters-cosmonautics-1.21.1-neoforge") }
apply(from = "gradle/addon-versioning.gradle.kts")

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
architectury {
    minecraft = "1.21.1"
    platformSetupLoomIde()
    neoForge()
}
repositories {
    mavenCentral()
    maven("https://maven.architectury.dev/")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.parchmentmc.org/")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/snapshots")
    maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven")
}
apply(from = "gradle/sable-libraries.gradle.kts")
// Aeronautics bundles Simulated and Offroad; expose their nested mods to Loom dev runs.
apply(from = "gradle/aeronautics-libraries.gradle.kts")
val aeronauticsLibraries = files(configurations.named("aeronauticsNestedMods"))
val sableCompanion = files(configurations.named("sableCompanion"))
val sableLibraries = files(configurations.named("sableNestedMods"))
val veilLibraries = files(configurations.named("sableRuntimeLibraries"))

// Resolve the selected SDK without producing a Guest bundle before a real Guest API exists.
repositories {
    exclusiveContent {
        forRepository { maven { url = uri(File(gradle.extra["compuktersSdkDirectory"].toString()).resolve("repository")) } }
        filter { includeGroup("ru.lazyhat.compukters") }
    }
}

val developmentMod = if (providers.gradleProperty("compuktersDependencyMode").get() == "workspace") {
    files(rootProject.file(providers.gradleProperty("compuktersWorkspace").get()).resolve(
        "modules/minecraft/v1_21_1/v1_21_1-neoforge/build/devlibs/compukters-development-mod.jar",
    )).builtBy(gradle.includedBuild("Compukters").task(":v1_21_1-neoforge:developmentModJar"))
} else {
    "ru.lazyhat.compukters:compukters-neoforge-1.21.1-dev:${compuktersSdk.getProperty("modVersion")}"
}

dependencies {
    minecraft("net.minecraft:minecraft:1.21.1")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-1.21.1:2024.11.17@zip")
    })
    neoForge("net.neoforged:neoforge:21.1.252")
    modImplementation("maven.modrinth:sable:fg9dTRz9")
    modImplementation("maven.modrinth:create-cosmonatics:HuCMavv8") { isTransitive = false }
    modImplementation("com.simibubi.create:create-1.21.1:6.0.10-280:slim") { isTransitive = false }
    modImplementation("net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1")
    modCompileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6")
    modRuntimeOnly("dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6")
    modImplementation("com.tterrag.registrate:Registrate:MC1.21-1.3.0+67")
    compileOnly(sableCompanion)
    // Loom dev runs do not discover Sable's Jar-in-Jar runtime dependencies automatically.
    modRuntimeOnly(sableLibraries)
    modRuntimeOnly("maven.modrinth:create-aeronautics:44pLdPGg") { isTransitive = false }
    modRuntimeOnly(aeronauticsLibraries)
    forgeRuntimeLibrary(veilLibraries)
    compileOnly("ru.lazyhat.compukters:compukters-addon-api:$addonSdkVersion")
    compileOnly("ru.lazyhat.compukters:compukters-addon-neoforge-1.21.1:$addonSdkVersion")
    runtimeOnly(developmentMod)
    listOf(
        "org.jetbrains.kotlin:kotlin-stdlib:2.4.10",
        "io.github.oshai:kotlin-logging-jvm:8.0.4",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
        "org.tukaani:xz:1.12",
    ).forEach { forgeRuntimeLibrary(it) { isTransitive = false } }
}
loom {
    mods { maybeCreate("compukters_cosmonautics").sourceSet("main") }
    runs {
        named("client") { runDir("run/client"); ideConfigGenerated(true) }
        named("server") { runDir("run/server"); ideConfigGenerated(true) }
    }
}
tasks.withType<ConfigurableKtLintTask>().configureEach { exclude { it.file.path.contains("build/generated") } }
tasks.jar { archiveClassifier.set("dev") }

// Stable development archive consumed by the independent all-addon run build.
val developmentModJar = tasks.register<Jar>("developmentModJar") {
    manifest.attributes["Fabric-Loom-Remap"] = "false"
    group = "build"
    archiveFileName.set("compukters-cosmonautics-development-mod.jar")
    destinationDirectory.set(layout.buildDirectory.dir("devlibs"))
    from(sourceSets.main.get().output)
}

val productionJar = tasks.named<RemapJarTask>("remapJar") {
    inputFile.set(tasks.jar.flatMap { it.archiveFile })
    archiveClassifier.set("")
}
val verifyProductionJar = tasks.register("verifyProductionJar") {
    group = "verification"
    dependsOn(productionJar)
    inputs.file(productionJar.flatMap { it.archiveFile })
    val checkedArchive = productionJar.flatMap { it.archiveFile }
    doLast {
        val archive = checkedArchive.get().asFile
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()
            listOf(
                "META-INF/neoforge.mods.toml",
                "ru/lazyhat/compukters/integration/cosmonautics/CompuktersCosmonauticsMod.class",
            ).forEach { required ->
                check(entries.count { it == required } == 1) { "$required is missing or duplicated in ${archive.name}" }
            }
            listOf(
                "dev/devce/", "dev/egg/", "dev/ryanhcode/", "com/simibubi/create/",
                "ru/lazyhat/compukters/api/", "ru/lazyhat/compukters/core/", "ru/lazyhat/compukters/impl/",
                "ru/lazyhat/compukters/integration/create/", "ru/lazyhat/compukters/integration/sable/",
                "ru/lazyhat/compukters/integration/propulsion/", "META-INF/compukters/addons/",
            ).forEach { forbidden ->
                check(entries.none { it.startsWith(forbidden) }) { "$forbidden implementation leaked into ${archive.name}" }
            }
            check(entries.none { it.contains("GameTest") || it.contains("GuestComputerScenario") }) {
                "GameTest implementation leaked into ${archive.name}"
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyProductionJar) }
tasks.named("assemble") { dependsOn(productionJar) }
tasks.register("buildProductionJar") {
    group = "build"
    dependsOn(productionJar, verifyProductionJar)
}
