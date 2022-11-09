/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/4/22, 11:52 PM
 */

package by.enrollie.util

import by.enrollie.privateProviders.EnvironmentInterface

private const val EversityServerBanner = """
    ______                       _  __           _____                           
   / ____/_   _____  __________ (_)/ /___  __   / ___/___  _____ _   _____  _____
  / __/  | | / / _ \/ ___/ ___// // __/ / / /   \__ \/ _ \/ ___/| | / / _ \/ ___/
 / /___  | |/ /  __/ /  (__  )/ // /_/ /_/ /   ___/ /  __/ /    | |/ /  __/ /    
/_____/  |___/\___/_/  /____//_/ \__/\__, /   /____/\___/_/     |___/\___/_/     
                                    /____/                                       
"""

fun getBootstrapText(environmentInterface: EnvironmentInterface): String {
    val longestLineLength = EversityServerBanner.lines().maxOf { it.length }
    var resultText = ""
    run {
        val env = environmentInterface.environmentType.shortName
        val version = environmentInterface.serverVersion
        val padLength = (longestLineLength - 2 - env.length - version.length).takeIf { it >= 0 } ?: 0
        resultText += " $env ${" ".repeat(padLength)} $version\n"
    }
    resultText += "".padStart(longestLineLength, '=')
    resultText += EversityServerBanner
    resultText += "Running as ${environmentInterface.serverName}".let { msg ->
        val padLength = ((longestLineLength - msg.length) / 2).takeIf { it >= 0 } ?: 0
        " ".repeat(padLength) + msg + " ".repeat(padLength)
    }
    resultText += "\n"
    return resultText
}
