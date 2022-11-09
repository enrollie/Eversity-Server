/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/27/22, 11:33 PM
 */

package by.enrollie.util

import java.net.NetworkInterface
import kotlin.random.Random

fun generateServerName(): String {
    val adjectives = listOf(
        "aged", "ancient", "autumn", "billowing", "bitter", "black", "blue", "bold",
        "broad", "broken", "calm", "cold", "cool", "crimson", "curly", "damp",
        "dark", "dawn", "delicate", "divine", "dry", "empty", "falling", "fancy",
        "flat", "floral", "fragrant", "frosty", "gentle", "green", "hidden", "holy",
        "icy", "jolly", "late", "lingering", "little", "lively", "long", "lucky",
        "misty", "morning", "muddy", "mute", "nameless", "noisy", "odd", "old",
        "orange", "patient", "plain", "polished", "proud", "purple", "quiet", "rapid",
        "raspy", "red", "restless", "rough", "round", "royal", "shiny", "shrill",
        "shy", "silent", "small", "snowy", "soft", "solitary", "sparkling", "spring",
        "square", "steep", "still", "summer", "super", "sweet", "throbbing", "tight",
        "tiny", "twilight", "wandering", "weathered", "white", "wild", "winter", "wispy",
        "withered", "yellow", "young"
    )
    val nouns = listOf(
        "art", "band", "bar", "base", "bird", "block", "boat", "bonus",
        "bread", "breeze", "brook", "bush", "butterfly", "cake", "cell", "cherry",
        "cloud", "credit", "darkness", "dawn", "dew", "disk", "dream", "dust",
        "feather", "field", "fire", "firefly", "flower", "fog", "forest", "frog",
        "frost", "glade", "glitter", "grass", "hall", "hat", "haze", "heart",
        "hill", "king", "lab", "lake", "leaf", "limit", "math", "meadow",
        "mode", "moon", "morning", "mountain", "mouse", "mud", "night", "paper",
        "pine", "poetry", "pond", "queen", "rain", "recipe", "resonance", "rice",
        "river", "salad", "scene", "sea", "shadow", "shape", "silence", "sky",
        "smoke", "snow", "snowflake", "sound", "star", "sun", "sun", "sunset",
        "surf", "term", "thunder", "tooth", "tree", "truth", "union", "unit",
        "violet", "voice", "water", "water", "waterfall", "wave", "wildflower", "wind",
        "wood"
    )
    val computerID = NetworkInterface.getNetworkInterfaces().toList().filterNot { it.isLoopback }
        .minByOrNull { it.index }?.hardwareAddress?.hashCode() ?: 0
    val random = Random(computerID.toLong())
    return "${adjectives[random.nextInt(0, adjectives.size)]}-${nouns[random.nextInt(0, nouns.size)]}-${computerID.toString().take(4)}"
}
