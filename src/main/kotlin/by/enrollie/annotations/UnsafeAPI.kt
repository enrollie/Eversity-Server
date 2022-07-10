/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is not type safe, use it only if you know what you are doing"
)
@Retention(AnnotationRetention.BINARY)
annotation class UnsafeAPI
