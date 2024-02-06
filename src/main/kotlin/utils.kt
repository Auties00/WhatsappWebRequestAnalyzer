package it.auties.analyzer

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun initialize(): ChromeDriver {
    // you can download the driver from https://googlechromelabs.github.io/chrome-for-testing/#stable
    // and specify the path to the driver in the following line
    System.setProperty("webdriver.chrome.driver", "C:\\Users\\hunter\\Downloads\\chromedriver-win64\\chromedriver.exe")
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({}, 0, 1, TimeUnit.MINUTES)
    val options = ChromeOptions()
    options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe")
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