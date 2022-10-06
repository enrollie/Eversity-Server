module Eversity.shared {
    requires kotlin.stdlib;
    requires kotlin.reflect;
    requires kotlinx.serialization.core;
    requires kotlinx.serialization.json;
    requires kotlinx.coroutines.core.jvm;
    requires io.ktor.server.core;
    requires io.ktor.utils;
    requires brigadier;
    exports by.enrollie.data_classes;
    exports by.enrollie.providers;
    exports by.enrollie.exceptions;
    exports by.enrollie.annotations;
}
