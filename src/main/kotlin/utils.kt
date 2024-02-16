package it.auties.analyzer

import io.github.bonigarcia.wdm.managers.ChromeDriverManager
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun initialize(): ChromeDriver {
    ChromeDriverManager.chromedriver().setup()
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({}, 0, 1, TimeUnit.MINUTES)
    val options = ChromeOptions()
    options.addArguments("--user-data-dir=${Path.of("./.profile").toAbsolutePath()}")
    return ChromeDriver(options)
}

fun String.indexesOf(input: String): List<Int> {
    val results = ArrayList<Int>()
    var last: Int = indexOf(input)
    while (last != -1) {
        results.add(last)
        last = indexOf(input, last + input.length)
    }

    return results
}

fun <T> Optional<T>.orThrow(): T = this.orElseThrow()
fun <T> Optional<T>.orNull(): T? = this.orElse(null)
fun <T> ArrayList<T>.copy() = ArrayList(this)
