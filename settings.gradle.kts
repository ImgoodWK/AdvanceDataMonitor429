pluginManagement {
    // pluginManagement is compiled in an earlier Settings phase, so values
    // used here must be resolved inside this block rather than through
    // top-level script variables.
    val useChinaMirrors = providers.gradleProperty("textech.useChinaMirrors")
        .orNull
        ?.equals("true", ignoreCase = true) == true
    val isCi = providers.environmentVariable("CI")
        .orNull
        ?.equals("true", ignoreCase = true) == true

    repositories {
        // Domestic mirrors are an explicit local opt-in. Official sources
        // remain available as fallbacks and are the only remote defaults in CI.
        if (useChinaMirrors) {
            maven {
                name = "Aliyun Gradle Plugin"
                url = uri("https://maven.aliyun.com/repository/gradle-plugin")
            }
            maven {
                name = "Aliyun Public"
                url = uri("https://maven.aliyun.com/repository/public")
            }
            maven {
                name = "Tencent Maven Public"
                url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/")
            }
        }

        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }

        // Local publications are useful for development, but must never make
        // a CI or tagged build depend on state outside the repository.
        if (!isCi) {
            mavenLocal()
        }
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version("1.0.33")
}
