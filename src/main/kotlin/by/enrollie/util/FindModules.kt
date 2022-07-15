/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:38 AM
 */

package by.enrollie.util

import java.util.*

inline fun <reified T> getServices(layer: ModuleLayer): List<T> = ServiceLoader.load(layer, T::class.java).toList()
