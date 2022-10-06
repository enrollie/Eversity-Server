/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/11/22, 7:32 PM
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "by.enrollie"
val libVersion = "0.1.3-alpha.3"
version = libVersion

repositories {
    mavenCentral()
    maven { url = uri("https://libraries.minecraft.net") }
}

dependencies {
    api(kotlin("stdlib"))
    api("joda-time:joda-time:2.11.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
    api("org.jetbrains.kotlin:kotlin-reflect:1.7.10")
    api("io.ktor:ktor-server-core:2.1.1")
    api("com.mojang:brigadier:1.0.18")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                name.set("Eversity Server API")
                description.set("API used to create plugins for Eversity")
                url.set("https://github.com/enrollie/Eversity-Server")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("pavelm")
                        name.set("Pavel Matusevich")
                        email.set("neitex@protonmail.com")
                        roles.add("Main developer")
                        timezone.set("GMT+3")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/enrollie/Eversity-Server.git")
                    url.set("https://github.com/enrollie/Eversity-Server.git")
                }
            }
            repositories {
                maven {
                    url = uri("https://packages.neitex.me/releases")
                    credentials.username = System.getenv("reposiliteAlias")
                    credentials.password = System.getenv("reposilitePassword")

                    authentication {
                        create<BasicAuthentication>("basic")
                    }
                }
            }
            groupId = "by.enrollie"
            artifactId = "eversity-shared-api"
            version = libVersion

            from(components["java"])
        }
    }
}
