package it.auties.whatsapp4j

object PathUtils {
    fun fromJar(input: String): String = javaClass.classLoader.getResource(input)?.path!!
}