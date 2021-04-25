import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.4.21"
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
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

val fatJar = task("fatJar", type = Jar::class) {
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("${project.name}-with-dependencies")
    manifest {
        attributes["Implementation-Title"] = "Analyzer"
        attributes["Implementation-Version"] = archiveVersion
        attributes["Main-Class"] = "it.auties.whatsapp4j.MainKt"
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}


tasks.withType<JavaCompile> {
    options.forkOptions.jvmArgs?.add("--add-opens")
    options.forkOptions.jvmArgs?.add("jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
    options.forkOptions.jvmArgs?.add("--add-opens")
    options.forkOptions.jvmArgs?.add("java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<JavaCompile> {
    options.compilerArgs.plus("--enable-preview")
}

application {
    mainClass.set("it.auties.whatsapp4j.MainKt")
}

