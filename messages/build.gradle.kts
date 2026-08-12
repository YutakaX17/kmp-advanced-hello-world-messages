plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.licensee)
    `maven-publish`
    signing
}

group = rootProject.group
version = rootProject.version

kotlin {
    explicitApi()
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
    jvm()
    android {
        namespace = "io.github.yutakax17.advancedhelloworld.messages"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.advanced.hello.world.kmp.core)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("MessagesDatabase") {
            packageName.set("io.github.yutakax17.advancedhelloworld.messages.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/license/mit")
                    distribution.set("repo")
                }
            }
        }
    }
    publications.named<MavenPublication>("kotlinMultiplatform") {
        artifactId = "kmp-advanced-hello-world-messages"
    }
    publications.named<MavenPublication>("jvm") {
        artifactId = "kmp-advanced-hello-world-messages-jvm"
    }
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YutakaX17/kmp-advanced-hello-world-messages")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

licensee {
    allow("Apache-2.0")
    allowUrl("https://opensource.org/license/mit")
}
