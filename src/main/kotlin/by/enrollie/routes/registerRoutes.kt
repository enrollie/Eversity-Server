/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.routes

import io.ktor.server.routing.*

fun Route.registerAllRoutes() {
    user()
    users()
    Class()
    classes()
    absence()
    absences()
    wsRegister()
}
