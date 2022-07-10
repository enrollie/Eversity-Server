/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
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
    id("eu.davidea.grabver") version "2.0.2"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "by.enrollie"
version = versioning.name
application {
    mainClass.set("by.enrollie.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    maven { url = uri("https://packages.neitex.me") }
}

dependencies {
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
    implementation("io.sentry:sentry:6.1.4")
    implementation("io.sentry:sentry-kotlin-extensions:6.1.4")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.kodein.di:kodein-di:7.12.0")

    implementation("com.neitex:schools_parser:$schoolsByParserVersion")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

versioning {
    major = 1
    minor = 0
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.dependsOn.add((tasks.getByName("processResources") as ProcessResources).apply {
    filesMatching("selfInfo.properties") {
        val props = mutableMapOf<String, String>()
        props["version"] = versioning.fullName
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
    this.didWork = true
}
tasks.register("incrementVer") {

}
