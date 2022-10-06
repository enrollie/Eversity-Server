/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/4/22, 11:52 PM
 */

package by.enrollie.util

private const val EversityServerBanner = """
    ______                       _  __           _____                           
   / ____/_   _____  __________ (_)/ /___  __   / ___/___  _____ _   _____  _____
  / __/  | | / / _ \/ ___/ ___// // __/ / / /   \__ \/ _ \/ ___/| | / / _ \/ ___/
 / /___  | |/ /  __/ /  (__  )/ // /_/ /_/ /   ___/ /  __/ /    | |/ /  __/ /    
/_____/  |___/\___/_/  /____//_/ \__/\__, /   /____/\___/_/     |___/\___/_/     
                                    /____/                                       
"""

fun getBootstrapText(): String {
    val longestLineLength = EversityServerBanner.lines().maxOf { it.length }
    var resultText =
        ""//.padStart(((longestLineLength - BeforeBanner.length) / 2).takeIf { it >= 0 } ?: 0, ' ') + BeforeBanner.padEnd(longestLineLength, ' ')
    //resultText += '\n'
    resultText += "".padEnd(longestLineLength, '=')
    resultText += EversityServerBanner
    resultText += "".padStart(longestLineLength, '=')
    return resultText
}
