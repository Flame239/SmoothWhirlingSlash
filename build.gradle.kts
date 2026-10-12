plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "org.poe2"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // IDEA's Java type inference uses annotations absent from kotlin-stdlib's older dependency.
    compileOnly("org.jetbrains:annotations:26.1.0")
    testCompileOnly("org.jetbrains:annotations:26.1.0")
    implementation("com.formdev:flatlaf:3.7")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("org.poe2.MainKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Dsun.java2d.uiScale=1")
}

kotlin {
    jvmToolchain(26)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("test.runtimeClasspath", sourceSets["test"].runtimeClasspath.asPath)
}

val portableName = "SmoothWhirlingSlash"
val portableInput = layout.buildDirectory.dir("portable-input")
val portableDestination = layout.buildDirectory.dir("portable")
val portableFolder = portableDestination.map { it.dir(portableName) }
val packagingJdk = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(26))
}

val stagePortableJars = tasks.register<Sync>("stagePortableJars") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(portableInput)
}

val buildPortableFolder = tasks.register<Exec>("portableFolder") {
    group = "distribution"
    description = "Build a portable Windows folder with an executable and bundled Java runtime."
    dependsOn(stagePortableJars)
    inputs.dir(portableInput)
    inputs.file("src/main/resources/icons/whirling-slash.ico")
    inputs.file("README.md")
    outputs.dir(portableFolder)
    doFirst {
        check(System.getProperty("os.name").startsWith("Windows")) { "Portable packaging must run on Windows." }
        val output = portableFolder.get().asFile
        check(output.canonicalFile.toPath().startsWith(layout.buildDirectory.get().asFile.canonicalFile.toPath()))
        delete(output)
        commandLine(
            packagingJdk.get().metadata.installationPath.file("bin/jpackage.exe").asFile,
            "--type", "app-image",
            "--name", portableName,
            "--app-version", "1.0",
            "--input", portableInput.get().asFile,
            "--dest", portableDestination.get().asFile,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", application.mainClass.get(),
            "--icon", file("src/main/resources/icons/whirling-slash.ico"),
            "--add-modules", "java.desktop,java.logging,jdk.unsupported",
            "--java-options", "--enable-native-access=ALL-UNNAMED",
            "--java-options", "-DwhirlingSlash.directory=\$BINDIR",
        )
    }
    doLast {
        copy { from("README.md"); into(portableFolder) }
    }
}

tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Zip the portable Windows application folder."
    dependsOn(buildPortableFolder)
    archiveFileName.set("$portableName-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(portableFolder) { into(portableName) }
}
