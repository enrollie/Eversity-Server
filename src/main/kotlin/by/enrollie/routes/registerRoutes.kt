/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/14/22, 1:50 AM
 */

package by.enrollie.routes

import by.enrollie.impl.ProvidersCatalog
import io.ktor.server.routing.*

fun Route.registerAllRoutes() {
    user()
    users()
    Class()
    classes()
    absence()
    absences()
    wsRegister()
    templates()
    template()
    bellsTimetable()
    getSchool()
    teacher()
    roles()
    route("/temp/file") { ProvidersCatalog.expiringFilesServer.registerInRoute(this) }
}
