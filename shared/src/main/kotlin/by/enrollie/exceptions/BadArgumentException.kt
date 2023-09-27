/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 11/17/22, 11:57 PM
 */

package by.enrollie.exceptions

/**
 * Argument is syntactically correct, but it makes no logical sense.
 */
class BadArgumentException(message: String, val humanMessage: String?) : Exception(message)
