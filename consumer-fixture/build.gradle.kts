plugins {
    kotlin("jvm") version "2.4.10"
}

dependencies {
    implementation("io.github.yutakax17.advancedhelloworld:kmp-advanced-hello-world-messages-jvm:0.1.0-SNAPSHOT")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
