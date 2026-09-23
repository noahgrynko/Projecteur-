pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android (pilotes USB CDC/CH340/CP210x/FTDI) n'est publié que sur JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ProjecteurRemote"
include(":app")
