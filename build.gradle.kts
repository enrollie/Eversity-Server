/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/14/22, 1:39 AM
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val schoolsByParserVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.7.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.10"
    id("eu.davidea.grabver") version "2.0.2"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "by.enrollie"
versioning {
    major = 0
    minor = 9
    if (System.getenv()["buildType"] == "release") {
        incrementOn = "shadowJar"
    }
    saveOn = "shadowJar"
}
application {
    mainClass.set("by.enrollie.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    maven {
        url = uri("https://packages.neitex.me/releases")
    }

    maven("https://libraries.minecraft.net")
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
    implementation("joda-time:joda-time:2.12.1")
    implementation("com.osohq:oso:0.26.4")
    implementation("io.micrometer:micrometer-registry-datadog:1.10.0")
    implementation("io.micrometer:micrometer-registry-jmx:1.10.0")
    implementation("io.sentry:sentry:6.7.0")
    implementation("io.sentry:sentry-kotlin-extensions:6.7.0")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.kodein.di:kodein-di:7.15.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    implementation("com.neitex:schools_parser:$schoolsByParserVersion")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.3")

    implementation("fr.opensagres.xdocreport:fr.opensagres.xdocreport.document.docx:2.0.4")
    implementation("fr.opensagres.xdocreport:fr.opensagres.xdocreport.converter.docx.xwpf:2.0.4")
    implementation("fr.opensagres.xdocreport:fr.opensagres.xdocreport.template.velocity:2.0.4")

    implementation("io.github.z4kn4fein:semver:1.3.3")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("org.jline:jline:3.21.0")
    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.jline:jline-terminal-jansi:3.21.0")
    implementation("com.mojang:brigadier:1.0.18")


    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.dependsOn.add((tasks.getByName("processResources") as ProcessResources).apply {
    filesMatching("selfInfo.properties") {
        val props = mutableMapOf<String, String>()
        props["version"] = versioning.name
        props["schoolsByParserVersion"] = schoolsByParserVersion
        props["buildTimestamp"] = (System.currentTimeMillis() / 1000).toString()
        props["buildID"] = versioning.build.toString()
        props["apiVersion"] = project.childProjects.toList().first().second.version.toString()
        expand(props)
    }
})


tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "16"
    }
}
val compileJava: JavaCompile by tasks

tasks.processResources {
    dependsOn("cleanResources")
}
version = versioning.name

tasks.register("cleanResources") {
    delete("$buildDir/resources")
    didWork = true
}
val shadowJar: ShadowJar by tasks
shadowJar.archiveVersion.set(versioning.name)
shadowJar.archiveAppendix.set("")
if (System.getenv()["buildType"] == "release") {
    shadowJar.archiveClassifier.set("release")
} else shadowJar.archiveClassifier.set("")
