/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/1/22, 8:46 PM
 */

package by.enrollie.plugins

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.routing.*
import io.ktor.util.*

val RequestStartTimeAttributeKey = AttributeKey<Long>("RequestAcceptTime")
val RequestEndTimeAttributeKey = AttributeKey<Long>("RequestEndTime")

val insertTimePlugin = createApplicationPlugin("InsertTimePlugin") {
    on(MonitoringEvent(Routing.RoutingCallStarted)) {
        it.attributes.put(RequestStartTimeAttributeKey, System.currentTimeMillis())
    }
    onCallRespond { call ->
        call.attributes.put(RequestEndTimeAttributeKey, System.currentTimeMillis())
    }
}
