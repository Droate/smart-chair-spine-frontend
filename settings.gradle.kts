pluginManagement {
    repositories {
        google()                 // AGP 插件必须从这里下载
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "smart-chair-system"

include(":mobile-app:app")
include(":simulator:android-simulator")
include(":shared:models")
include(":shared:protocol")
include(":shared:utils")
include(":backend:chair-service")
// 如果还有其他模块，在这里继续 include