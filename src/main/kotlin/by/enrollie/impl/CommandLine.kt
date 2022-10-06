/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:38 AM
 */

package by.enrollie.impl

import by.enrollie.privateProviders.CommandLineInterface
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jline.reader.Candidate
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import org.slf4j.LoggerFactory

class CommandLine private constructor() : CommandLineInterface {
    private val terminal = TerminalBuilder.builder().color(true).build()
    private val reader = LineReaderBuilder.builder().terminal(terminal).completer { _, line, candidates ->
        candidates.addAll(dispatcher.getCompletionSuggestions(dispatcher.parse(line.line(), Unit)).get().list.map {
            Candidate(it.text)
        })
    }.build()
    private val inputFlow = MutableSharedFlow<String>()
    private val outputFlow = MutableSharedFlow<String>(10)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val dispatcher: CommandDispatcher<Unit> = CommandDispatcher()
    override val inputBroadcast: SharedFlow<String>
        get() = inputFlow
    override val outputBroadcast: SharedFlow<String>
        get() = outputFlow
    private val logger = LoggerFactory.getLogger(this::class.java)

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

    override fun registerCommand(command: LiteralArgumentBuilder<Unit>) {
        dispatcher.register(command)
    }

    init {
        dispatcher.register(LiteralArgumentBuilder.literal<Unit>("help").executes {
            logger.info("Available commands: ${dispatcher.root.children.map { it.name }}")
            1
        })
        coroutineScope.launch {
            while (true) {
                try {
                    val line = reader.readLine()
                    if (line != null) {
                        inputFlow.emit(line.trim())
                    }
                } catch (e: EndOfFileException) {
                    break
                } catch (_: UserInterruptException) {
                    logger.info("Received CTRL+C signal")
                    Runtime.getRuntime().exit(0)
                }
            }
        }
        coroutineScope.launch {
            inputFlow.collect {
                logger.debug("Received command: '$it'")
                dispatcher.parse(it, Unit).let { command ->
                    try {
                        dispatcher.execute(command)
                    } catch (e: CommandSyntaxException) {
                        logger.error("Error while executing command: ${e.message}")
                    } catch (e: RuntimeException) {
                        logger.error("Error while executing command: ${e.message}")
                    }
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
