/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/26/22, 12:32 AM
 */

package by.enrollie.routes

import io.ktor.server.routing.*

fun Route.registerAllRoutes() {
    user()
    wsRegister()
}
