/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 2:59 AM
 */

package by.enrollie.providers

import kotlinx.coroutines.flow.SharedFlow

interface CommandLineInterface {
    val inputBroadcast: SharedFlow<String>
    val outputBroadcast: SharedFlow<String>

    fun writeMessage(string: String)
    fun injectInput(string: String)
}
