module Eversity.shared {
    requires org.joda.time;
    requires kotlin.stdlib;
    requires kotlin.reflect;
    requires kotlinx.serialization.core;
    requires kotlinx.serialization.json;
    requires kotlinx.coroutines.core.jvm;
    exports by.enrollie.data_classes;
    exports by.enrollie.providers;
    exports by.enrollie.exceptions;
    exports by.enrollie.annotations;
}
