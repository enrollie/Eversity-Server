/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is not safe, refer to its documentation for more information."
)
@Retention(AnnotationRetention.BINARY)
annotation class UnsafeAPI
