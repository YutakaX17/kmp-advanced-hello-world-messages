plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

group = rootProject.group
version = rootProject.version

kotlin {
    explicitApi()
    jvm()
    android {
        namespace = "io.github.yutakax17.advancedhelloworld.messages"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.advanced.hello.world.kmp.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("MessagesDatabase") {
            packageName.set("io.github.yutakax17.advancedhelloworld.messages.database")
            // Enable after the initial schema snapshot is committed with the
            // first numbered migration.
            verifyMigrations.set(false)
        }
    }
}

publishing {
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
    }
}
