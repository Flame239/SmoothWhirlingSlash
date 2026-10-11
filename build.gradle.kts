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
