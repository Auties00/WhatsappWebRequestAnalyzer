package it.auties.analyzer

import it.auties.bytes.Bytes
import org.openqa.selenium.chrome.ChromeDriver
import java.io.File
import java.nio.file.Files
import java.nio.file.Files.createDirectories
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val directory = File(System.getProperty("user.home") + "/.whatsapp")

fun initialize(): ChromeDriver {
    System.setProperty("webdriver.chrome.driver", fromJar("${getPlatformFolder()}/chromedriver.${getPlatformExtension()}"))
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({}, 0, 1, TimeUnit.MINUTES)
    return ChromeDriver()
}

private fun fromJar(input: String): String {
    val file = Path.of(directory.toString(), input)
    createDirectories(file.parent)
    if(Files.notExists(file)) {
        Files.write(
            file,
            ClassLoader.getSystemClassLoader().getResource(input)!!.openStream().readAllBytes(),
            StandardOpenOption.CREATE_NEW,
        )
    }

    return file.toString()
}

private fun getPlatformFolder(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> "win32"
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> "linux64"
        else -> "mac64"
    }
}

private fun getPlatformExtension(): String =
    if (System.getProperty("os.name").lowercase().contains("win")) "exe" else ""


fun String.indexesOf(input: String): List<Int> {
    val results = ArrayList<Int>()
    var last: Int = indexOf(input)
    while (last != -1) {
        results.add(last)
        last = indexOf(input, last + input.length)
    }

    return results
}

fun List<Byte>.toBytes(): Bytes = Bytes.of(toByteArray())
fun <T> Optional<T>.orThrow(): T = this.orElseThrow()
fun <T> Optional<T>.orNull(): T? = this.orElse(null)