package it.auties.whatsapp4j

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object PathUtils {
    private val directory = File(System.getProperty("user.home") + "/.whatsapp")

    fun fromJar(input: String): String {
        directory.mkdirs()
        val file = File(directory, input)
        if(!file.exists()) {
            Files.write(
                file.toPath(),
                javaClass.classLoader.getResource(input)!!.openStream().readAllBytes(),
                StandardOpenOption.CREATE_NEW,
            )
        }

        return file.path
    }
}