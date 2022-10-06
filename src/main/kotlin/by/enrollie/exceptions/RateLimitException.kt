/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 9/11/22, 12:48 AM
 */

package by.enrollie.exceptions

// Originally intended to be named "EnhanceYourCalmException", but that's not descriptive enough :)
class RateLimitException(message: String) : Exception(message)
