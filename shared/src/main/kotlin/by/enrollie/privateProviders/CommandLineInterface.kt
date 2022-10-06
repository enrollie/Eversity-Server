/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 9/14/22, 8:56 PM
 */

package by.enrollie.privateProviders

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.coroutines.flow.SharedFlow

interface CommandLineInterface {
    /**
     * Flow of all messages that come **into** the console.
     * It is preferable to use [registerCommand] to process commands instead of manually listening to this flow.
     * @see registerCommand for registering commands.
     */
    val inputBroadcast: SharedFlow<String>

    /**
     * Broadcast of all messages that are sent to the output.
     */
    val outputBroadcast: SharedFlow<String>

    /**
     * Sends a message to the [outputBroadcast] flow (which, in turn, is sent to the stdout).
     */
    fun writeMessage(string: String)

    /**
     * Writes a message into the [inputBroadcast] flow (which, in turn, triggers command processing).
     */
    fun injectInput(string: String)

    /**
     * Registers a command in the CLI.
     */
    fun registerCommand(command: LiteralArgumentBuilder<Unit>)
}
