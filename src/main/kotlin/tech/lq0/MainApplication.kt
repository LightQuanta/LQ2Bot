package tech.lq0

import love.forte.simbot.spring.EnableSimbot
import org.springframework.boot.autoconfigure.SpringBootApplication
import kotlin.Array
import kotlin.String
import org.springframework.boot.runApplication

@EnableSimbot
@SpringBootApplication
class MainApplication

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
}
