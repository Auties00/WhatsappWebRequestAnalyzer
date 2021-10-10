import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.5.31"
    application
}

group = "it.auties"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.auties00", "whatsappweb4j", "1.2")
    implementation("org.seleniumhq.selenium", "selenium-java", "4.0.0-beta-3")
    implementation("org.seleniumhq.selenium", "selenium-devtools-v90", "4.0.0-beta-3")
}

application {
    mainClass.set("it.auties.whatsapp4j.MainKt")
}

