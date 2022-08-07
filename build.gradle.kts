/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val schoolsByParserVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.0"
    id("io.wusa.semver-git-plugin") version "2.3.7"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "by.enrollie"
//version = semver.info
application {
    mainClass.set("by.enrollie.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    maven { url = uri("https://packages.neitex.me/releases") }
}

dependencies {
    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta2")
    implementation("joda-time:joda-time:2.10.14")
    implementation("com.osohq:oso:0.26.1")
    implementation("com.newrelic.telemetry:micrometer-registry-new-relic:0.9.0")
    implementation("io.micrometer:micrometer-registry-jmx:1.9.2")
    implementation("io.sentry:sentry:6.3.1")
    implementation("io.sentry:sentry-kotlin-extensions:6.3.1")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.kodein.di:kodein-di:7.14.0")

    implementation("com.neitex:schools_parser:$schoolsByParserVersion")

    implementation("org.jline:jline:3.21.0")
    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.jline:jline-terminal-jansi:3.21.0")


    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.dependsOn.add((tasks.getByName("processResources") as ProcessResources).apply {
    filesMatching("selfInfo.properties") {
        val props = mutableMapOf<String, String>()
        props["version"] = semver.info.toString()
        props["schoolsByParserVersion"] = schoolsByParserVersion
        props["buildTimestamp"] = (System.currentTimeMillis() / 1000).toString()
        expand(props)
    }
})

tasks.processResources {
    dependsOn("cleanResources")
}
tasks.register("cleanResources") {
    delete("$buildDir/resources")
    didWork = true
}
tasks.register("getVersion") {
    println("Current version: ${semver.info}")
    didWork = true
}

semver {
    snapshotSuffix = "SNAPSHOT"
    dirtyMarker = "dirty"
    initialVersion = "0.1.0"
    tagType = io.wusa.TagType.LIGHTWEIGHT
    project.version = semver.info
    branches { // list of branch configurations
        branch {
            regex = ".+"
            incrementer = "CONVENTIONAL_COMMITS_INCREMENTER"
            formatter = Transformer {
                "${it.version.major}.${it.version.minor}.${it.version.patch}+build.${it.count}"
            }
        }
    }
}
