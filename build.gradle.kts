import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.5.31"
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = "it.auties"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.github.auties00", "standard", "2.2.2-SNAPSHOT")
    implementation("com.github.auties00", "whatsappweb4j", "2.2.2-SNAPSHOT")
    implementation("org.seleniumhq.selenium", "selenium-java", "4.0.0-rc-1")
    implementation("org.seleniumhq.selenium", "selenium-devtools", "4.0.0-rc-1")
    implementation("org.bouncycastle", "bcpkix-jdk15on", "1.68")
}

val fatJar = task("fatJar", type = Jar::class) {
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("${project.name}-with-dependencies")
    manifest {
        attributes["Implementation-Title"] = "Analyzer"
        attributes["Implementation-Version"] = archiveVersion
        attributes["Main-Class"] = "it.auties.analyzer.MainKt"
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
    options.forkOptions.jvmArgs?.add("--illegal-access=permit")
    options.forkOptions.jvmArgs?.add("--add-opens")
    options.forkOptions.jvmArgs?.add("jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
    options.forkOptions.jvmArgs?.add("--add-opens")
    options.forkOptions.jvmArgs?.add("java.base/java.lang=ALL-UNNAMED")
    options.forkOptions.jvmArgs?.plus("--enable-preview")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<JavaCompile> {
    options.compilerArgs.plus("--add-opens")
    options.compilerArgs.plus("jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
    options.compilerArgs.plus("--add-opens")
    options.compilerArgs.plus("java.base/java.lang=ALL-UNNAMED")
    options.compilerArgs.plus("--enable-preview")
}

application {
    mainClass.set("it.auties.analyzer.MainKt")
}