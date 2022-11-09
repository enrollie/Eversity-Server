/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 2:55 AM
 */

package by.enrollie.privateProviders

@Deprecated(
    "Use EnvironmentInterface instead",
    ReplaceWith("EnvironmentInterface", "by.enrollie.privateProviders.EnvironmentInterface"),
    DeprecationLevel.WARNING
)
interface ApplicationMetadata {
    val title: String
    val version: String
    val buildTimestamp: Long
}
