/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/11/22, 12:00 AM
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "by.enrollie"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("joda-time:joda-time:2.10.14")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.10")
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
            artifactId = "eversity_api"
            version = rootProject.version.toString()

            from(components["java"])
        }
    }
}
