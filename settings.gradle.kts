
pluginManagement {
    repositories {
        // 国内镜像优先（阿里云 + 腾讯云），加速 Maven Central / Gradle Plugin Portal 等通用依赖
        maven {
            name = "Aliyun Public"
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            name = "Aliyun Gradle Plugin"
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
        }
        maven {
            name = "Aliyun Google"
            url = uri("https://maven.aliyun.com/repository/google")
        }
        maven {
            name = "Tencent Maven Public"
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/")
        }
        // GTNH 专用构件（gtnhgradle 等）仅官方 Nexus 提供，无国内镜像
        maven {
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        mavenLocal()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version("1.0.33")
}
