package com.university.studentixflow

import com.university.studentixflow.db.DatabaseFactory
import com.university.studentixflow.plugins.configureHTTP
import com.university.studentixflow.plugins.configureRouting
import com.university.studentixflow.plugins.configureSecurity
import com.university.studentixflow.plugins.configureSerialization
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init(environment.config)
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureRouting()
}
