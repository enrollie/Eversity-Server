/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/27/22, 8:47 PM
 */

package by.enrollie.privateProviders

interface EnvironmentInterface {
    enum class EnvironmentType(val shortName: String, val fullName: String) {
        DEVELOPMENT("DEV", "DEVELOPMENT"), PRODUCTION("PROD", "PRODUCTION"), TESTING("TEST", "TESTING")
    }

    /**
     * Current environment type
     */
    val environmentType: EnvironmentType

    /**
     * Name of the system on which the application is running
     */
    val systemName: String

    /**
     * Human-readable name of the server instance, so it can be distinguished from other instances on the same system (i.e. "patient-snowflake-1374"). Set by the environment variable "EVERSITY_SERVER_NAME".
     */
    val serverName: String

    /**
     * Timestamp of the server start
     */
    val startTime: Long

    /**
     * Server version
     */
    val serverVersion: String

    /**
     * Server API version
     */
    val serverApiVersion: String

    /**
     * Server build timestamp
     */
    val serverBuildTimestamp: Long

    /**
     * Server build number
     */
    val serverBuildID: String

    /**
     * Server environment variables
     */
    val environmentVariables: Map<String, String>
}
