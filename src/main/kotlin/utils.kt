package it.auties.analyzer

import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.chrome.ChromeDriver
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun initialize(): ChromeDriver {
    WebDriverManager.chromedriver().setup()
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({}, 0, 1, TimeUnit.MINUTES)
    return ChromeDriver()
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