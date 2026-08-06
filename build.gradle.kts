buildscript {
    configurations.classpath {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.binary.compatibility)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.dependency.check)
}

group = "io.github.yutakax17.advancedhelloworld"
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
    }
    format("misc") {
        target("*.md", ".github/**/*.yml", ".github/**/*.yaml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom(files("messages/src/commonMain/kotlin"))
    config.setFrom(files("config/detekt/detekt.yml"))
}

dependencyCheck {
    failBuildOnCVSS = 7.0F
    formats = listOf("HTML", "SARIF")
    suppressionFile = "config/dependency-check-suppressions.xml"
    nvd.apiKey = providers.environmentVariable("NVD_API_KEY").orNull
    nvd.delay = 6_000
    nvd.resultsPerPage = 2_000
    nvd.maxRetryCount = 10
    nvd.validForHours = 4
}

dependencyAnalysis {
    issues {
        all {
            onAny {
                severity("fail")
            }
            onUsedTransitiveDependencies {
                // The commonMain API declaration resolves to this published
                // Android variant; declaring the variant separately would
                // break the KMP dependency boundary.
                exclude(
                    "io.github.yutakax17.advancedhelloworld:core-android",
                )
            }
        }
    }
}
