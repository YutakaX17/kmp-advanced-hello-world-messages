pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = uri("../messages/build/repo"))
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "messages-consumer-fixture"
