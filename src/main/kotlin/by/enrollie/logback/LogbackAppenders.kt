/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/25/22, 2:58 PM
 */

package by.enrollie.logback

import by.enrollie.impl.CommandLine
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import com.github.ajalt.mordant.rendering.TextColors
import org.joda.time.DateTime


class IntelliJLikeLayoutWithoutColors : PatternLayout() {
    override fun doLayout(event: ILoggingEvent?): String? {
        event ?: return null
        val timestamp = DateTime.now().toString("YYYY-MM-dd HH:mm:ss,SSS")
        return "$timestamp [${event.threadName}]   ${event.level.levelStr} - ${event.loggerName} - ${event.formattedMessage}${
            if (event.mdcPropertyMap.isNotEmpty()) " ${
                event.mdcPropertyMap.toList().joinToString(separator = ", ", prefix = "(", postfix = ")"){
                    "${it.first}: `${it.second}`"
                }
            }" else ""
        }\n".let {
            (if (event.throwableProxy != null) {
                it + "\t" + ThrowableProxyUtil.asString(event.throwableProxy)
            } else it)
        }
    }
}

class IntelliJLikeLayout : PatternLayout() {
    override fun doLayout(event: ILoggingEvent?): String? {
        event ?: return null
        val timestamp = DateTime.now().toString("YYYY-MM-dd hh:mm:ss,SSS")
        val levelColor = when (event.level.levelInt) {
            Level.OFF_INT -> TextColors.gray(event.level.levelStr)
            Level.ERROR_INT -> TextColors.red(event.level.levelStr)
            Level.WARN_INT -> TextColors.yellow(event.level.levelStr)
            Level.INFO_INT -> TextColors.brightBlue(event.level.levelStr)
            Level.DEBUG_INT -> TextColors.white(event.level.levelStr)
            Level.TRACE_INT -> TextColors.gray(event.level.levelStr)
            Level.ALL_INT -> event.level.levelStr
            else -> error("Unknown event level int ${event.level.levelInt}")
        }
        return "${TextColors.green(timestamp)} [${event.threadName}]   $levelColor - ${event.loggerName} - ${event.formattedMessage}${
            if (event.mdcPropertyMap.isNotEmpty()) " ${
                event.mdcPropertyMap.toList().joinToString(separator = ", ", prefix = "(", postfix = ")"){
                    "${it.first}: `${it.second}`"
                }
            }" else ""
        }".let {
            (if (event.throwableProxy != null) {
                it + "\n\t" + ThrowableProxyUtil.asString(event.throwableProxy).trimIndent()
            } else it)
        }
    }
}

class CustomAppender : AppenderBase<ILoggingEvent>() {
    private val layout = IntelliJLikeLayout()
    override fun append(eventObject: ILoggingEvent) {
        layout.doLayout(eventObject)?.let {
            CommandLine.instance.writeMessage(it)
        }
    }

    override fun start() {
        super.start()
        layout.start()
    }

    override fun stop() {
        super.stop()
        layout.stop()
    }
}
