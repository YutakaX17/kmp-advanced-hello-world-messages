pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        providers.gradleProperty("localMavenRepository").orNull?.let {
            maven(url = uri(it))
        }
        mavenCentral()
        google()
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YutakaX17/kmp-advanced-hello-world-core")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

if (providers.gradleProperty("useLocalKmpCore").orNull == "true") {
    includeBuild("../kmp-advanced-hello-world-core") {
        dependencySubstitution {
            substitute(
                module(
                    "io.github.yutakax17.advancedhelloworld:" +
                        "kmp-advanced-hello-world-core",
                ),
            ).using(project(":core"))
        }
    }
}

rootProject.name = "kmp-advanced-hello-world-messages"
include(":messages")
