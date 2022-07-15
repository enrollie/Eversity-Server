/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:38 AM
 */

package by.enrollie.impl

import by.enrollie.providers.CommandLineInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder

class CommandLine private constructor() : CommandLineInterface {
    private val terminal = TerminalBuilder.builder().color(true).build()
    private val reader = LineReaderBuilder.builder().terminal(terminal).build()
    private val inputFlow = MutableSharedFlow<String>()
    private val outputFlow = MutableSharedFlow<String>(10)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    override val inputBroadcast: SharedFlow<String>
        get() = inputFlow
    override val outputBroadcast: SharedFlow<String>
        get() = outputFlow

    override fun writeMessage(string: String) {
        reader.printAbove(string)
        coroutineScope.launch {
            outputFlow.emit(string)
        }
    }

    override fun injectInput(string: String) {
        coroutineScope.launch {
            inputFlow.emit(string)
        }
    }

    init {
        coroutineScope.launch {
            while (true) {
                try {
                    val line = reader.readLine()
                    if (line != null) {
                        inputFlow.emit(line)
                    }
                } catch (e: EndOfFileException) {
                    break
                } catch (_: UserInterruptException) {
                }
            }
        }
    }

    companion object {
        val instance: CommandLine by lazy {
            CommandLine()
        }
    }
}
