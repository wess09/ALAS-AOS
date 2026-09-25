import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.azurpilot.ghio.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly：插件里只引 AGP/KGP 的 DSL 类型，运行时那份由消费方的 buildscript classpath 提供
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    // 消费方 classpath 上没有这两个，必须 implementation
    // 读配方（YAML）与写 agent 运行时描述（JSON）各一个；
    // JSON 侧只用 buildJsonObject 那套运行时 API，不碰 @Serializable，因此不需要序列化编译器插件
    implementation(libs.snakeyaml.engine)
    implementation(libs.kotlinx.serialization.json)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "azurpilot.android.application"
            implementationClass = "com.azurpilot.ghio.gradle.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "azurpilot.android.library"
            implementationClass = "com.azurpilot.ghio.gradle.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "azurpilot.android.compose"
            implementationClass = "com.azurpilot.ghio.gradle.AndroidComposeConventionPlugin"
        }
        register("kotlinJvm") {
            id = "azurpilot.kotlin.jvm"
            implementationClass = "com.azurpilot.ghio.gradle.KotlinJvmConventionPlugin"
        }
    }
}
