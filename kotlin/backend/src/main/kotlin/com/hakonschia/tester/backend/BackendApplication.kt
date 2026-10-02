package com.hakonschia.tester.backend

import com.malinskiy.adam.interactor.StartAdbInteractor
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BackendApplication

fun main(args: Array<String>) {
    runBlocking {
        StartAdbInteractor().execute()
    }

    runApplication<BackendApplication>(*args)
}
