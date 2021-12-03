package it.auties.analyzer

import it.auties.whatsapp4j.common.binary.BinaryFlag
import it.auties.whatsapp4j.standard.binary.BinaryMetric
import org.openqa.selenium.chrome.ChromeDriver
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val directory = File(System.getProperty("user.home") + "/.whatsapp")

fun initialize(): ChromeDriver {
    System.setProperty("webdriver.chrome.driver", fromJar("chromedriver${getPlatformExtension()}"))
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({}, 0, 1, TimeUnit.MINUTES)
    return ChromeDriver()
}

private fun fromJar(input: String): String {
    directory.mkdirs()
    val file = File(directory, input)
    if(!file.exists()) {
        Files.write(
            file.toPath(),
            ClassLoader.getSystemClassLoader().getResource(input)!!.openStream().readAllBytes(),
            StandardOpenOption.CREATE_NEW,
        )
    }

    return file.path
}

private fun getPlatformExtension(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> ".exe"
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> ""
        else -> throw UnsupportedOperationException("Whatsapp request analyzer only works on Windows and Linux")
    }
}

fun toMetric(byte: Byte): BinaryMetric? {
    return BinaryMetric.values()
        .firstOrNull { it.data() == java.lang.Byte.toUnsignedInt(byte) }
}

fun toFlag(byte: Byte): BinaryFlag? {
    return BinaryFlag.values()
        .firstOrNull { it.data() == byte }
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